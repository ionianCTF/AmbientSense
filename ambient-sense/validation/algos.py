"""
Python port of the Ambient Sense algorithms, plus upgrade candidates.

Keeping the port one-to-one with the Kotlin makes the experimental results
transferable: a constant tuned here is a constant changed there.

Tracker variants
----------------
ema     current shipping algorithm: EMA-smoothed RSSI -> log-distance range ->
        ordinary least squares slope of range over a sliding window -> thresholds.
kalman  constant-velocity Kalman filter on range, process noise matched to the
        expected manoeuvre, measurement noise derived from RSSI scatter.
pass    constant-velocity straight-line *pass* model
            r(t)^2 = r0^2 + v^2 (t - t0)^2
        fitted by searching t0 and solving for v^2, r0^2 by least squares. This is
        the physically correct model for something driving past you: the range is
        hyperbolic, not linear, so a straight-line slope systematically
        under-estimates speed near the point of closest approach.
"""

from __future__ import annotations

import math
from dataclasses import dataclass, field
from typing import Dict, Iterable, List, Optional, Sequence, Tuple

import numpy as np

# ------------------------------------------------------------------- settings

@dataclass
class Settings:
    # geometry
    path_loss_exponent: float = 2.9
    default_tx: float = -59.0
    proximity_radius_m: float = 25.0
    # classification
    static_speed_mps: float = 0.45
    pedestrian_max_mps: float = 2.4
    vehicle_min_mps: float = 3.0
    min_track_samples: int = 4
    group_devices: bool = True
    fit_window_s: float = 10.0
    min_fit_r2: float = 0.18
    adaptive_gate: bool = False      # accept a fast, clean fit on fewer samples
    use_bic: bool = True             # decide "moving vs static" by model selection
    speed_sigma_k: float = 1.0       # require |v| > static_thr + k * sigma_v
    min_range_sweep_m: float = 0.0   # ignore "motion" smaller than this range change
    # A laptop, TV or beacon that has not budged for minutes is infrastructure, not a
    # person. Require both a long static dwell *and* a rock-steady range: a person
    # standing still still sways far more than a bolted-down device.
    exclude_infrastructure: bool = True
    infrastructure_dwell_s: float = 120.0
    infrastructure_range_std_m: float = 0.6
    autocorr_corrected_bic: bool = True
    # crowd heuristics
    device_to_person_factor: float = 1.15
    rf_person_coeff: float = 6.0
    dip_person_coeff: float = 0.55
    vehicle_dip_coeff: float = 0.25
    static_entity_weight: float = 1.0     # a standing cluster is more ambiguous than a walker
    rules_device_weight: float = 0.55
    rules_rf_weight: float = 0.45
    # The device-free (Wi-Fi) channel has a field of view set by the AP geometry, not
    # by the radius the user asked about. When the region of interest grows past that
    # field of view the RF term carries less information about it, so its prior is
    # rescaled by rf_area_scale (see validation/radius.py).
    rf_area_scale: float = 1.0
    vehicle_radius_m: float = 25.0        # vehicles are counted inside their own radius
    kalman_q: float = 1.5          # process noise, (m/s^2)^2-ish
    # rls
    forgetting: float = 0.985
    # misc
    rssi_sigma_db: float = 4.0        # assumed RSSI scatter, drives Kalman R


# --------------------------------------------------------------- wifi features

class LinkWindow:
    """Ring buffer + statistics for one BSSID (port of WifiScanner.LinkState)."""

    CAP = 96
    MIN_SAMPLES = 6
    DIP_K = 1.2
    DIP_RELEASE_K = 0.5
    DIP_MIN_GAP_MS = 400.0
    QUIET_STD_DB = 0.6

    def __init__(self):
        self.rssi: List[float] = []
        self.ts: List[float] = []
        self.last_seen = 0.0
        self.last_rssi = -100.0

    def add(self, rssi: float, t: float):
        self.rssi.append(rssi)
        self.ts.append(t)
        if len(self.rssi) > self.CAP:
            self.rssi.pop(0)
            self.ts.pop(0)
        self.last_seen = t
        self.last_rssi = rssi

    def stats(self, now: float) -> Optional[Tuple[float, float, float]]:
        n = len(self.rssi)
        if n < self.MIN_SAMPLES:
            return None
        v = np.asarray(self.rssi, dtype=float)
        ts = np.asarray(self.ts, dtype=float)
        mean_db = float(v.mean())
        std_db = float(v.std(ddof=1))
        lin = 10.0 ** (v / 10.0)
        cv = float(lin.std(ddof=1) / lin.mean()) if lin.mean() > 0 else 0.0

        thr = mean_db - self.DIP_K * max(std_db, 0.4)
        rel = mean_db - self.DIP_RELEASE_K * max(std_db, 0.4)
        dips = 0
        armed = True
        last_dip = None
        for i in range(n):
            if armed and v[i] < thr and (last_dip is None or ts[i] - last_dip > self.DIP_MIN_GAP_MS / 1000.0):
                dips += 1
                armed = False
                last_dip = ts[i]
            elif not armed and v[i] > rel:
                armed = True
        span_min = max((ts[-1] - ts[0]) / 60.0, 0.25)
        dip_rate = 0.0 if std_db < self.QUIET_STD_DB else dips / span_min
        return std_db, cv, dip_rate


class WifiFeatures:
    """Port of WifiScanner.tick(): fluctuation + dip + churn statistics."""

    def __init__(self, retention_s: float = 300.0):
        self.links: Dict[str, LinkWindow] = {}
        self.first_seen: Dict[str, float] = {}
        self.appearance: List[float] = []
        self.retention = retention_s

    def ingest(self, t: float, scan: Dict[str, float]):
        for bssid, rssi in scan.items():
            if bssid not in self.first_seen:
                self.first_seen[bssid] = t
                self.appearance.append(t)
            self.links.setdefault(bssid, LinkWindow()).add(rssi, t)

    def tick(self, now: float) -> Dict[str, float]:
        cutoff = now - self.retention
        for bssid in [b for b, w in self.links.items() if w.last_seen < cutoff]:
            self.links.pop(bssid, None)
            self.first_seen.pop(bssid, None)
        self.appearance = [t for t in self.appearance if now - t <= 60.0]

        stds, cvs = [], []
        strong = active = 0
        dip_total = 0.0
        for w in self.links.values():
            st = w.stats(now)
            if st is None:
                continue
            std, cv, dip = st
            stds.append(std)
            cvs.append(cv)
            if w.last_rssi >= -70:
                strong += 1
            if cv >= 0.25:
                active += 1
            dip_total += dip

        med = lambda a: float(np.median(a)) if a else 0.0
        p90 = lambda a: float(np.percentile(a, 90)) if a else 0.0
        cv_med, cv_p90 = med(cvs), p90(cvs)
        std_med = med(stds)
        churn = float(len(self.appearance))
        activity = float(cv_med * 6.0 + min(dip_total / 30.0, 1.5) * 0.25
                         + min(churn / 20.0, 1.5) * 0.15)
        return {
            "ap_count": float(len(self.links)),
            "strong_ap": float(strong),
            "links_tracked": float(len(stds)),
            "std_median": std_med,
            "std_p90": p90(stds),
            "cv_median": cv_med,
            "cv_p90": cv_p90,
            "dip_rate": float(dip_total),
            "active_fraction": (active / len(stds)) if stds else 0.0,
            "churn": churn,
            "activity": activity,
        }


# --------------------------------------------------------------- ble tracking

@dataclass
class TrackState:
    key: str
    rssi: float
    tx: Optional[int]
    distance: float
    speed: float
    r2: float
    motion: str
    samples: int
    total_samples: int
    first_seen: float
    last_seen: float
    group: int = -1
    truth_cls: Optional[str] = None
    static_truth: bool = False
    target_id: int = -1
    infrastructure: bool = False
    pos_error: Optional[float] = None
    true_dist: float = -1.0
    truth_kind: str = "clutter"


def range_from_rssi(rssi: float, tx: float, n: float) -> float:
    return float(np.clip(10.0 ** ((tx - rssi) / (10.0 * max(n, 1.0))), 0.3, 150.0))


def _classify(speed: float, s: Settings) -> str:
    a = abs(speed)
    if a <= s.static_speed_mps:
        return "static"
    if a <= s.pedestrian_max_mps:
        return "pedestrian"
    return "vehicle"


class _Track:
    def __init__(self, key: str, tx: Optional[int], t: float, truth_cls: Optional[str],
                 static_truth: bool, target_id: int = -1):
        self.key = key
        self.tx = tx
        self.truth_cls = truth_cls
        self.static_truth = static_truth
        self.target_id = target_id
        self.truth_kind: str = "clutter"
        self.rssi_ema: Optional[float] = None
        self.ts: List[float] = []
        self.rs: List[float] = []
        self.total = 0
        self.first_seen = t
        self.last_seen = t
        self.motion = "unknown"
        self.candidate = "unknown"
        self.candidate_since = t
        self.speed = 0.0
        self.r2 = 0.0
        self.samples = 0
        self.true_dist = -1.0
        # kalman state
        self.x = None
        self.P = None
        self.speed_se = float("inf")
        self.speed_significant = False

    # ---- shared bookkeeping
    def add(self, obs, s: Settings):
        t = obs.t
        rssi = obs.rssi
        if obs.tx is not None:
            self.tx = obs.tx
        self.rssi_ema = rssi if self.rssi_ema is None else self.rssi_ema + 0.25 * (rssi - self.rssi_ema)
        self.total += 1
        self.last_seen = t
        tx = self.tx if self.tx is not None else s.default_tx
        self.ts.append(t)
        self.rs.append(range_from_rssi(self.rssi_ema, tx, s.path_loss_exponent))
        if getattr(obs, "true_dist", -1.0) >= 0.0:
            self.true_dist = float(obs.true_dist)
        self.truth_kind = getattr(obs, "truth_kind", self.truth_kind) or self.truth_kind

    def _window(self, s: Settings) -> Tuple[np.ndarray, np.ndarray]:
        t0 = self.ts[-1] - s.fit_window_s
        idx = [i for i, t in enumerate(self.ts) if t >= t0]
        return (np.asarray(self.ts)[idx], np.asarray(self.rs)[idx])

    # ---- variant 1: EMA + windowed OLS (shipping algorithm)
    def fit_ema(self, s: Settings):
        ts, rs = self._window(s)
        self.samples = len(ts)
        if len(ts) < 2:
            self.speed, self.r2 = 0.0, 0.0
            return
        if len(ts) > 1 and np.ptp(ts) > 1e-6:
            slope, intercept = np.polyfit(ts, rs, 1)
            pred = slope * ts + intercept
            ss_res = float(np.sum((rs - pred) ** 2))
            ss_tot = float(np.sum((rs - rs.mean()) ** 2))
            self.speed = float(slope)
            self.r2 = float(1.0 - ss_res / ss_tot) if ss_tot > 1e-9 else 0.0
        else:
            self.speed, self.r2 = 0.0, 0.0

    # ---- variant 2: constant-velocity Kalman filter on range
    def fit_kalman(self, s: Settings):
        """Sequential filter: each measurement is assimilated exactly once."""
        self.samples = len([t for t in self.ts if t >= self.ts[-1] - s.fit_window_s])
        if len(self.rs) == 0:
            return
        sigma_r = (math.log(10.0) / 10.0) * max(self.rs[-1], 1.0) * s.rssi_sigma_db / s.path_loss_exponent
        R = max(sigma_r ** 2, 0.25)
        q = s.kalman_q
        if self.x is None:
            self.x = np.array([self.rs[0], 0.0])
            self.P = np.diag([25.0, 100.0])
            self.kf_index = 1
        start = getattr(self, "kf_index", 1)
        if start >= len(self.rs):
            return
        ts = np.asarray(self.ts)[start:]
        rs = np.asarray(self.rs)[start:]
        self.kf_index = len(self.rs)
        # the most recent sample plus the ones assimilated now, for the R^2 report
        ts = np.concatenate(([self.ts[start - 1]], ts))
        rs = np.concatenate(([self.rs[start - 1]], rs))
        for i in range(1, len(ts)):
            dt = max(ts[i] - ts[i - 1], 1e-3)
            F = np.array([[1.0, dt], [0.0, 1.0]])
            Q = q * np.array([[dt ** 4 / 4.0, dt ** 3 / 2.0], [dt ** 3 / 2.0, dt ** 2]])
            self.x = F @ self.x
            self.P = F @ self.P @ F.T + Q
            z = np.array([rs[i]])
            H = np.array([[1.0, 0.0]])
            y = z - H @ self.x
            S = H @ self.P @ H.T + R
            K = self.P @ H.T / S[0, 0]
            self.x = self.x + (K.flatten() * y[0])
            self.P = (np.eye(2) - K @ H) @ self.P
        self.speed = float(self.x[1])
        res = float(np.mean((rs - (rs[-1] + self.speed * (ts - ts[-1]))) ** 2))
        tot = float(np.var(rs)) + 1e-9
        self.r2 = float(max(0.0, 1.0 - res / tot))

    # ---- variant 3: constant-velocity straight-line pass model
    def fit_pass(self, s: Settings, n_grid: int = 25):
        """
        r(t)^2 = r0^2 + v^2 (t - t0)^2

        For a fixed t0 the model is linear in (r0^2, v^2) with regressor u = (t-t0)^2,
        so we grid over t0 and solve each column in closed form. Residuals and the
        static-vs-moving decision are always evaluated in *range* space (metres),
        never in squared-range space, or the two models are not comparable.
        """
        ts, rs = self._window(s)
        self.samples = len(ts)
        self.speed_se = float("inf")
        self.pass_moving = False
        if len(ts) < 3:
            self.speed, self.r2 = 0.0, 0.0
            return

        y = rs ** 2                       # fit space
        ybar = float(y.mean())
        yc = y - ybar
        # t0 is kept near the observed span: a closest-approach time far outside the
        # data is the model inventing a manoeuvre it never saw
        t0_grid = np.linspace(ts[0] - 2.0, ts[-1] + 2.0, n_grid)
        U = (ts[:, None] - t0_grid[None, :]) ** 2
        Uc = U - U.mean(axis=0, keepdims=True)
        denom = (Uc ** 2).sum(axis=0)
        safe = denom > 1e-9
        slopes = np.zeros_like(denom)
        slopes[safe] = (Uc[:, safe] * yc[:, None]).sum(axis=0) / denom[safe]
        intercepts = ybar - slopes * U.mean(axis=0)
        # residuals in range space
        r_hat = np.sqrt(np.maximum(intercepts[None, :] + slopes[None, :] * U, 0.0))
        sse_pass = ((rs[:, None] - r_hat) ** 2).sum(axis=0)
        sse_pass[~safe] = np.inf
        sse_pass[slopes < 0] = np.inf
        j = int(np.argmin(sse_pass))

        n = len(ts)
        sse_static = float(np.sum((rs - rs.mean()) ** 2))
        sse_best = float(sse_pass[j])

        # RSSI noise is temporally correlated (multipath does not reshuffle every
        # millisecond), so the residuals of a static device wander smoothly and a
        # 3-parameter model can "fit" that wander. Correct the penalty with the
        # effective sample size derived from the lag-1 residual autocorrelation.
        resid_r = rs - r_hat[:, j]
        rho = 0.0
        if np.std(resid_r) > 1e-9 and len(resid_r) > 3:
            a = resid_r[:-1] - resid_r.mean()
            b = resid_r[1:] - resid_r.mean()
            den = float(np.sum(a * a))
            rho = float(np.sum(a * b) / den) if den > 1e-12 else 0.0
        rho = float(np.clip(rho, -0.9, 0.95))
        n_eff = float(np.clip(n * (1.0 - rho) / (1.0 + rho), 3.0, float(n)))
        nn = n_eff if s.autocorr_corrected_bic else float(n)

        if s.use_bic:
            # nested model comparison: constant range (1 param) vs straight-line pass (3)
            bic_static = nn * math.log(max(sse_static, 1e-9) / n) + 1.0 * math.log(nn)
            bic_pass = nn * math.log(max(sse_best, 1e-9) / n) + 3.0 * math.log(nn)
            self.pass_moving = bic_pass < bic_static
            if bic_pass >= bic_static:
                self.speed = 0.0
                self.r2 = 0.0
                self.t0 = float(ts[-1])
                self.r0 = float(rs.mean())
                return

        self.speed = float(math.sqrt(max(slopes[j], 0.0)))
        # standard error of v, from the y-space regression: v = sqrt(b) => se_v = se_b/(2v)
        resid = y - (intercepts[j] + slopes[j] * U[:, j])
        dof = max(n - 2, 1)
        sigma_y2 = float(np.sum(resid ** 2)) / dof
        den_j = float(denom[j]) if denom[j] > 1e-9 else 1e-9
        se_b = math.sqrt(sigma_y2 / den_j)
        self.speed_se = float(se_b / (2.0 * self.speed)) if self.speed > 1e-6 else float("inf")
        self.t0 = float(t0_grid[j])
        self.r0 = float(math.sqrt(max(intercepts[j], 0.0)))
        self.r2 = float(max(0.0, 1.0 - sse_best / sse_static)) if sse_static > 1e-9 else 0.0

    def classify(self, t: float, s: Settings):
        # A speed estimate is only meaningful if it is larger than its own standard
        # error - otherwise the "motion" is just range noise being fitted.
        sweep = 0.0
        if len(self.rs) > 1:
            sweep = float(np.ptp(np.asarray(self.rs)[
                [i for i, t in enumerate(self.ts) if t >= self.ts[-1] - s.fit_window_s]]
                if s.fit_window_s > 0 else np.asarray(self.rs)))
        if sweep < s.min_range_sweep_m:
            self.speed = 0.0
            self.r2 = 0.0
            self.motion = "static"
            self.candidate = "static"
            return
        se = getattr(self, "speed_se", float("inf"))
        if math.isfinite(se) and abs(self.speed) <= s.static_speed_mps + s.speed_sigma_k * se:
            self.speed_significant = False
        else:
            self.speed_significant = True
        # A car crossing your detection bubble at 12 m/s is visible for ~2-3 s, so the
        # "wait for N samples" rule throws away most vehicles. When the fit is clean and
        # the speed is decisively high we accept it early.
        if s.adaptive_gate and self.samples >= 3 and self.r2 >= 0.5 \
                and abs(self.speed) > s.vehicle_min_mps * 1.4:
            self.motion = "vehicle"
            self.candidate = "vehicle"
            return
        if self.samples < s.min_track_samples or self.r2 < s.min_fit_r2:
            return
        cls = "static" if not getattr(self, "speed_significant", True) else _classify(self.speed, s)
        a = abs(self.speed)
        strong = self.r2 > 0.6 and (
            (cls == "static" and a < s.static_speed_mps * 0.5) or
            (cls == "pedestrian" and s.static_speed_mps * 1.6 < a < s.pedestrian_max_mps * 0.8) or
            (cls == "vehicle" and a > s.vehicle_min_mps * 1.25)
        )
        if cls != self.candidate:
            self.candidate = cls
            self.candidate_since = t
        if cls != self.motion and (strong or (t - self.candidate_since) >= 0.9):
            self.motion = cls


def is_infra(tr, now: float, s: Settings) -> bool:
    """Long-lived, perfectly still device => building infrastructure, not a person."""
    if not s.exclude_infrastructure:
        return False
    if tr.motion != "static":
        return False
    if now - tr.first_seen < s.infrastructure_dwell_s:
        return False
    recent = [r for t, r in zip(tr.ts, tr.rs) if t >= now - 30.0]
    if len(recent) < 5:
        return False
    return float(np.std(recent)) < s.infrastructure_range_std_m


class BleTracker:
    """Port of DeviceTracker with selectable fitting backend."""

    def __init__(self, settings: Settings, variant: str = "ema"):
        self.s = settings
        self.variant = variant
        self.tracks: Dict[str, _Track] = {}
        self.cls_history: Dict[str, str] = {}

    def ingest(self, obs):
        tr = self.tracks.get(obs.key)
        if tr is None:
            tr = _Track(obs.key, obs.tx, obs.t, obs.truth_cls, obs.static, obs.target_id)
            self.tracks[obs.key] = tr
        tr.add(obs, self.s)

    def tick(self, now: float, expire_s: float = 14.0, active_s: float = 5.0) -> List[TrackState]:
        s = self.s
        for key in [k for k, tr in self.tracks.items() if now - tr.last_seen > expire_s]:
            del self.tracks[key]
        out: List[TrackState] = []
        for tr in self.tracks.values():
            if self.variant == "kalman":
                tr.fit_kalman(s)
            elif self.variant == "pass":
                tr.fit_pass(s)
            elif self.variant == "hybrid":
                # the pass model is right when something is genuinely driving past;
                # otherwise the sequential filter tracks slow/gradual motion better
                tr.fit_kalman(s)
                v_kalman = tr.speed
                tr.fit_pass(s)
                if not getattr(tr, "pass_moving", False):
                    tr.speed = v_kalman
                    tr.speed_se = float("inf")
            else:
                tr.fit_ema(s)
            tr.classify(now, s)
            if now - tr.last_seen <= active_s:
                out.append(TrackState(
                    key=tr.key, rssi=float(tr.rssi_ema or -100.0), tx=tr.tx,
                    distance=float(tr.rs[-1]) if tr.rs else 99.0,
                    speed=float(tr.speed), r2=float(tr.r2), motion=tr.motion,
                    samples=tr.samples, total_samples=tr.total,
                    first_seen=tr.first_seen, last_seen=tr.last_seen,
                    truth_cls=tr.truth_cls, static_truth=tr.static_truth,
                    target_id=tr.target_id, infrastructure=is_infra(tr, now, s),
                    true_dist=float(tr.true_dist), truth_kind=tr.truth_kind,
                ))
        _assign_groups(out, s)
        return out


def _assign_groups(tracks: List[TrackState], s: Settings) -> None:
    """Union-find clustering of devices travelling together (port of the app)."""
    for t in tracks:
        t.group = -1
    if not s.group_devices or not tracks:
        return
    parent = {t.key: t.key for t in tracks}

    def find(a):
        while parent[a] != a:
            a = parent[a]
        return a

    def union(a, b):
        ra, rb = find(a), find(b)
        if ra != rb:
            parent[ra] = rb

    for i in range(len(tracks)):
        for j in range(i + 1, len(tracks)):
            a, b = tracks[i], tracks[j]
            max_slope = max(abs(a.speed), abs(b.speed))
            slope_close = abs(a.speed - b.speed) <= 0.35 * max_slope + 0.30
            same_dir = (a.speed >= 0) == (b.speed >= 0)
            radius = 1.5 if max_slope < s.static_speed_mps else 3.5
            dist_close = abs(a.distance - b.distance) <= radius
            confident = a.samples >= s.min_track_samples and b.samples >= s.min_track_samples
            if slope_close and same_dir and dist_close and confident:
                union(a.key, b.key)
    ids: Dict[str, int] = {}
    for t in tracks:
        root = find(t.key)
        t.group = ids.setdefault(root, len(ids) + 1)
    counts: Dict[int, int] = {}
    for t in tracks:
        counts[t.group] = counts.get(t.group, 0) + 1
    for t in tracks:
        if counts[t.group] == 1:
            t.group = -1


def group_entities(tracks: Iterable[TrackState]) -> List[TrackState]:
    """Lead device per cluster (largest |speed|), as the app does."""
    buckets: Dict[str, List[TrackState]] = {}
    for t in tracks:
        buckets.setdefault(("g%d" % t.group) if t.group >= 0 else ("s" + t.key), []).append(t)
    return [max(members, key=lambda m: abs(m.speed)) for members in buckets.values()]


# -------------------------------------------------------------------- fusion

def fuse(wifi: Dict[str, float], tracks: List[TrackState], spl: float,
         s: Settings, mode: str = "both") -> Dict[str, float]:
    entities = group_entities(tracks)
    mobile = [e for e in entities if not e.infrastructure]
    # A car is not a person, and a motionless cluster is more ambiguous than a walker
    # (it may be a parked car or a laptop), so standing entities get a reduced weight.
    people_like = 0.0
    near_detail = []
    for e in mobile:
        if e.distance <= s.proximity_radius_m and e.motion != "vehicle":
            w = 1.0 if e.motion == "pedestrian" else s.static_entity_weight
            people_like += w
            near_detail.append((float(e.distance), float(e.true_dist), e.motion, w,
                                e.truth_kind))
    entities_near = people_like
    vehicle_entities = sum(1 for e in mobile
                           if e.motion == "vehicle" and e.distance <= s.vehicle_radius_m)

    device_term = s.device_to_person_factor * entities_near
    rf_term = 0.0
    if mode in ("wifi", "both"):
        rf_term = (s.rf_person_coeff * wifi.get("cv_median", 0.0)
                   * math.sqrt(max(wifi.get("links_tracked", 1.0), 1.0))
                   + s.dip_person_coeff * wifi.get("dip_rate", 0.0)) * s.rf_area_scale
    if mode == "ble":
        people = device_term
    elif mode == "wifi":
        people = rf_term
    else:
        people = s.rules_device_weight * device_term + s.rules_rf_weight * rf_term

    vehicles = vehicle_entities + (
        s.vehicle_dip_coeff * wifi.get("dip_rate", 0.0) if mode in ("wifi", "both") else 0.0)
    return {
        "people_rules": float(people),
        "vehicle_rules": float(vehicles),
        "entities": float(len(mobile)),
        "entities_near": float(entities_near),
        "vehicle_entities": float(vehicle_entities),
        "devices": float(len(tracks)),
        "background_devices": float(len(entities) - len(mobile)),
        "spl": float(spl),
        "near_detail": near_detail,
    }


def feature_vector(wifi: Dict[str, float], tracks: List[TrackState], spl: float,
                   people_rules: float, hour: float = 12.0) -> np.ndarray:
    entities = group_entities(tracks)
    mobile = [e for e in entities if not e.infrastructure]
    return np.array([
        people_rules,
        float(len(mobile)),
        float(sum(1 for e in mobile if e.distance <= 25.0)),
        float(sum(1 for e in mobile if e.motion == "pedestrian")),
        float(sum(1 for e in mobile if e.motion == "static")),
        float(sum(1 for e in mobile if e.motion == "vehicle")),
        wifi.get("cv_median", 0.0),
        wifi.get("cv_p90", 0.0),
        wifi.get("std_median", 0.0),
        wifi.get("dip_rate", 0.0),
        wifi.get("churn", 0.0),
        wifi.get("ap_count", 0.0),
        float(spl),
        hour,
    ])


PEOPLE_FEATURES = ["rules", "ble_entities", "entities_near", "walkers", "standing",
                   "vehicles", "wifi_cv_med", "wifi_cv_p90", "wifi_std",
                   "dip_rate", "churn", "ap_count", "spl", "hour"]


# ----------------------------------------------------------------------- rls

class RLSModel:
    """Port of CalibrationModel (recursive least squares with forgetting)."""

    def __init__(self, n_features: int, forgetting: float = 0.985,
                 initial_covariance: float = 5.0):
        self.n = n_features + 1
        self.lam = forgetting
        self.w = np.zeros(self.n)
        self.P = np.eye(self.n) / initial_covariance
        self.mean = np.zeros(n_features)
        self.m2 = np.zeros(n_features)
        self.count = 0
        self.obs = 0
        self.abs_err = 0.0
        self.sq_err = 0.0
        self.sum_y = 0.0
        self.sum_y2 = 0.0
        self.bias_ema = 0.0

    def _z(self, x: np.ndarray, update: bool) -> np.ndarray:
        z = np.ones(self.n)
        for i, v in enumerate(x):
            if update:
                self.count += 1
                d = v - self.mean[i]
                self.mean[i] += d / self.count
                self.m2[i] += d * (v - self.mean[i])
            sd = math.sqrt(self.m2[i] / (self.count - 1)) if self.count > 1 else 0.0
            z[i + 1] = np.clip((v - self.mean[i]) / (sd if sd > 1e-6 else 1.0), -8, 8)
        return z

    def predict(self, x: np.ndarray) -> float:
        if self.obs == 0:
            return 0.0
        return float(self.w @ self._z(x, False))

    def update(self, x: np.ndarray, y: float):
        z = self._z(x, True)
        Px = self.P @ z
        denom = self.lam + float(z @ Px)
        if abs(denom) < 1e-9:
            return
        k = Px / denom
        err = y - float(self.w @ z)
        self.w += k * err
        self.P = (self.P - np.outer(k, Px)) / self.lam
        self.obs += 1
        self.abs_err += abs(err)
        self.sq_err += err * err
        self.sum_y += y
        self.sum_y2 += y * y
        self.bias_ema = err if self.obs == 1 else self.bias_ema + 0.25 * (err - self.bias_ema)

    def trust(self) -> float:
        if self.obs < 6:
            return 0.0
        return float(np.clip((self.obs - 6) / 24.0, 0.0, 1.0))

    def stats(self) -> Dict[str, float]:
        mae = self.abs_err / self.obs if self.obs else 0.0
        rmse = math.sqrt(self.sq_err / self.obs) if self.obs else 0.0
        r2 = None
        if self.obs > 2:
            my = self.sum_y / self.obs
            vy = self.sum_y2 / self.obs - my * my
            if vy > 1e-9:
                r2 = 1.0 - (self.sq_err / self.obs) / vy
        return {"obs": self.obs, "mae": mae, "rmse": rmse, "r2": r2,
                "bias": self.bias_ema}


# -------------------------------------------------------------------- metrics

def regression_metrics(y_true: Sequence[float], y_pred: Sequence[float]) -> Dict[str, float]:
    t = np.asarray(y_true, dtype=float)
    p = np.asarray(y_pred, dtype=float)
    err = p - t
    return {
        "n": int(len(t)),
        "mae": float(np.mean(np.abs(err))),
        "rmse": float(np.sqrt(np.mean(err ** 2))),
        "bias": float(np.mean(err)),
        "sd": float(np.std(err)),
        "within1": float(np.mean(np.abs(err) <= 1.0)),
        "within2": float(np.mean(np.abs(err) <= 2.0)),
        "pearson": float(np.corrcoef(t, p)[0, 1]) if len(t) > 2 and np.std(t) > 1e-9 and np.std(p) > 1e-9 else float("nan"),
    }


def confusion(y_true: Sequence[str], y_pred: Sequence[str],
              labels: Sequence[str] = ("static", "pedestrian", "vehicle")) -> Dict[str, Dict[str, int]]:
    cm = {a: {b: 0 for b in labels} for a in labels}
    for a, b in zip(y_true, y_pred):
        if a in cm and b in cm:
            cm[a][b] += 1
    return cm


def prf(cm: Dict[str, Dict[str, int]], labels=("static", "pedestrian", "vehicle")) -> Dict[str, Dict[str, float]]:
    out = {}
    for c in labels:
        tp = cm[c][c]
        fp = sum(cm[o][c] for o in labels if o != c)
        fn = sum(cm[c][o] for o in labels if o != c)
        prec = tp / (tp + fp) if tp + fp else 0.0
        rec = tp / (tp + fn) if tp + fn else 0.0
        f1 = 2 * prec * rec / (prec + rec) if prec + rec else 0.0
        out[c] = {"precision": prec, "recall": rec, "f1": f1, "support": tp + fn}
    total = sum(cm[a][b] for a in labels for b in labels)
    out["accuracy"] = {"accuracy": sum(cm[c][c] for c in labels) / total if total else 0.0}
    return out
