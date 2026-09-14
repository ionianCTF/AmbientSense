"""
Physically grounded RF/acoustic scene simulator used to validate Ambient Sense.

The point of this module is to produce measurements that are *wrong in the same way
real measurements are wrong*: bodies block links, moving scatterers ripple the field,
phones rotate their MAC, and Android throttles Wi-Fi scans. Every model below is a
standard textbook model with its constants documented, so the numbers the experiments
report can be traced back to physics rather than to the simulator's mood.

Models
------
1. Log-distance path loss (Rappaport):
       PL(d) = PL(d0) + 10 n log10(d/d0) + X_sh,   X_sh ~ N(0, sigma_sh^2)
   with PL(1 m) = 40.0 dB for 2.4 GHz free space.

2. Body shadowing (LOS blocking). A body crossing the line of sight adds
       B = A * exp(-(x_perp / w)^2),  w = first Fresnel radius + body radius
   where the first Fresnel radius of a link of length d with the body at distances
   d1/d2 from the endpoints is  r_F = sqrt(lambda * d1 * d2 / d).
   Peak depths: adult torso ~6-10 dB at 2.4 GHz, a car ~12-20 dB.

3. Moving scatterer / bistatic radar equation (the device-free sensing mechanism):
       P_scatter / P_direct = sigma * d^2 / (4 pi d_tx^2 d_rx^2)
   i.e. the ripple a person induces on a link falls off as 1/(d_tx * d_rx). With
   sigma ~ 1 m^2 for a person at 2.4 GHz this yields ~1-3 dB ripples for somebody a
   few metres from the link, which is exactly what is observed in practice. The
   scattered phasor rotates at the bistatic Doppler rate, so faster objects produce
   faster fluctuations (and shorter events).

4. BLE advertisement stream: exponential ad intervals, missed detections, per-device
   TX power (only sometimes advertised), extra body loss for pocketed phones,
   rotating randomised MACs, and a background of *static* beacons/laptops/displays
   that are the dominant source of counting error in the real world.

5. Acoustic: individual sources add energetically,
       L = 10 log10( sum_i 10^(L_i/10) ),  L_i = L_src - 20 log10(d_i)
   cars are far louder than pedestrians, which is why the acoustic channel is
   informative for vehicles.
"""

from __future__ import annotations

import math
from dataclasses import dataclass, field
from typing import Dict, List, Optional, Sequence, Tuple

import numpy as np

LAMBDA_2G = 3e8 / 2.4e9          # 0.125 m
PL0_2G = 40.0                     # free-space path loss at 1 m, 2.4 GHz
SPEED_OF_SOUND_CORRECTION = 0.0   # placeholder, unused

# n = path-loss exponent, sigma_sh = shadowing std (dB), n_ap = access points in range,
# n_static = background BLE beacons/laptops, sigma_meas = RSSI reporting noise (dB).
# Urban is a street (partial line of sight); "dense" is a built-up canyon.
ENVIRONMENTS = {
    "urban":    dict(n=2.55, sigma_sh=5.5, n_ap=14, n_static=22, sigma_meas=2.0),
    "indoor":   dict(n=2.60, sigma_sh=5.0, n_ap=9,  n_static=14, sigma_meas=1.8),
    "open":     dict(n=2.05, sigma_sh=4.0, n_ap=5,  n_static=4,  sigma_meas=2.2),
    "dense":    dict(n=3.10, sigma_sh=7.5, n_ap=20, n_static=35, sigma_meas=2.0),
}


# --------------------------------------------------------------------------- data

@dataclass
class Target:
    """A person or vehicle passing the observer on a straight line."""
    cls: str                 # 'person' | 'vehicle'
    speed: float             # m/s
    offset: float            # perpendicular distance at closest approach (m)
    t_ca: float              # time of closest approach (s)
    rcs: float               # radar cross-section, m^2 (2.4 GHz)
    block_db: float          # peak LOS blocking depth, dB
    src_db: float            # acoustic source level at 1 m, dB(A)
    devices: List["BleDev"] = field(default_factory=list)

    def radius(self, t: float) -> float:
        dt = t - self.t_ca
        return math.sqrt(self.offset ** 2 + (self.speed * dt) ** 2)

    def position(self, t: float) -> Tuple[float, float]:
        """2-D position; the closest approach happens at (offset, 0)."""
        return (self.offset, self.speed * (t - self.t_ca))


@dataclass
class BleDev:
    key: str
    target: Optional[Target]      # None => static clutter
    pos: Tuple[float, float]      # static position for clutter
    tx_ref: float                 # RSSI at 1 m, dBm
    advertises_tx: bool
    ad_interval: float            # mean seconds between adverts
    body_loss_db: float           # extra loss (pocket, orientation, in-car)
    next_ad: float = 0.0
    randomized: bool = False
    rotate_at: float = math.inf
    static: bool = False


@dataclass
class AccessPoint:
    bssid: str
    pos: Tuple[float, float]
    tx_dbm: float
    wall_db: float                # extra loss from walls to the observer
    channel: int


@dataclass
class BleObservation:
    t: float
    key: str
    rssi: float
    tx: Optional[int]
    static: bool
    # Motion ground truth of whatever carries the device:
    #  'pedestrian' | 'vehicle' | 'static' (parked car, standing person, building clutter)
    truth_cls: Optional[str] = "static"
    target_id: int = -1
    # What carries the device: 'person' | 'vehicle' | 'clutter' (a beacon, laptop,
    # meter or parked-car sensor that has nothing to do with the crowd).
    truth_kind: str = "clutter"
    # True observer->device distance at the moment of the advert (m). Only used by
    # the validation harness: it lets us score the "is it inside the radius?" gate.
    true_dist: float = -1.0


# -------------------------------------------------------------------------- scene

class Scene:
    """One simulated measurement session."""

    def __init__(
        self,
        seed: int,
        env: str = "urban",
        duration: float = 300.0,
        n_pedestrians: int = 6,
        n_vehicles: int = 3,
        scene_radius: float = 70.0,
        wifi_scan_interval: float = 30.0,
        ble_detect_prob: float = 0.75,
        enable_noise: bool = True,
    ):
        self.rng = np.random.default_rng(seed)
        self.env = ENVIRONMENTS[env]
        self.env_name = env
        self.duration = duration
        self.scene_radius = scene_radius
        self.wifi_scan_interval = wifi_scan_interval
        self.ble_detect_prob = ble_detect_prob
        self.enable_noise = enable_noise

        self.aps: List[AccessPoint] = self._make_aps()
        self.targets: List[Target] = self._make_targets(n_pedestrians, n_vehicles)
        self.devices: List[BleDev] = self._make_devices()

        # acoustic / RF background
        self.base_spl = float(self.rng.normal(45, 4))        # quiet street, dB(A)
        self.base_spl = max(self.base_spl, 32.0)

        # per-link static multipath (constant complex term, gives a stable fading floor)
        self.ap_static_fading = {
            ap.bssid: self.rng.normal(0.0, 2.0) for ap in self.aps
        }
        self._ble_fast_fading = {}

    # ------------------------------------------------------------------ setup

    def _make_aps(self) -> List[AccessPoint]:
        rng, n = self.rng, self.env["n_ap"]
        aps = []
        for i in range(n):
            d = float(np.clip(rng.gamma(2.0, 12.0), 4.0, self.scene_radius))
            th = rng.uniform(0, 2 * math.pi)
            pos = (d * math.cos(th), d * math.sin(th))
            # APs further away are usually behind more walls
            wall = float(np.clip(rng.normal(3.0 + 0.35 * d, 6.0), 0.0, 45.0))
            aps.append(
                AccessPoint(
                    bssid="%02x:%02x:%02x:aa:%02x:%02x" % (
                        rng.integers(0, 255), rng.integers(0, 255), rng.integers(0, 255),
                        rng.integers(0, 255), i & 0xFF),
                    pos=pos,
                    tx_dbm=float(rng.choice([20.0, 20.0, 17.0, 14.0])),
                    wall_db=wall,
                    channel=int(rng.choice([1, 6, 11, 36, 44, 149])),
                )
            )
        return aps

    def _make_targets(self, n_ped: int, n_veh: int) -> List[Target]:
        rng = self.rng
        out: List[Target] = []
        for _ in range(n_ped):
            out.append(Target(
                cls="person",
                speed=float(np.clip(rng.normal(1.35, 0.3), 0.6, 2.4)),
                offset=float(rng.uniform(0.5, 25.0)),
                t_ca=float(rng.uniform(-40.0, self.duration + 40.0)),
                rcs=float(rng.uniform(0.5, 1.5)),
                block_db=float(rng.uniform(5.0, 10.0)),
                src_db=float(rng.uniform(48, 62)),      # talking / footsteps
            ))
        for _ in range(n_veh):
            out.append(Target(
                cls="vehicle",
                speed=float(np.clip(rng.normal(12.0, 4.0), 4.0, 28.0)),
                offset=float(rng.uniform(3.0, 30.0)),
                t_ca=float(rng.uniform(-20.0, self.duration + 20.0)),
                rcs=float(rng.uniform(10.0, 40.0)),
                block_db=float(rng.uniform(12.0, 22.0)),
                src_db=float(rng.uniform(72, 88)),      # engine + tyres
            ))
        return out

    def _make_devices(self) -> List[BleDev]:
        rng = self.rng
        devs: List[BleDev] = []
        counter = [0]

        def new_key(randomized: bool):
            counter[0] += 1
            flag = (rng.integers(0, 16) * 16) | 0x02 if randomized else rng.integers(0, 16) * 16
            return "%02x:%02x:%02x:%02x:%02x:%02x" % (
                flag, rng.integers(0, 255), rng.integers(0, 255),
                rng.integers(0, 255), rng.integers(0, 255), counter[0] & 0xFF)

        # devices carried by moving targets
        self._target_index = {}
        for idx, tgt in enumerate(self.targets):
            self._target_index[id(tgt)] = idx
        for tgt in self.targets:
            if tgt.cls == "person":
                n_dev = (1 if rng.random() < 0.78 else 0) + (1 if rng.random() < 0.28 else 0) \
                        + (1 if rng.random() < 0.15 else 0)
            else:
                n_dev = int(rng.integers(1, 4))         # head unit + phone(s) + TPMS
            for k in range(n_dev):
                randomized = rng.random() < 0.6
                devs.append(BleDev(
                    key=new_key(randomized),
                    target=tgt,
                    pos=(0.0, 0.0),
                    tx_ref=float(rng.normal(-59.0, 4.0)),
                    advertises_tx=bool(rng.random() < 0.6),
                    ad_interval=float(np.clip(rng.normal(0.5, 0.25), 0.1, 1.5)),
                    body_loss_db=float(rng.uniform(2.0, 10.0)) if k == 0 else
                                 float(rng.uniform(1.0, 6.0)),
                    randomized=randomized,
                    rotate_at=float(rng.uniform(200.0, 900.0)) if randomized else math.inf,
                    next_ad=float(rng.uniform(0.0, 3.0)),
                ))

        # static clutter: beacons, laptops, TVs, meters, parked-car sensors
        for _ in range(self.env["n_static"]):
            d = float(np.clip(abs(rng.normal(18.0, 14.0)), 1.0, self.scene_radius))
            th = rng.uniform(0, 2 * math.pi)
            devs.append(BleDev(
                key=new_key(rng.random() < 0.3),
                target=None,
                pos=(d * math.cos(th), d * math.sin(th)),
                tx_ref=float(rng.normal(-62.0, 6.0)),
                advertises_tx=bool(rng.random() < 0.4),
                ad_interval=float(np.clip(rng.normal(0.8, 0.5), 0.2, 3.0)),
                body_loss_db=float(rng.uniform(0.0, 6.0)),   # walls, furniture
                static=True,
                next_ad=float(rng.uniform(0.0, 3.0)),
            ))
        return devs

    @staticmethod
    def _motion_class(target: Optional["Target"]) -> str:
        """Motion ground truth for a device: what a perfect classifier would say."""
        if target is None:
            return "static"                      # building clutter
        if target.speed <= 0.3:
            return "static"                      # parked car / standing person
        return "pedestrian" if target.cls == "person" else "vehicle"

    # -------------------------------------------------------------- rf physics

    def _path_loss(self, d: float) -> float:
        return PL0_2G + 10.0 * self.env["n"] * math.log10(max(d, 1.0))

    def _link_field(self, tx: Tuple[float, float], t: float) -> complex:
        """Complex field of one link: direct + scattered contributions.

        `tx` is the position of the transmitter (an access point, or a BLE device
        acting as one end of a link). The receiver is the phone, at the origin.
        """
        d = math.hypot(tx[0], tx[1])
        # scattered phasors from every target
        scattered = 0j
        for tgt in self.targets:
            r = tgt.radius(t)
            if r > self.scene_radius:
                continue
            px, py = tgt.position(t)
            d_rx = math.hypot(px, py)                                  # target -> phone
            d_tx = math.hypot(px - tx[0], py - tx[1])                  # TX -> target
            if d_rx < 0.5 or d_tx < 0.5:
                continue
            # bistatic radar equation, relative to the direct path
            rho = tgt.rcs * d * d / (4.0 * math.pi * d_tx * d_tx * d_rx * d_rx)
            a = math.sqrt(max(rho, 0.0))
            phase = 2.0 * math.pi * (d_tx + d_rx) / LAMBDA_2G
            scattered += a * complex(math.cos(phase), math.sin(phase))
        return 1.0 + scattered

    def _blocking_loss(self, tx: Tuple[float, float], t: float) -> float:
        """Extra loss from targets occluding the line of sight (dB)."""
        ax, ay = tx
        d = math.hypot(ax, ay)
        total = 0.0
        for tgt in self.targets:
            px, py = tgt.position(t)
            # perpendicular distance from the target to the AP->phone segment
            vx, vy = -ax, -ay                    # phone is at the origin
            wx, wy = px - ax, py - ay
            seg_len2 = vx * vx + vy * vy
            if seg_len2 < 1e-9:
                continue
            s = (wx * vx + wy * vy) / seg_len2
            if s < 0.0 or s > 1.0:               # not between the endpoints
                continue
            cx, cy = ax + s * vx, ay + s * vy
            x_perp = math.hypot(px - cx, py - cy)
            d1 = s * d
            d2 = (1.0 - s) * d
            r_f = math.sqrt(LAMBDA_2G * max(d1, 0.1) * max(d2, 0.1) / max(d, 0.1))
            w = r_f + 0.3                        # Fresnel zone + body radius
            total += tgt.block_db * math.exp(-(x_perp / w) ** 2)
        return total

    def wifi_rssi(self, ap: AccessPoint, t: float) -> float:
        d = math.hypot(ap.pos[0], ap.pos[1])
        pl = self._path_loss(d) + ap.wall_db + self.ap_static_fading[ap.bssid]
        field = self._link_field(ap.pos, t)
        ripple = 20.0 * math.log10(max(abs(field), 1e-3))
        blocking = self._blocking_loss(ap.pos, t)
        noise = self.rng.normal(0.0, self.env["sigma_meas"])
        rssi = ap.tx_dbm - pl + ripple - blocking + noise
        return float(np.clip(rssi, -100.0, -20.0))

    def wifi_scan(self, t: float) -> Dict[str, float]:
        """One full scan result table, as Android would hand it to us."""
        out = {}
        for ap in self.aps:
            rssi = self.wifi_rssi(ap, t)
            if rssi > -95.0:                      # below sensitivity it disappears
                out[ap.bssid] = rssi
        return out

    # ------------------------------------------------------------------- ble

    def _dev_position(self, dev: BleDev, t: float) -> Tuple[float, float]:
        if dev.target is None:
            return dev.pos
        return dev.target.position(t)

    def ble_observations(self, t: float, dt: float) -> List[BleObservation]:
        """Advertisements heard in the window (t-dt, t]."""
        out: List[BleObservation] = []
        for dev in self.devices:
            if t < dev.next_ad:
                continue
            dev.next_ad = t + self.rng.exponential(dev.ad_interval)
            if self.rng.random() > self.ble_detect_prob:
                continue
            px, py = self._dev_position(dev, t)
            d = math.hypot(px, py)
            if d > 90.0:
                continue
            # dev.tx_ref is the RSSI at 1 m, so only the loss *beyond* 1 m applies
            pl = (self._path_loss(d) - PL0_2G) + dev.body_loss_db
            # fast fading: spatially correlated, de-correlated as the device moves
            key_state = self._ble_fast_fading.get(dev.key)
            if key_state is None or abs(key_state[0] - d) > 0.4:
                fade = float(self.rng.normal(0.0, 4.0))
                self._ble_fast_fading[dev.key] = (d, fade)
            else:
                fade = key_state[1] * 0.7 + float(self.rng.normal(0.0, 3.0)) * 0.3
                self._ble_fast_fading[dev.key] = (d, fade)
            # Device-free channel: a *static* emitter and the phone form a link, and
            # every body or vehicle that crosses that link deforms it. This is the
            # effect Iannizzotto et al. (Appl. Sci. 2025) and the Fresnel-zone model
            # exploit, and it is what makes BLE adverts useful as a sensor and not
            # just as a tag. Moving emitters are skipped: their own body loss already
            # dominates, and the app only ever turns stationary devices into links.
            ripple = 0.0
            blocking = 0.0
            if dev.target is None:
                field = self._link_field((px, py), t)
                ripple = 20.0 * math.log10(max(abs(field), 1e-3))
                blocking = self._blocking_loss((px, py), t)
            rssi = dev.tx_ref - pl + fade + ripple - blocking
            if rssi < -97.0:
                continue
            out.append(BleObservation(
                t=t,
                key=dev.key,
                rssi=float(rssi),
                tx=int(round(dev.tx_ref)) if dev.advertises_tx else None,
                static=dev.static,
                truth_cls=self._motion_class(dev.target),
                target_id=self._target_index.get(id(dev.target), -1),
                truth_kind=("clutter" if dev.target is None else dev.target.cls),
                true_dist=float(d),
            ))
        # MAC rotation
        for dev in self.devices:
            if dev.randomized and t > dev.rotate_at:
                dev.key = "%02x:%02x:%02x:%02x:%02x:%02x" % (
                    (self.rng.integers(0, 16) * 16) | 0x02, self.rng.integers(0, 255),
                    self.rng.integers(0, 255), self.rng.integers(0, 255),
                    self.rng.integers(0, 255), self.rng.integers(0, 255))
                dev.rotate_at = t + float(self.rng.uniform(200.0, 900.0))
                self._ble_fast_fading.pop(dev.key, None)
        return out

    # -------------------------------------------------------------- acoustics

    def spl(self, t: float) -> float:
        if not self.enable_noise:
            return self.base_spl
        energy = 10.0 ** (self.base_spl / 10.0)
        for tgt in self.targets:
            r = max(tgt.radius(t), 1.0)
            if r > 120.0:
                continue
            li = tgt.src_db - 20.0 * math.log10(r) - 0.02 * r   # small air absorption
            energy += 10.0 ** (li / 10.0)
        return float(10.0 * math.log10(energy) + self.rng.normal(0.0, 1.2))

    # ------------------------------------------------------------ truth

    def truth(self, t: float, radius: float = 30.0) -> Tuple[int, int]:
        people = sum(1 for tg in self.targets
                     if tg.cls == "person" and tg.radius(t) <= radius)
        vehicles = sum(1 for tg in self.targets
                       if tg.cls == "vehicle" and tg.radius(t) <= radius)
        return people, vehicles

    def truth_by_radius(self, t: float, radii: Sequence[float]) -> Dict[float, Tuple[int, int]]:
        return {r: self.truth(t, r) for r in radii}


# ---------------------------------------------------------------- session driver

def run_session(
    scene: Scene,
    dt: float = 1.0,
    wifi_scan_interval: Optional[float] = None,
) -> Dict[str, list]:
    """
    Drive a scene forward and return the two observation streams plus ground truth,
    sampled on the app's 1 Hz fusion tick.
    """
    wifi_interval = wifi_scan_interval if wifi_scan_interval is not None \
        else scene.wifi_scan_interval
    wifi_scans: List[Tuple[float, Dict[str, float]]] = []
    ble: List[BleObservation] = []
    truth: List[Tuple[float, int, int, float]] = []

    next_wifi = 0.0
    t = 0.0
    while t <= scene.duration:
        if t >= next_wifi - 1e-9:
            wifi_scans.append((t, scene.wifi_scan(t)))
            next_wifi = t + wifi_interval
        ble.extend(scene.ble_observations(t, dt))
        p, v = scene.truth(t)
        truth.append((t, p, v, scene.spl(t)))
        t += dt

    return {
        "wifi_scans": wifi_scans,
        "ble": ble,
        "truth": truth,
        "scene": scene,
    }
