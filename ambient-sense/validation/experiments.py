"""
Experiment suite for Ambient Sense.

Every experiment runs the *ported app code* over simulated scenes, so a result here
is a prediction about the app. Nothing in this file touches the Android code; the
winners get ported by hand afterwards.

E0  Ranging accuracy vs distance          -> how precise is BLE range at all?
E1  Motion classification                 -> EMA vs Kalman vs pass-model, + thresholds
E2  Crowd counting                        -> BLE / Wi-Fi / both, uncalibrated vs calibrated
E3  Feature ablation                      -> which sensors actually earn their keep
E4  Forgetting factor under drift         -> how fast should the model forget?
E5  Sensing range envelope                -> how far can you actually sense?
E6  Wi-Fi scan interval                   -> what Android's scan throttling costs you
E7  Head-to-head: shipping vs upgraded    -> the headline number
"""

from __future__ import annotations

import json
import math
import os
import sys
import time
from typing import Dict, List, Optional, Sequence

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from rfscene import Scene, ENVIRONMENTS          # noqa: E402
from algos import (Settings, WifiFeatures, BleTracker, fuse, feature_vector,   # noqa: E402
                   RLSModel, regression_metrics, confusion, prf, group_entities,
                   range_from_rssi, PEOPLE_FEATURES)

RESULTS_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "results")
os.makedirs(RESULTS_DIR, exist_ok=True)


# --------------------------------------------------------------- scene builder

def make_scene(seed: int, env: str = "urban", duration: float = 300.0,
               n_ped: int = 6, n_veh: int = 3, wifi_interval: float = 30.0,
               standing_frac: float = 0.25, parked_frac: float = 0.2,
               ble_detect_prob: float = 0.75) -> Scene:
    """A scene with a realistic mix: walkers, standing people, moving and parked cars."""
    scene = Scene(seed=seed, env=env, duration=duration, n_pedestrians=n_ped,
                  n_vehicles=n_veh, wifi_scan_interval=wifi_interval,
                  ble_detect_prob=ble_detect_prob)
    rng = np.random.default_rng(seed * 7919 + 13)
    for tg in scene.targets:
        if tg.cls == "person" and rng.random() < standing_frac:
            tg.speed = 0.0
            tg.offset = float(rng.uniform(2.0, 22.0))
            tg.t_ca = 0.0
            tg.src_db = float(rng.uniform(44.0, 56.0))     # standing, maybe talking
        elif tg.cls == "vehicle" and rng.random() < parked_frac:
            tg.speed = 0.0
            tg.offset = float(rng.uniform(3.0, 25.0))
            tg.t_ca = 0.0
            tg.src_db = 28.0                               # engine off
    return scene


# ---------------------------------------------------------------- pipeline

def run_pipeline(seed: int = 0, env: str = "urban", duration: float = 300.0,
                 settings: Optional[Settings] = None, variant: str = "ema",
                 wifi_interval: float = 30.0, mode: str = "both",
                 n_ped: int = 10, n_veh: int = 5, dt: float = 1.0,
                 standing_frac: float = 0.25, parked_frac: float = 0.2,
                 ble_detect_prob: float = 0.75, truth_radius: float = 30.0,
                 hour: float = 12.0, collect_range: bool = False,
                 collect_gate: bool = False,
                 dt_ble: float = 1.0,
                 interference: Optional["InterferenceBank"] = None,
                 if_radius: float = 30.0,
                 if_features: bool = False) -> Dict:
    s = settings or Settings()
    scene = make_scene(seed, env, duration, n_ped, n_veh, wifi_interval,
                       standing_frac, parked_frac, ble_detect_prob)
    wifi = WifiFeatures()
    tracker = BleTracker(s, variant=variant)

    rows: List[Dict] = []
    pending: Dict[str, Dict] = {}
    outcomes: List[Dict] = []
    next_wifi = 0.0
    # Which devices are stationary enough to be used as RF *links* rather than as
    # tags. Decided one tick late, exactly as the app must decide it.
    link_keys: set = set()
    # Gate bookkeeping: of the entities the estimator actually counted, how many were
    # really inside the radius the user asked about?
    gate = dict(counted=0, counted_true_inside=0,
                counted_weight=0.0, counted_true_inside_weight=0.0,
                range_err=[])

    steps = int(duration / dt)
    for step in range(steps + 1):
        t = step * dt
        if t >= next_wifi - 1e-9:
            wifi.ingest(t, scene.wifi_scan(t))
            next_wifi = t + wifi_interval
        n_sub = max(1, int(round(dt / dt_ble)))
        for k in range(n_sub):
            tt = t - dt + dt_ble * (k + 1)
            for obs in scene.ble_observations(tt, dt_ble):
                tracker.ingest(obs)
                if interference is not None and obs.key in link_keys:
                    interference.ingest(tt, obs.key, obs.rssi)
        tracks = tracker.tick(t)
        if interference is not None:
            link_keys = {tr.key for tr in tracks
                         if tr.motion == "static" or tr.infrastructure}
        wf = wifi.tick(t)
        spl = scene.spl(t)
        f = fuse(wf, tracks, spl, s, mode=mode)
        tp, tv = scene.truth(t, truth_radius)
        if_stats = interference.tick(t, if_radius) if interference is not None else None
        feat = feature_vector(wf, tracks, spl, f["people_rules"], hour=hour,
                             if_stats=if_stats if if_features else None)

        near_inside = 0.0
        near_counted = 0.0
        near_person_inside = 0.0
        near_person = 0.0
        if collect_gate:
            for est, tru, motion, w, kind in f["near_detail"]:
                gate["counted"] += 1
                gate["counted_weight"] += w
                near_counted += w
                if kind == "person":
                    near_person += w
                if 0.0 <= tru <= s.proximity_radius_m:
                    gate["counted_true_inside"] += 1
                    gate["counted_true_inside_weight"] += w
                    near_inside += w
                    if kind == "person":
                        near_person_inside += w
                if tru > 0.0:
                    gate["range_err"].append(abs(est - tru) / tru)

        rows.append(dict(t=t, tp=float(tp), tv=float(tv), spl=spl,
                         rules_p=f["people_rules"], rules_v=f["vehicle_rules"],
                         entities=f["entities"], entities_near=f["entities_near"],
                         devices=f["devices"], wifi=wf, feat=feat,
                         near_inside=float(near_inside),
                         near_counted=float(near_counted),
                         near_person_inside=float(near_person_inside),
                         near_person=float(near_person),
                         ifs=if_stats))

        # ---- device level bookkeeping
        live = {tr.key for tr in tracks}
        for tr in tracks:
            rec = pending.setdefault(tr.key, dict(
                truth=tr.truth_cls, pred=tr.motion, samples=tr.samples,
                speed=tr.speed, r2=tr.r2, target_id=tr.target_id,
                true_dist=float(getattr(tr, 'true_dist', -1.0)),
                speed_se=getattr(tr, "speed_se", float("inf")),
                dists=[], static=tr.static_truth))
            rec["pred"] = tr.motion
            rec["samples"] = tr.samples
            rec["speed"] = tr.speed
            rec["r2"] = tr.r2
            rec["dists"].append(tr.distance)
        for key in list(pending):
            if key not in live:
                rec = pending.pop(key)
                if rec["dists"]:
                    rec["dist"] = float(np.median(rec["dists"]))
                    rec["min_dist"] = float(np.min(rec["dists"]))
                outcomes.append(rec)

    for key, rec in pending.items():
        if rec["dists"]:
            rec["dist"] = float(np.median(rec["dists"]))
            rec["min_dist"] = float(np.min(rec["dists"]))
            outcomes.append(rec)

    result = dict(rows=rows, outcomes=outcomes, scene=scene, settings=s,
                  variant=variant, mode=mode, env=env, seed=seed, gate=gate)

    if collect_range:
        # per-target detection: did any device of that target get classified correctly?
        detected: Dict[int, str] = {}
        for rec in outcomes:
            if rec["target_id"] >= 0 and rec["samples"] >= s.min_track_samples:
                prev = detected.get(rec["target_id"])
                order = {"unknown": 0, "static": 1, "pedestrian": 2, "vehicle": 3}
                if prev is None or order[rec["pred"]] > order[prev]:
                    detected[rec["target_id"]] = rec["pred"]
        per_target = []
        for i, tg in enumerate(scene.targets):
            per_target.append(dict(
                cls=tg.cls, offset=float(tg.offset), speed=float(tg.speed),
                truth="pedestrian" if (tg.cls == "person" and tg.speed > 0.3)
                      else ("vehicle" if (tg.cls == "vehicle" and tg.speed > 0.3) else "static"),
                pred=detected.get(i, "missed")))
        result["per_target"] = per_target
    return result


def many_runs(n: int, **kwargs) -> List[Dict]:
    return [run_pipeline(seed=1000 + i, **kwargs) for i in range(n)]


# --------------------------------------------------------------- E0: ranging

def e0_ranging(n_scenes: int = 6) -> Dict:
    """How accurate is a BLE range estimate, purely as a function of distance?"""
    bins = [(0, 3), (3, 6), (6, 10), (10, 16), (16, 25), (25, 40), (40, 70)]
    acc = {b: [] for b in bins}
    for i in range(n_scenes):
        scene = make_scene(1000 + i)
        for dev in scene.devices:
            if dev.target is None:
                continue
            tgt = dev.target
            t = tgt.t_ca if tgt.speed > 0 else 0.0
            px, py = dev.target.position(t)
            d_true = math.hypot(px, py)
            if d_true > 70:
                continue
            for _ in range(3):
                pl = scene._path_loss(d_true) - 40.0 + dev.body_loss_db
                rssi = dev.tx_ref - pl + float(np.random.default_rng(i).normal(0, 4.0))
                d_est = range_from_rssi(rssi,
                                        dev.tx_ref if dev.advertises_tx else -59.0,
                                        scene.env["n"])
                for b in bins:
                    if b[0] <= d_true < b[1]:
                        acc[b].append((d_est - d_true) / d_true)
                        break
    out = {}
    for b in bins:
        v = np.asarray(acc[b])
        if len(v) == 0:
            continue
        out["%g-%gm" % b] = dict(
            n=int(len(v)),
            bias_pct=float(np.mean(v) * 100),
            rmse_pct=float(np.sqrt(np.mean(v ** 2)) * 100),
            p68_pct=float(np.percentile(np.abs(v), 68) * 100),
            p95_pct=float(np.percentile(np.abs(v), 95) * 100),
        )
    return out


# --------------------------------------------------- E1: motion classification

def e1_classification(variants: Sequence[str] = ("ema", "kalman", "pass", "pass_gate"),
                      n_scenes: int = 8, duration: float = 300.0) -> Dict:
    out = {}
    for variant in variants:
        kw = {}
        real = variant
        if variant == "pass_gate":
            real = "pass"
            kw = dict(settings=Settings(adaptive_gate=True, min_track_samples=3))
        runs = many_runs(n_scenes, variant=real, duration=duration,
                         collect_range=True, wifi_interval=30.0, **kw)
        recs = []
        for r in runs:
            for o in r["outcomes"]:
                if o["samples"] >= r["settings"].min_track_samples and o.get("dist") is not None:
                    recs.append(o)
        cm = confusion([r["truth"] for r in recs], [r["pred"] for r in recs])
        scores = prf(cm)
        # by distance
        by_dist = {}
        for lo, hi in [(0, 5), (5, 12), (12, 25), (25, 60)]:
            sub = [r for r in recs if lo <= r["dist"] < hi]
            if len(sub) < 10:
                continue
            c = confusion([r["truth"] for r in sub], [r["pred"] for r in sub])
            by_dist["%g-%gm" % (lo, hi)] = prf(c)
        out[variant] = dict(
            cm=cm, scores=scores, by_distance=by_dist,
            n_classified=len(recs),
            mean_speed_err={cls: float(np.mean([abs(r["speed"]) for r in recs
                                                if r["truth"] == cls])) if any(
                r["truth"] == cls for r in recs) else 0.0
                for cls in ("static", "pedestrian", "vehicle")},
        )
    return out


def e1_threshold_search(variant: str = "pass", n_scenes: int = 6,
                        duration: float = 300.0) -> Dict:
    """Grid the speed thresholds and the significance gate."""
    cfg = Settings(adaptive_gate=True, min_track_samples=3)
    runs = many_runs(n_scenes, variant=variant, duration=duration, settings=cfg)
    recs = []
    for r in runs:
        for o in r["outcomes"]:
            if o.get("dist") is not None:
                recs.append(o)
    best = None
    grid_static = [0.3, 0.45, 0.6, 0.8, 1.0, 1.3]
    grid_veh = [1.6, 2.0, 2.4, 3.0, 3.6, 4.5]
    grid_k = [0.5, 1.0, 2.0]
    table = []
    for gs in grid_static:
        for gv in grid_veh:
            for gk in grid_k:
                if gv <= gs + 0.4:
                    continue
            preds = []
            for r in recs:
                a = abs(r["speed"])
                se = r.get("speed_se", float("inf"))
                sig = (a > gs + gk * se) if math.isfinite(se) else (a > gs)
                preds.append("static" if not sig else ("pedestrian" if a <= gv else "vehicle"))
            sc = prf(confusion([r["truth"] for r in recs], preds))
            macro = float(np.mean([sc[c]["f1"] for c in ("static", "pedestrian", "vehicle")]))
            acc = sc["accuracy"]["accuracy"]
            table.append(dict(static=gs, vehicle=gv, k_sigma=gk, macro_f1=macro, accuracy=acc,
                              veh_recall=sc["vehicle"]["recall"],
                              veh_precision=sc["vehicle"]["precision"],
                              ped_recall=sc["pedestrian"]["recall"]))
            score = macro
            if best is None or score > best[0]:
                best = (score, gs, gv, table[-1])
    return dict(best_static=best[1], best_vehicle=best[2], best_score=best[0],
                best_row=best[3], table=table)


# --------------------------------------------------------------- E2: counting

def _pick_label_times(rows: List[Dict], k: int, rng: np.random.Generator,
                      train_fraction: float = 0.6) -> List[int]:
    n = len(rows)
    cut = int(n * train_fraction)
    if k <= 0 or cut < 2:
        return []
    # spread labels over the training part, at least 8 s apart
    idxs = sorted(rng.choice(cut, size=min(k, cut), replace=False).tolist())
    return idxs


def eval_counts(rows: List[Dict], k_labels: int = 0, seed: int = 0,
                forgetting: float = 0.985, feature_mask: Optional[List[int]] = None,
                apply_model: bool = True) -> Dict[str, Dict[str, float]]:
    """Train on k labels from the first 60% of a run, evaluate on the last 40%."""
    rng = np.random.default_rng(seed)
    idxs = _pick_label_times(rows, k_labels, rng)
    model = RLSModel(len(PEOPLE_FEATURES), forgetting=forgetting)
    for i in idxs:
        r = rows[i]
        x = r["feat"] if feature_mask is None else r["feat"][feature_mask]
        model.update(x, r["tp"] - r["rules_p"])
    cut = int(len(rows) * 0.6)
    eval_rows = rows[cut:] if k_labels > 0 else rows
    pred_p, true_p, pred_v, true_v, rules_p, rules_v = [], [], [], [], [], []
    for r in eval_rows:
        x = r["feat"] if feature_mask is None else r["feat"][feature_mask]
        corr = model.predict(x) * model.trust() if apply_model else 0.0
        pred_p.append(max(0.0, r["rules_p"] + corr))
        true_p.append(r["tp"])
        rules_p.append(r["rules_p"])
        true_v.append(r["tv"])
        rules_v.append(r["rules_v"])
    return {
        "people": regression_metrics(true_p, pred_p),
        "people_rules_only": regression_metrics(true_p, rules_p),
        "vehicles_rules_only": regression_metrics(true_v, rules_v),
        "model": model.stats(),
    }


def e2_counting(n_scenes: int = 10, duration: float = 300.0) -> Dict:
    out = {}
    for mode in ("ble", "wifi", "both"):
        for wifi_interval in (2.0, 30.0):
            runs = many_runs(n_scenes, mode=mode, duration=duration,
                             wifi_interval=wifi_interval)
            for k in (0, 5, 10, 20, 40):
                agg = {"people": [], "people_rules_only": [], "vehicles_rules_only": [],
                       "model": []}
                for j, r in enumerate(runs):
                    m = eval_counts(r["rows"], k_labels=k, seed=7 + j)
                    for key in agg:
                        agg[key].append(m[key])
                key = f"{mode}_scan{wifi_interval:g}s_k{k}"
                out[key] = {
                    "mae": float(np.mean([a["mae"] for a in agg["people"]])),
                    "rmse": float(np.mean([a["rmse"] for a in agg["people"]])),
                    "bias": float(np.mean([a["bias"] for a in agg["people"]])),
                    "within1": float(np.mean([a["within1"] for a in agg["people"]])),
                    "pearson": float(np.nanmean([a["pearson"] for a in agg["people"]])),
                    "mae_rules": float(np.mean([a["mae"] for a in agg["people_rules_only"]])),
                    "veh_mae_rules": float(np.mean([a["mae"] for a in agg["vehicles_rules_only"]])),
                    "model_obs": float(np.mean([a["obs"] for a in agg["model"]])),
                }
    return out


def e2b_cross_scene(n_scenes: int = 8, duration: float = 300.0) -> Dict:
    """Train on one place, measure in another: the honest generalisation number."""
    out = {}
    for k in (0, 10, 25, 40):
        maes, maes_rules = [], []
        for i in range(n_scenes):
            train_rows = run_pipeline(seed=4000 + i, duration=duration,
                                      wifi_interval=30.0)["rows"]
            test_rows = run_pipeline(seed=5000 + i, duration=duration,
                                     wifi_interval=30.0)["rows"]
            model = RLSModel(len(PEOPLE_FEATURES))
            rng = np.random.default_rng(17 + i)
            for idx in sorted(rng.choice(len(train_rows) - 1, k, replace=False).tolist()):
                r = train_rows[idx]
                model.update(r["feat"], r["tp"] - r["rules_p"])
            pred, truth = [], []
            for r in test_rows:
                pred.append(max(0.0, r["rules_p"] + model.predict(r["feat"]) * model.trust()))
                truth.append(r["tp"])
            maes.append(regression_metrics(truth, pred)["mae"])
            maes_rules.append(regression_metrics(truth, [r["rules_p"] for r in test_rows])["mae"])
        out[f"k{k}"] = dict(mae_cross_scene=float(np.mean(maes)),
                            mae_rules=float(np.mean(maes_rules)))
    return out


# --------------------------------------------------------- E3: feature ablation

def e3_ablation(n_scenes: int = 8, duration: float = 300.0, k: int = 25) -> Dict:
    runs = many_runs(n_scenes, duration=duration, mode="both", wifi_interval=30.0)
    idx_all = list(range(len(PEOPLE_FEATURES)))
    wifi_idx = [6, 7, 8, 9, 10, 11]
    ble_idx = [1, 2, 3, 4, 5]
    variants = {
        "all_features": idx_all,
        "no_wifi": [i for i in idx_all if i not in wifi_idx],
        "no_ble": [i for i in idx_all if i not in ble_idx],
        "no_sound": [i for i in idx_all if i != 12],
        "rules_only": idx_all,      # model disabled below
        "entities_and_rules_only": [0, 1, 2, 12],
    }
    out = {}
    for name, mask in variants.items():
        maes, rmses = [], []
        for j, r in enumerate(runs):
            m = eval_counts(r["rows"], k_labels=k, seed=11 + j,
                            feature_mask=mask, apply_model=(name != "rules_only"))
            maes.append(m["people"]["mae"])
            rmses.append(m["people"]["rmse"])
        out[name] = dict(mae=float(np.mean(maes)), rmse=float(np.mean(rmses)))
    return out


# -------------------------------------------------------------- E4: forgetting

def e4_forgetting(n_scenes: int = 6, duration: float = 200.0) -> Dict:
    """
    Drift: the user measures in env A, then moves to env B. Older labels are still
    true but describe somewhere else - how fast should the model forget them?
    """
    out = {}
    for lam in (0.90, 0.95, 0.98, 0.99, 1.0):
        maes_new, maes_old = [], []
        for i in range(n_scenes):
            rows_a = run_pipeline(seed=2000 + i, env="urban", duration=duration,
                                  wifi_interval=30.0)["rows"]
            rows_b = run_pipeline(seed=3000 + i, env="indoor", duration=duration,
                                  wifi_interval=30.0)["rows"]
            model = RLSModel(len(PEOPLE_FEATURES), forgetting=lam)
            rng = np.random.default_rng(5 + i)
            # 20 labels in environment A, then 20 in environment B
            for idx in sorted(rng.choice(len(rows_a) - 1, 20, replace=False).tolist()):
                r = rows_a[idx]
                model.update(r["feat"], r["tp"] - r["rules_p"])
            for idx in sorted(rng.choice(len(rows_b) // 2, 20, replace=False).tolist()):
                r = rows_b[idx]
                model.update(r["feat"], r["tp"] - r["rules_p"])
            tail_b, tail_a = rows_b[len(rows_b) // 2:], rows_a[len(rows_a) // 2:]
            for r in tail_b:
                maes_new.append(abs(max(0.0, r["rules_p"] + model.predict(r["feat"]) * model.trust()) - r["tp"]))
            for r in tail_a:
                maes_old.append(abs(max(0.0, r["rules_p"] + model.predict(r["feat"]) * model.trust()) - r["tp"]))
        out["lambda_%.2f" % lam] = dict(
            mae_new_env=float(np.mean(maes_new)),
            mae_old_env=float(np.mean(maes_old)),
        )
    return out


# ------------------------------------------------------------- E5: range envelope

def e5_range(settings=None, variant="pass", n_scenes: int = 14,
             duration: float = 300.0,
             bins=((0, 5), (5, 10), (10, 15), (15, 20), (20, 30), (30, 45))) -> Dict:
    """
    Sensing range envelope, binned by how close the target actually passes.
    'tracked'      = at least one of its devices produced a classifiable track
    'correct'      = at least one track got the motion class right
    """
    cfg = settings or Settings()
    runs = many_runs(n_scenes, settings=cfg, variant=variant, duration=duration,
                     collect_range=True, wifi_interval=30.0)
    out: Dict[str, Dict[str, Dict[str, float]]] = {}
    for cls in ("pedestrian", "vehicle"):
        per_bin: Dict[str, Dict[str, float]] = {}
        for lo, hi in bins:
            tot = tracked = correct = 0
            samples_seen = []
            for r in runs:
                for pt in r["per_target"]:
                    if pt["truth"] != cls or not (lo <= pt["offset"] < hi):
                        continue
                    tot += 1
                    if pt["pred"] != "missed":
                        tracked += 1
                    if pt["pred"] == cls:
                        correct += 1
            if tot:
                per_bin["%g-%gm" % (lo, hi)] = dict(
                    n=tot,
                    tracked=tracked / tot,
                    classified_correct=correct / tot,
                )
        out[cls] = per_bin
    return out


def e5_counting_vs_radius(n_scenes: int = 8, duration: float = 240.0,
                          radii=(10, 20, 30, 45, 60)) -> Dict:
    """MAE of the people/vehicle estimate when the truth is defined at radius R."""
    out = {}
    for R in radii:
        runs = many_runs(n_scenes, variant="pass", duration=duration,
                         truth_radius=float(R), wifi_interval=30.0)
        for k in (0, 20):
            maes_p, maes_v = [], []
            for j, r in enumerate(runs):
                m = eval_counts(r["rows"], k_labels=k, seed=3 + j)
                maes_p.append(m["people"]["mae"])
                maes_v.append(m["vehicles_rules_only"]["mae"])
            out[f"R{R}_k{k}"] = dict(people_mae=float(np.mean(maes_p)),
                                     vehicle_mae=float(np.mean(maes_v)))
    return out


# --------------------------------------------------------- E6: scan interval

def e6_scan_interval(n_scenes: int = 6, duration: float = 300.0) -> Dict:
    """What Android's Wi-Fi scan throttling costs the device-free channel."""
    out = {}
    for interval in (1.0, 2.0, 5.0, 10.0, 30.0, 60.0):
        runs = many_runs(n_scenes, mode="wifi", duration=duration,
                         wifi_interval=interval)
        cors_dip, cors_cv, cors_act, maes = [], [], [], []
        for j, r in enumerate(runs):
            rows = r["rows"]
            truth = np.array([row["tp"] + row["tv"] for row in rows], dtype=float)
            dip = np.array([row["wifi"]["dip_rate"] for row in rows], dtype=float)
            cv = np.array([row["wifi"]["cv_median"] for row in rows], dtype=float)
            act = np.array([row["wifi"]["activity"] for row in rows], dtype=float)

            def corr(a):
                if np.std(a) < 1e-9 or np.std(truth) < 1e-9:
                    return 0.0
                return float(np.corrcoef(a, truth)[0, 1])

            cors_dip.append(corr(dip))
            cors_cv.append(corr(cv))
            cors_act.append(corr(act))
            maes.append(eval_counts(rows, k_labels=20, seed=21 + j)["people"]["mae"])
        out["scan_%gs" % interval] = dict(
            corr_dip=float(np.mean(cors_dip)),
            corr_cv=float(np.mean(cors_cv)),
            corr_activity=float(np.mean(cors_act)),
            people_mae_wifi_only=float(np.mean(maes)),
        )
    return out


# ------------------------------------------------- E7: shipping vs upgraded

SHIPPING = Settings(pedestrian_max_mps=2.4, vehicle_min_mps=3.0, static_speed_mps=0.45,
                    device_to_person_factor=1.15, rf_person_coeff=6.0,
                    rules_device_weight=0.55, rules_rf_weight=0.45,
                    forgetting=0.985)


def upgraded(static_thr, veh_thr, dev_factor, rf_coeff, w_dev, lam, k_sigma=1.0):
    return Settings(static_speed_mps=static_thr, pedestrian_max_mps=veh_thr * 0.8,
                    vehicle_min_mps=veh_thr, device_to_person_factor=dev_factor,
                    rf_person_coeff=rf_coeff, rules_device_weight=w_dev,
                    rules_rf_weight=1.0 - w_dev, forgetting=lam,
                    adaptive_gate=True, min_track_samples=3, speed_sigma_k=k_sigma)


def e7_head_to_head(n_scenes: int = 10, duration: float = 300.0,
                    best_static: float = 0.45, best_vehicle: float = 3.0) -> Dict:
    out = {}
    configs = {
        "shipping_ema": (SHIPPING, "ema"),
        "shipping_pass": (SHIPPING, "pass"),
        "upgraded_pass": (upgraded(best_static, best_vehicle, 1.15, 6.0, 0.75, 0.98), "pass"),
        "upgraded_kalman": (upgraded(best_static, best_vehicle, 1.15, 6.0, 0.75, 0.98), "kalman"),
    }
    for name, (cfg, variant) in configs.items():
        runs = many_runs(n_scenes, settings=cfg, variant=variant, duration=duration,
                         mode="both", wifi_interval=30.0)
        for k in (0, 10, 25):
            mp, mv, mr = [], [], []
            for j, r in enumerate(runs):
                m = eval_counts(r["rows"], k_labels=k, seed=31 + j,
                                forgetting=cfg.forgetting)
                mp.append(m["people"]["mae"])
                mv.append(m["vehicles_rules_only"]["mae"])
                mr.append(m["people"]["rmse"])
            out[f"{name}_k{k}"] = dict(
                people_mae=float(np.mean(mp)),
                people_rmse=float(np.mean(mr)),
                vehicle_mae=float(np.mean(mv)),
            )
        # classification on the same runs
        recs = [o for r in runs for o in r["outcomes"]
                if o.get("dist") is not None and o["samples"] >= cfg.min_track_samples]
        sc = prf(confusion([r["truth"] for r in recs], [r["pred"] for r in recs]))
        out[f"{name}_classification"] = {
            c: {k2: float(v) for k2, v in sc[c].items()} for c in
            ("static", "pedestrian", "vehicle", "accuracy")
        }
    return out


# ------------------------------------------------------------------------ cli

def main(which: str = "all"):
    t0 = time.time()
    results: Dict[str, object] = {}
    jobs = [
        ("e0_ranging", lambda: e0_ranging(6)),
        ("e1_classification", lambda: e1_classification(n_scenes=8)),
        ("e1_thresholds", lambda: e1_threshold_search(variant="pass", n_scenes=6)),
        ("e2_counting", lambda: e2_counting(n_scenes=10)),
        ("e3_ablation", lambda: e3_ablation(n_scenes=8)),
        ("e4_forgetting", lambda: e4_forgetting(n_scenes=6)),
        ("e5_range", lambda: e5_range(n_scenes=12)),
        ("e5_counting_radius", lambda: e5_counting_vs_radius(n_scenes=6)),
        ("e6_scan_interval", lambda: e6_scan_interval(n_scenes=6)),
    ]
    for name, fn in jobs:
        if which != "all" and which not in name:
            continue
        print(f"[running] {name} ...", flush=True)
        t = time.time()
        results[name] = fn()
        print(f"          done in {time.time() - t:.1f}s", flush=True)

    if which in ("all", "e7"):
        thr = results.get("e1_thresholds", {})
        bs = thr.get("best_static", 0.45)
        bv = thr.get("best_vehicle", 3.0)
        bk = thr.get("best_row", {}).get("k_sigma", 1.0)
        print(f"[running] e7_head_to_head (thresholds {bs}/{bv}, k_sigma {bk}) ...", flush=True)
        results["e7_head_to_head"] = e7_head_to_head(n_scenes=10,
                                                     best_static=bs, best_vehicle=bv)
        results["tuned"] = dict(static_thr=bs, vehicle_thr=bv, k_sigma=bk)

    path = os.path.join(RESULTS_DIR, "results.json")
    with open(path, "w") as fh:
        json.dump(results, fh, indent=2, default=str)
    print(f"\nwrote {path} in {time.time() - t0:.1f}s")


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else "all")
