"""
E8 — What does the sensing radius actually buy you?

The app lets the user pick the radius of the region it reports on ("how many people
are within R metres?"). This module measures what that choice does to accuracy, so
the picker in the UI can show *measured* numbers instead of a guess.

Three questions:

Q1  Scale.   The device-free (Wi-Fi) channel sees a fixed volume of space set by the
             access-point geometry, not by the radius the user picked. If the user
             asks for 5 m, the RF fluctuation caused by a crowd 40 m away is still in
             the signal. How must the RF prior be rescaled as R changes?
             -> e8a_rf_scale sweeps the multiplier per radius.

Q2  Accuracy. For each radius, how wrong is the count before and after the user has
             labelled the scene, and how much of the truth can the radios see at all?
             -> e8b_table.

Q3  Coverage. What fraction of the people genuinely inside R does BLE actually
             detect, and what fraction of what it counts really is inside R?
             -> e8b_table (coverage / gate_precision) and e8d_envelope.

Truth is always "targets whose distance to the observer is <= R at this instant",
i.e. exactly the question the user asked.
"""

from __future__ import annotations

import json
import os
import time
from typing import Dict, List, Sequence

import numpy as np

from experiments import run_pipeline, many_runs, eval_counts, RESULTS_DIR
from algos import Settings, regression_metrics

# Radii offered in the UI. 5 m is a room, 60 m is a small square; 90 m is the
# simulator's hard BLE horizon (advertisements below -97 dBm are never heard).
RADII: Sequence[float] = (5.0, 10.0, 15.0, 20.0, 30.0, 45.0, 60.0, 90.0)
REFERENCE_RADIUS_M = 25.0

# The upgraded configuration that ships, as validated by final_suite.py.
def upgraded(static_weight: float = 0.4, factor: float = 1.0) -> Settings:
    return Settings(
        adaptive_gate=True, min_track_samples=3, use_bic=True,
        speed_sigma_k=2.0, exclude_infrastructure=True,
        static_speed_mps=0.6, pedestrian_max_mps=1.3, vehicle_min_mps=1.6,
        device_to_person_factor=factor, rf_person_coeff=3.0, dip_person_coeff=0.3,
        vehicle_dip_coeff=0.0, rules_device_weight=0.75, rules_rf_weight=0.25,
        forgetting=0.985, static_entity_weight=static_weight,
    )


def radius_config(r: float, rf_scale: float = 1.0, vehicle_radius: float | None = None) -> Settings:
    s = upgraded()
    s.proximity_radius_m = float(r)
    s.rf_area_scale = float(rf_scale)
    s.vehicle_radius_m = float(r if vehicle_radius is None else vehicle_radius)
    return s


# --------------------------------------------------------------- Q1: RF scaling

def e8a_rf_scale(radii: Sequence[float] = RADII,
                 alphas: Sequence[float] = (0.15, 0.3, 0.5, 0.75, 1.0, 1.5, 2.5, 4.0),
                 n_scenes: int = 6, duration: float = 300.0,
                 mode: str = "both") -> Dict:
    """
    Rules-only MAE (no labels yet) as a function of the RF multiplier, per radius.
    The best multiplier tells us how to rescale the device-free prior when the user
    changes R.
    """
    out: Dict[str, Dict] = {}
    for r in radii:
        per_alpha = {}
        for a in alphas:
            runs = many_runs(n_scenes, settings=radius_config(r, a), variant="pass",
                             duration=duration, mode=mode, wifi_interval=30.0,
                             truth_radius=r)
            maes, biases = [], []
            for j, run in enumerate(runs):
                m = eval_counts(run["rows"], k_labels=0, seed=7 + j)
                maes.append(m["people"]["mae"])
                biases.append(m["people"]["bias"])
            per_alpha["%g" % a] = dict(mae=float(np.mean(maes)),
                                       bias=float(np.mean(biases)))
        best_a = min(per_alpha, key=lambda k: per_alpha[k]["mae"])
        out["R%g" % r] = dict(per_alpha=per_alpha, best_alpha=float(best_a),
                              best_mae=float(per_alpha[best_a]["mae"]))
        print("   R=%-4g  best alpha=%-5s  MAE %.2f" % (r, best_a, per_alpha[best_a]["mae"]),
              flush=True)
    return out


def fit_rf_law(scale_result: Dict) -> Dict:
    """
    Fit log(alpha) = p * log(R / R_ref). Returns the exponent and the predicted
    multiplier for every radius, so the app can compute it in closed form.
    """
    xs, ys = [], []
    for key, rec in scale_result.items():
        r = float(key[1:])
        a = rec["best_alpha"]
        if a <= 0:
            continue
        xs.append(np.log(r / REFERENCE_RADIUS_M))
        ys.append(np.log(a))
    xs, ys = np.asarray(xs), np.asarray(ys)
    # least squares through the origin: log(alpha) = p * x
    p = float(np.dot(xs, ys) / max(np.dot(xs, xs), 1e-9))
    resid = ys - p * xs
    return dict(exponent=p,
                rms_log_resid=float(np.sqrt(np.mean(resid ** 2))),
                reference_radius_m=REFERENCE_RADIUS_M,
                measured={k: v["best_alpha"] for k, v in scale_result.items()},
                fitted={k: float(np.exp(p * np.log(float(k[1:]) / REFERENCE_RADIUS_M)))
                        for k in scale_result})


# ------------------------------------------------------------- Q2/Q3: the table

def _one_radius(r: float, rf_scale: float, n_scenes: int, duration: float,
                ks: Sequence[int] = (0, 10, 25, 50)) -> Dict:
    cfg = radius_config(r, rf_scale)
    runs = [run_pipeline(seed=i, duration=duration, settings=cfg, variant="pass",
                         mode="both", wifi_interval=30.0, truth_radius=r,
                         collect_gate=True, collect_range=True)
            for i in range(n_scenes)]

    truth_p = float(np.mean([row["tp"] for run in runs for row in run["rows"]]))
    truth_v = float(np.mean([row["tv"] for run in runs for row in run["rows"]]))

    # coverage: of the people genuinely inside R, how many did BLE actually see?
    # (an entity only counts as a seen person if the device belongs to a person)
    seen = float(np.sum([row["near_person_inside"] for run in runs for row in run["rows"]]))
    tot = float(np.sum([row["tp"] for run in runs for row in run["rows"]]))
    coverage = seen / max(tot, 1e-9)

    counted = float(np.sum([row["near_counted"] for run in runs for row in run["rows"]]))
    counted_in = float(np.sum([row["near_inside"] for run in runs for row in run["rows"]]))
    counted_person = float(np.sum([row["near_person"] for run in runs for row in run["rows"]]))
    gate_precision = counted_in / max(counted, 1e-9)
    person_share = counted_person / max(counted, 1e-9)
    rel_err = [e for run in runs for e in run["gate"]["range_err"]]

    res: Dict[str, float] = dict(
        radius_m=float(r), rf_area_scale=float(rf_scale),
        truth_people_mean=truth_p, truth_vehicles_mean=truth_v,
        coverage_people=float(coverage),
        gate_precision=float(gate_precision),
        person_share=float(person_share),
        range_rel_err_mean=float(np.mean(rel_err)) if rel_err else 0.0,
        range_rel_err_p90=float(np.percentile(rel_err, 90)) if rel_err else 0.0,
    )

    for k in ks:
        mp, mr, mb, mw, mv = [], [], [], [], []
        for j, run in enumerate(runs):
            m = eval_counts(run["rows"], k_labels=k, seed=7 + j, forgetting=cfg.forgetting)
            mp.append(m["people"]["mae"])
            mr.append(m["people"]["rmse"])
            mb.append(m["people"]["bias"])
            mw.append(m["people"]["within1"])
            mv.append(m["vehicles_rules_only"]["mae"])
        res["mae_k%d" % k] = float(np.mean(mp))
        res["rmse_k%d" % k] = float(np.mean(mr))
        res["bias_k%d" % k] = float(np.mean(mb))
        res["within1_k%d" % k] = float(np.mean(mw))
        res["veh_mae_k%d" % k] = float(np.mean(mv))

    # per-mode breakdown at 25 labels: which channel carries the radius?
    for mode in ("ble", "wifi"):
        cfg_m = radius_config(r, rf_scale)
        runs_m = [run_pipeline(seed=i, duration=duration, settings=cfg_m, variant="pass",
                               mode=mode, wifi_interval=30.0, truth_radius=r)
                  for i in range(n_scenes)]
        maes = []
        for j, run in enumerate(runs_m):
            m = eval_counts(run["rows"], k_labels=25, seed=11 + j, forgetting=cfg.forgetting)
            maes.append(m["people"]["mae"])
        res["mae_%s_k25" % mode] = float(np.mean(maes))

    # vehicle detection: of the cars that pass within R, how many did we catch?
    det = tot_v = 0
    false_alarms = tot_nv = 0
    for run in runs:
        for pt in run["per_target"]:
            if pt["truth"] == "vehicle" and pt["offset"] <= r:
                tot_v += 1
                det += 1 if pt["pred"] == "vehicle" else 0
            if pt["truth"] in ("pedestrian", "static") and pt["offset"] <= r:
                tot_nv += 1
                false_alarms += 1 if pt["pred"] == "vehicle" else 0
    res["vehicle_recall"] = float(det / max(tot_v, 1))
    res["vehicle_false_alarm"] = float(false_alarms / max(tot_nv, 1))
    res["vehicle_n"] = int(tot_v)
    return res


def e8b_table(radii: Sequence[float] = RADII, rf_scale=None, n_scenes: int = 10,
              duration: float = 300.0, law: Dict | None = None) -> List[Dict]:
    """The numbers the UI shows: accuracy for every radius the user can pick."""
    rows = []
    for r in radii:
        if rf_scale is not None:
            a = rf_scale
        elif law is not None:
            a = float(np.exp(law["exponent"] * np.log(r / law["reference_radius_m"])))
        else:
            a = 1.0
        rec = _one_radius(r, a, n_scenes, duration)
        rec["rf_area_scale"] = float(a)
        rows.append(rec)
        print("   R=%-4g a=%.2f truth=%.2f mae0=%.2f mae25=%.2f cov=%.2f person=%.2f "
              "precin=%.2f vrec=%.2f"
              % (r, a, rec["truth_people_mean"], rec["mae_k0"], rec["mae_k25"],
                 rec["coverage_people"], rec["person_share"], rec["gate_precision"],
                 rec["vehicle_recall"]),
              flush=True)
    return rows


# --------------------------------------------------- Q3b: cumulative envelope

def e8d_envelope(radii: Sequence[float] = RADII, n_scenes: int = 12,
                 duration: float = 300.0, env: str = "urban") -> List[Dict]:
    """
    Cumulative detection envelope: for targets that pass at offset <= R, the
    fraction the app tracks at all and the fraction it classifies correctly.
    """
    from experiments import make_scene
    runs = many_runs(n_scenes, settings=upgraded(), variant="pass", duration=duration,
                     collect_range=True, wifi_interval=30.0, env=env)
    out = []
    for r in radii:
        row = dict(radius_m=float(r))
        for cls in ("pedestrian", "vehicle"):
            tot = tracked = correct = 0
            for run in runs:
                for pt in run["per_target"]:
                    if pt["truth"] != cls or pt["offset"] > r:
                        continue
                    tot += 1
                    if pt["pred"] != "missed":
                        tracked += 1
                    if pt["pred"] == cls:
                        correct += 1
            row[cls + "_n"] = tot
            row[cls + "_tracked"] = float(tracked / max(tot, 1))
            row[cls + "_correct"] = float(correct / max(tot, 1))
        out.append(row)
        print("   R=%-4g ped tracked %.2f correct %.2f | veh tracked %.2f correct %.2f"
              % (r, row["pedestrian_tracked"], row["pedestrian_correct"],
                 row["vehicle_tracked"], row["vehicle_correct"]), flush=True)
    return out


# ----------------------------------------------------------------------- driver

def main(n_scenes: int = 10, quick: bool = False) -> Dict:
    t0 = time.time()
    radii = RADII
    n_scale = 4 if quick else 6

    print("[1/3] RF prior scaling (rules-only MAE vs RF multiplier)")
    scale = e8a_rf_scale(radii, n_scenes=n_scale)
    law = fit_rf_law(scale)
    print("      law: alpha(R) = (R/%.0f)^%.3f   (rms log residual %.3f)"
          % (law["reference_radius_m"], law["exponent"], law["rms_log_resid"]))

    print("[2/3] per-radius accuracy table")
    table = e8b_table(radii, law=law, n_scenes=n_scenes)

    print("[3/3] cumulative detection envelope")
    env = e8d_envelope(radii, n_scenes=12)

    res = dict(rf_scale_sweep=scale, rf_law=law, table=table, envelope=env,
               runtime_s=time.time() - t0)
    path = os.path.join(RESULTS_DIR, "radius.json")
    with open(path, "w") as fh:
        json.dump(res, fh, indent=2)
    print("wrote %s in %.0f s" % (path, time.time() - t0))
    return res


if __name__ == "__main__":
    import sys
    quick = "--quick" in sys.argv
    main(n_scenes=4 if quick else 10, quick=quick)


# ------------------------------------ Q4: switching radius without relabelling

def e8c_radius_switch(pairs=(("15", "45"), ("45", "15"), ("10", "60"), ("60", "10")),
                      law: Dict | None = None, q: float = 0.48, n_scenes: int = 10,
                      duration: float = 300.0, k: int = 25) -> List[Dict]:
    """
    The user changes the radius. Does the calibration they already did still work?

    The online model learns a *correction* in absolute headcount, so a model fitted
    at 15 m is in the wrong units for a 60 m region. If we divide the training
    residual by s(R) = (R/25)^q and multiply the prediction by the same law at the
    current radius, the model becomes (approximately) radius-invariant and old
    labels keep working. This measures how much that buys.
    """
    from algos import RLSModel, PEOPLE_FEATURES, regression_metrics

    def alpha(r):
        if law is None:
            return 1.0
        return float(np.exp(law["exponent"] * np.log(r / law["reference_radius_m"])))

    def scl(r, on):
        return float((r / REFERENCE_RADIUS_M) ** q) if on else 1.0

    out = []
    for ra, rb in pairs:
        ra, rb = float(ra), float(rb)
        row = dict(train_radius_m=ra, test_radius_m=rb)
        for tag, scaled in (("unscaled", False), ("rescaled", True)):
            maes = []
            for seed in range(n_scenes):
                run_a = run_pipeline(seed=seed, duration=duration,
                                     settings=radius_config(ra, alpha(ra)), variant="pass",
                                     mode="both", wifi_interval=30.0, truth_radius=ra)
                run_b = run_pipeline(seed=seed, duration=duration,
                                     settings=radius_config(rb, alpha(rb)), variant="pass",
                                     mode="both", wifi_interval=30.0, truth_radius=rb)
                model = RLSModel(len(PEOPLE_FEATURES), forgetting=0.985)
                rng = np.random.default_rng(7 + seed)
                rows_a = run_a["rows"]
                idxs = sorted(rng.choice(int(len(rows_a) * 0.6), size=k,
                                         replace=False).tolist())
                for i in idxs:
                    r = rows_a[i]
                    model.update(r["feat"], (r["tp"] - r["rules_p"]) / scl(ra, scaled))
                rows_b = run_b["rows"]
                cut = int(len(rows_b) * 0.6)
                pred, truth = [], []
                for r in rows_b[cut:]:
                    corr = model.predict(r["feat"]) * model.trust() * scl(rb, scaled)
                    pred.append(max(0.0, r["rules_p"] + corr))
                    truth.append(r["tp"])
                maes.append(regression_metrics(truth, pred)["mae"])
            row[tag] = float(np.mean(maes))

        # controls: no model at all, and a model trained at the *right* radius
        for tag, train_r in (("rules_only", None), ("same_radius", rb)):
            maes = []
            for seed in range(n_scenes):
                run_b = run_pipeline(seed=seed, duration=duration,
                                     settings=radius_config(rb, alpha(rb)), variant="pass",
                                     mode="both", wifi_interval=30.0, truth_radius=rb)
                model = RLSModel(len(PEOPLE_FEATURES), forgetting=0.985)
                if train_r is not None:
                    run_a = run_pipeline(seed=seed, duration=duration,
                                         settings=radius_config(train_r, alpha(train_r)),
                                         variant="pass", mode="both", wifi_interval=30.0,
                                         truth_radius=train_r)
                    rng = np.random.default_rng(7 + seed)
                    rows_a = run_a["rows"]
                    for i in sorted(rng.choice(int(len(rows_a) * 0.6), size=k,
                                               replace=False).tolist()):
                        r = rows_a[i]
                        model.update(r["feat"], r["tp"] - r["rules_p"])
                rows_b = run_b["rows"]
                cut = int(len(rows_b) * 0.6)
                pred, truth = [], []
                for r in rows_b[cut:]:
                    corr = model.predict(r["feat"]) * model.trust()
                    pred.append(max(0.0, r["rules_p"] + corr))
                    truth.append(r["tp"])
                maes.append(regression_metrics(truth, pred)["mae"])
            row[tag] = float(np.mean(maes))
        out.append(row)
        print("   train %-4g -> test %-4g : unscaled %.2f | rescaled %.2f | "
              "rules %.2f | same-radius %.2f"
              % (ra, rb, row["unscaled"], row["rescaled"], row["rules_only"],
                 row["same_radius"]), flush=True)
    return out
