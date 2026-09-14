"""
Definitive experiment run: shipping configuration vs the upgraded one.

Upgrades under test
-------------------
U1  straight-line *pass* model for range-rate (handles tangential motion)
U2  BIC model selection + speed-significance gate (kills spurious motion)
U3  adaptive gate for very short tracks (vehicles are visible for ~3 s)
U4  infrastructure filter (a laptop that has not moved for 2 min is not a person)
U5  people term excludes vehicles
U6  retuned constants (thresholds, device->person factor, RF weight, no dip term
    for vehicles)
"""

from __future__ import annotations

import json
import os
import time

import numpy as np

from experiments import (run_pipeline, many_runs, eval_counts, e5_range,
                         e6_scan_interval, e1_classification, e2b_cross_scene,
                         e3_ablation, e0_ranging, RESULTS_DIR)
from algos import (Settings, RLSModel, regression_metrics, confusion, prf,
                   PEOPLE_FEATURES)


def shipping() -> Settings:
    """What v0.9 does today."""
    return Settings(
        adaptive_gate=False, min_track_samples=4, use_bic=False,
        speed_sigma_k=0.0, exclude_infrastructure=False,
        static_speed_mps=0.45, pedestrian_max_mps=2.4, vehicle_min_mps=3.0,
        device_to_person_factor=1.15, rf_person_coeff=6.0, dip_person_coeff=0.55,
        vehicle_dip_coeff=0.25, rules_device_weight=0.55, rules_rf_weight=0.45,
        forgetting=0.985, static_entity_weight=1.0,
    )


def upgraded(static_weight: float = 0.7, factor: float = 0.8) -> Settings:
    return Settings(
        adaptive_gate=True, min_track_samples=3, use_bic=True,
        speed_sigma_k=2.0, exclude_infrastructure=True,
        static_speed_mps=0.6, pedestrian_max_mps=1.3, vehicle_min_mps=1.6,
        device_to_person_factor=factor, rf_person_coeff=3.0, dip_person_coeff=0.3,
        vehicle_dip_coeff=0.0, rules_device_weight=0.75, rules_rf_weight=0.25,
        forgetting=0.985, static_entity_weight=static_weight,
    )


def sweep_static_weight(n: int = 6):
    """People estimate = factor * (walking + static_weight * standing)."""
    out = []
    for sw in (1.0, 0.7, 0.4):
        for f in (0.6, 0.8, 1.0):
            ep, tp = [], []
            for seed in range(n):
                r = run_pipeline(seed, duration=300, n_ped=10, n_veh=5,
                                 variant="pass", settings=upgraded(sw, f),
                                 mode="both")
                for row in r["rows"]:
                    ep.append(abs(row["rules_p"] - row["tp"]))
                    tp.append(row["tp"])
            out.append(dict(static_weight=sw, factor=f, mae=float(np.mean(ep)),
                            truth_mean=float(np.mean(tp))))
    return out


def counting(cur: Settings, variant: str, mode: str, n: int = 10):
    runs = many_runs(n, settings=cur, variant=variant, duration=300.0, mode=mode,
                     wifi_interval=30.0)
    res = {}
    for k in (0, 10, 25, 40):
        mp, mv, mr, mw = [], [], [], []
        for j, r in enumerate(runs):
            m = eval_counts(r["rows"], k_labels=k, seed=7 + j,
                            forgetting=cur.forgetting)
            mp.append(m["people"]["mae"])
            mv.append(m["vehicles_rules_only"]["mae"])
            mr.append(m["people"]["rmse"])
            mw.append(m["people"]["within1"])
        res[f"k{k}"] = dict(people_mae=float(np.mean(mp)),
                            people_rmse=float(np.mean(mr)),
                            vehicle_mae=float(np.mean(mv)),
                            within1=float(np.mean(mw)))
    return res


def classification(cur: Settings, variant: str, n: int = 8):
    runs = many_runs(n, settings=cur, variant=variant, duration=300.0)
    recs = [o for r in runs for o in r["outcomes"] if o.get("dist") is not None]
    sc = prf(confusion([r["truth"] for r in recs], [r["pred"] for r in recs]))
    return {
        c: {k: float(v) for k, v in sc[c].items()}
        for c in ("static", "pedestrian", "vehicle", "accuracy")
    }


def main():
    t0 = time.time()
    res: dict = {}

    print("[1/8] static-weight sweep", flush=True)
    res["sweep_static_weight"] = sweep_static_weight(6)
    best = min(res["sweep_static_weight"], key=lambda r: r["mae"])
    sw, fa = best["static_weight"], best["factor"]
    print("      best: static_weight=%.2f factor=%.2f (MAE %.2f)" % (sw, fa, best["mae"]))
    up = upgraded(sw, fa)
    ship = shipping()

    print("[2/8] ranging", flush=True)
    res["e0_ranging"] = e0_ranging(8)

    print("[3/8] classification", flush=True)
    res["e1_classification"] = {
        "shipping_ema": classification(ship, "ema"),
        "kalman": classification(ship, "kalman"),
        "upgraded_pass": classification(up, "pass"),
    }

    print("[4/8] counting, in-scene", flush=True)
    res["e2_counting"] = {
        "shipping_ble": counting(ship, "ema", "ble"),
        "shipping_both": counting(ship, "ema", "both"),
        "upgraded_ble": counting(up, "pass", "ble"),
        "upgraded_both": counting(up, "pass", "both"),
    }

    print("[5/8] cross-scene generalisation", flush=True)
    res["e2b_cross_scene"] = e2b_cross_scene(8)

    print("[6/8] ablation", flush=True)
    res["e3_ablation"] = e3_ablation(n_scenes=8, k=25)

    print("[7/8] range envelope", flush=True)
    res["e5_range"] = e5_range(n_scenes=14)
    res["e5_counting_radius"] = {}
    from experiments import e5_counting_vs_radius
    res["e5_counting_radius"] = e5_counting_vs_radius(n_scenes=6)

    print("[8/8] scan interval", flush=True)
    res["e6_scan_interval"] = e6_scan_interval(n_scenes=6)

    res["config"] = dict(
        shipping=ship.__dict__, upgraded=up.__dict__,
        best_static_weight=sw, best_factor=fa,
    )

    path = os.path.join(RESULTS_DIR, "results.json")
    with open(path, "w") as fh:
        json.dump(res, fh, indent=2, default=str)
    print("wrote %s in %.0fs" % (path, time.time() - t0))


if __name__ == "__main__":
    main()
