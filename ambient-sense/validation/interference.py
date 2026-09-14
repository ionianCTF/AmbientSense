"""
Device-free sensing on BLE *links*.

The app already does *device-based* BLE: every advert belongs to somebody, we range
it, we track it. This module does the opposite and is the part that works on people
and vehicles carrying nothing at all.

The physics
-----------
A stationary BLE emitter (a beacon, a laptop, a TV, a parked car's TPMS sender) and
the phone form a radio link. Anything that moves through that link perturbs it in two
ways:

  * **Scattering** — the body re-radiates the field, so the direct and scattered
    phasors interfere. As the body crosses Fresnel-zone boundaries the sum alternates
    between constructive and destructive, which is why a crossing shows up as a
    *sequence of troughs and peaks* rather than one dip
    (Zhang et al., "Fresnel Zone Based Theories for Contactless Sensing").
  * **Blocking** — a body in the first Fresnel zone attenuates the line of sight.

The result is a deformation of the RSSI series, which is exactly what Iannizzotto,
Lo Bello & Nucita ("Passive Indoor People Counting by Bluetooth Signal Deformation
Analysis with Deep Learning", Applied Sciences 15(11):6142, 2025) showed is enough to
count people with a BLE network, reaching >99% on their metric after a short
fine-tuning. We cannot ship their CNN+LSTM (no pre-trained net, tens of labels, no
server), so we take two things from that paper and one thing from the Fresnel
literature instead:

  1. **Median-of-k aggregation.** BLE's adaptive frequency hopping means each advert
     may arrive on a different channel with a different mean RSSI, and the app is not
     allowed to know which. Their fix - take the median of every k samples - is free
     and it is the difference between signal and mush.
  2. **Links, not devices.** A stationary emitter is a sensor; a moving one is a tag.
     Only the stationary ones feed this module.
  3. **Event geometry instead of a network.** Each trough-peak pair has an amplitude
     (target size / radar cross-section) and a duration (how long the target took to
     cross the zone → its speed). A car has ~20x the RCS of a person and crosses
     several times faster, so the two populations separate in the
     (amplitude, duration) plane. That gives the human/vehicle classification the app
     needs without a labelled dataset.

Counting uses Little's law, N = λ·W: the event rate gives the arrival rate λ (once
divided by the events one body produces on a full transit), and the dwell time W is
how long a body stays in the region, (2·R)/v for a straight crossing of a radius-R
disc. The multiplicative constant is fitted once from simulated data and then refined
online by the user's labels.

Why this matters more than the Wi-Fi version of the same idea: Android throttles
Wi-Fi scans to about one per 30 s in the foreground, while BLE adverts arrive at
1-10 Hz. The Wi-Fi dip detector is trying to see 0.5-second blocking events through a
sampler that runs 300x slower than the event.
"""

from __future__ import annotations

import math
from dataclasses import dataclass, field
from typing import Dict, List, Optional, Sequence, Tuple

import numpy as np


@dataclass
class InterferenceSettings:
    enabled: bool = True
    # Median-of-k aggregation (Iannizzotto et al.): kills frequency-hop outliers.
    agg_window_s: float = 0.5
    # Slow baseline; a room's mean RSSI drifts, the fluctuations we want do not.
    baseline_tau_s: float = 20.0
    # Statistics and event window.
    window_s: float = 20.0
    # Hysteresis: an extremum only counts once the signal swings back this far.
    deadband_db: float = 1.2
    deadband_sigma_k: float = 2.0
    min_amplitude_db: float = 1.0
    # Human / vehicle separation, measured in e9a_event_signatures().
    veh_amplitude_db: float = 4.0
    veh_duration_s: float = 0.7
    # Little's law
    walk_speed_mps: float = 1.4
    veh_speed_mps: float = 12.0
    path_factor: float = 2.0          # chord length across the region = 2R
    people_gain: float = 1.0          # fitted: events -> bodies
    vehicle_gain: float = 1.0
    # A link must be this quiet when empty, else it is too noisy to sense with.
    max_idle_sigma_db: float = 3.0


@dataclass
class IfEvent:
    t: float              # time of the extremum
    amplitude_db: float   # swing from the previous extremum
    duration_s: float     # time since the previous extremum
    rising: bool          # True if the extremum is a peak


class LinkState:
    """One stationary emitter, watched as a sensor rather than as a tag."""

    def __init__(self, key: str):
        self.key = key
        self._bin: List[float] = []
        self._bin_t0: Optional[float] = None
        self.baseline: Optional[float] = None
        self.last_t: Optional[float] = None
        # residual ring buffer (t, residual)
        self.res: List[Tuple[float, float]] = []
        # extremum tracking
        self.dir = 0                      # -1 falling (hunting a trough), +1 rising
        self.ext_val = 0.0
        self.ext_t = 0.0
        self.prev_val = 0.0
        self.prev_t = 0.0
        self.events: List[IfEvent] = []
        self.last_seen = 0.0
        self.samples = 0

    # ------------------------------------------------------------------ ingest
    def add(self, t: float, rssi: float, s: InterferenceSettings):
        self.last_seen = t
        if self._bin_t0 is None:
            self._bin_t0 = t
        self._bin.append(rssi)
        if t - self._bin_t0 >= s.agg_window_s:
            self._flush(s)

    def _flush(self, s: InterferenceSettings):
        if not self._bin or self._bin_t0 is None:
            return
        t = self._bin_t0 + 0.5 * (self.last_seen - self._bin_t0)
        x = float(np.median(self._bin))       # median-of-k: robust to hops/outliers
        self._bin = []
        self._bin_t0 = None
        self.samples += 1

        if self.baseline is None:
            self.baseline = x
            self.last_t = t
            self.prev_val, self.prev_t = 0.0, t
            self.ext_val, self.ext_t = 0.0, t
            return

        dt = max(t - (self.last_t or t), 1e-3)
        alpha = 1.0 - math.exp(-dt / s.baseline_tau_s)
        residual = x - self.baseline
        self.baseline += alpha * (x - self.baseline)
        self.last_t = t

        self.res.append((t, residual))
        self._detect(t, residual, s)

    # ------------------------------------------------------- Fresnel detector
    def _detect(self, t: float, x: float, s: InterferenceSettings):
        """Alternating extrema with hysteresis = trough/peak sequence of a crossing."""
        sigma = self.sigma_db(s.window_s)
        dead = max(s.deadband_db, s.deadband_sigma_k * (sigma if sigma > 0 else 0.5))

        if self.dir == 0:
            self.dir = 1 if x >= 0 else -1
            self.ext_val, self.ext_t = x, t
            return

        if self.dir == -1:                       # hunting a trough
            if x < self.ext_val:
                self.ext_val, self.ext_t = x, t
            elif x > self.ext_val + dead:        # swung back up: trough confirmed
                self._emit(self.ext_t, self.ext_val, False, s)
                self.dir = 1
                self.ext_val, self.ext_t = x, t
        else:                                    # hunting a peak
            if x > self.ext_val:
                self.ext_val, self.ext_t = x, t
            elif x < self.ext_val - dead:        # swung back down: peak confirmed
                self._emit(self.ext_t, self.ext_val, True, s)
                self.dir = -1
                self.ext_val, self.ext_t = x, t

    def _emit(self, t: float, val: float, rising: bool, s: InterferenceSettings):
        amp = abs(val - self.prev_val)
        dur = max(t - self.prev_t, 1e-3)
        if amp >= s.min_amplitude_db:
            self.events.append(IfEvent(t, amp, dur, rising))
        self.prev_val = val
        self.prev_t = t

    # ------------------------------------------------------------------ stats
    def prune(self, now: float, s: InterferenceSettings):
        cut = now - s.window_s
        self.res = [(t, r) for (t, r) in self.res if t >= cut]
        self.events = [e for e in self.events if e.t >= cut]

    def sigma_db(self, window_s: float = 20.0) -> float:
        """Robust (MAD) scatter of the residual."""
        if len(self.res) < 4:
            return 0.0
        v = np.array([r for (_, r) in self.res], dtype=float)
        med = float(np.median(v))
        mad = float(np.median(np.abs(v - med)))
        return 1.4826 * mad


class InterferenceBank:
    """All the stationary links, and the aggregate read-out they produce."""

    def __init__(self, settings: Optional[InterferenceSettings] = None):
        self.s = settings or InterferenceSettings()
        self.links: Dict[str, LinkState] = {}

    def ingest(self, t: float, key: str, rssi: float):
        if not self.s.enabled:
            return
        link = self.links.get(key)
        if link is None:
            link = LinkState(key)
            self.links[key] = link
        link.add(t, rssi, self.s)

    def expire(self, now: float, ttl_s: float = 60.0):
        for k in [k for k, l in self.links.items() if now - l.last_seen > ttl_s]:
            del self.links[k]

    def tick(self, now: float, radius_m: float) -> Dict[str, float]:
        s = self.s
        if not s.enabled:
            return _empty_stats()

        self.expire(now)
        for link in self.links.values():
            link.prune(now, s)

        sigmas, rates, amps, durs, iets = [], [], [], [], []
        human_rate = veh_rate = 0.0
        active = 0
        usable = 0
        span = s.window_s / 60.0

        for link in self.links.values():
            if link.samples < 6:
                continue
            sig = link.sigma_db(s.window_s)
            if sig > s.max_idle_sigma_db:
                continue                      # too noisy when presumably empty
            usable += 1
            sigmas.append(sig)
            evs = link.events
            if evs:
                active += 1
                rates.append(len(evs) / span)
                amps.extend(e.amplitude_db for e in evs)
                durs.extend(e.duration_s for e in evs)
                ts = [e.t for e in evs]
                iets.extend(np.diff(ts) for ts in [ts] if len(ts) > 1)
                for e in evs:
                    if e.amplitude_db >= s.veh_amplitude_db or e.duration_s <= s.veh_duration_s:
                        veh_rate += 1.0
                    else:
                        human_rate += 1.0

        human_rate /= 60.0                    # events per second
        veh_rate /= 60.0
        dwell_walk = s.path_factor * radius_m / s.walk_speed_mps
        dwell_veh = s.path_factor * radius_m / s.veh_speed_mps

        def mean(a):
            return float(np.mean(a)) if a else 0.0

        def p90(a):
            return float(np.percentile(a, 90)) if a else 0.0

        return {
            "links": float(usable),
            "active_links": float(active),
            "events_per_min": mean(rates) * (len(rates) if rates else 0.0),
            "event_rate_s": human_rate + veh_rate,
            "human_event_rate_s": human_rate,
            "vehicle_event_rate_s": veh_rate,
            "amp_mean_db": mean(amps),
            "amp_p90_db": p90(amps),
            "dur_mean_s": mean(durs),
            "iet_mean_s": mean(iets),
            "sigma_db": mean(sigmas),
            # Little's law: N = (events/s / gain) * dwell time
            "people_if": human_rate * dwell_walk / max(s.people_gain, 1e-6),
            "vehicles_if": veh_rate * dwell_veh / max(s.vehicle_gain, 1e-6),
        }


def _empty_stats() -> Dict[str, float]:
    keys = ("links", "active_links", "events_per_min", "event_rate_s",
            "human_event_rate_s", "vehicle_event_rate_s", "amp_mean_db",
            "amp_p90_db", "dur_mean_s", "iet_mean_s", "sigma_db",
            "people_if", "vehicles_if")
    return {k: 0.0 for k in keys}


IF_FEATURE_NAMES = [
    "if links", "if active links", "if events/min", "if human events/s",
    "if vehicle events/s", "if amplitude dB", "if amplitude p90 dB",
    "if event duration s", "if inter-event s", "if link sigma dB",
    "if people (Little)", "if vehicles (Little)",
]


def if_feature_vector(st: Dict[str, float]) -> List[float]:
    return [
        st["links"], st["active_links"], st["events_per_min"],
        st["human_event_rate_s"], st["vehicle_event_rate_s"],
        st["amp_mean_db"], st["amp_p90_db"], st["dur_mean_s"],
        st["iet_mean_s"], st["sigma_db"],
        st["people_if"], st["vehicles_if"],
    ]
