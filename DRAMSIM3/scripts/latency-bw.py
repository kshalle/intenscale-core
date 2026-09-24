#!/usr/bin/env python3

"""
Generate latency-vs-bandwidth curves for a DRAMsim3 memory configuration.

For the given config, this sweeps the average request interarrival time
across a range of target utilizations (of a single channel's theoretical
peak command-bus bandwidth, unless --channels says otherwise), for both
random and sequential (streaming) address traffic, at three read/write
mixes (100% read, 2:1 read:write, 1:1 read:write).

Each (traffic type, r/w mix, utilization) combination gets its own
synthetic trace of --num-requests requests (via trace_gen.Generator),
where each request's interarrival time is drawn uniformly from
[1, roundup(2 * target_interarrival)] -- the same per-transaction
arrival-gate logic used by the reference axi_txn_arrival_gate testbench,
just applied here in DRAM (tCK) cycles instead of AXI clock cycles. That
run is simulated once with dramsim3main, and the achieved bandwidth /
average read latency (submission-to-reply; writes have no comparable
"reply" in this model and are excluded from the latency figure even
under mixed read/write traffic, though they still count toward
bandwidth) is read back from the resulting stats JSON.

Output layout:
    latency-bw-curves/<config_name>/
        traces/                              generated .trace/.ini/.json/.log
        <config_name>_random_latency_bw.png
        <config_name>_sequential_latency_bw.png
        results.csv
"""

import argparse
import configparser
import csv
import json
import os
import random
import subprocess
import sys

import parse_config
import trace_gen

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
REPO_ROOT = os.path.dirname(SCRIPT_DIR)
SIMULATOR_NAME = "DRAMSim3"

# (label, fraction of requests that are reads, description)
RW_MIXES = [
    ("100pct_read", 1.0, "100% read"),
    ("2to1_rw", 2.0 / 3.0, "2:1 read:write"),
    ("1to1_rw", 0.5, "1:1 read:write"),
]

# (label used in file/graph names, stream_type passed to trace_gen.Generator)
TRAFFIC_TYPES = [
    ("random", "random", "Random"),
    ("sequential", "stream", "Sequential"),
]

# Row buffer policy is switched per traffic type: sequential/streaming
# access benefits from row-buffer locality (open-page keeps the row open
# across the burst), while random access gets no such locality (closed-page
# avoids paying for a row that's unlikely to be reused).
ROW_BUF_POLICY_FOR_TRAFFIC = {
    "random": "CLOSE_PAGE",
    "sequential": "OPEN_PAGE",
}


def resolve_config_path(name):
    """Accept a bare config name, a name with .ini, or a path; look in
    the current directory and then in configs/."""
    candidates = [name] if name.endswith(".ini") else [name, name + ".ini"]
    for c in candidates:
        if os.path.isfile(c):
            return os.path.abspath(c)
    for c in candidates:
        p = os.path.join(REPO_ROOT, "configs", c)
        if os.path.isfile(p):
            return os.path.abspath(p)
    raise SystemExit("Could not find config '%s' (looked in '.' and 'configs/')" % name)


def burst_cycle_for(bl, protocol):
    """Mirror Config::InitDRAMStructure's burst_cycle calculation
    (src/configuration.cc) so we can compute the command-bus peak rate
    without needing a simulation run."""
    protocol = protocol.upper()
    if protocol == "GDDR5":
        bc = bl / 4.0
    elif protocol == "GDDR5X":
        bc = bl / 8.0
    elif protocol == "GDDR6":
        bc = bl / 16.0
    else:
        bc = bl / 2.0
    if protocol == "LPDDR5":
        bc = bl / 8.0
    return bc


def get_config_params(config_path):
    bl = parse_config.get_val_from_file(config_path, "dram_structure", "BL")
    protocol = parse_config.get_protocol(config_path)
    bus_width = parse_config.get_val_from_file(config_path, "system", "bus_width")
    tck = parse_config.get_val_from_file(config_path, "timing", "tCK")
    return {
        "bl": bl,
        "protocol": protocol,
        "bus_width": bus_width,
        "tck": tck,
        "native_request_size_bytes": bus_width // 8 * bl,
    }


def burst_length_for_cacheline(cacheline_bytes, bus_width, protocol):
    """Derive the BL that makes one DRAM transaction exactly cacheline_bytes
    for this config's bus width, so every request the simulator actually
    executes is one cacheline (not just an accounting assumption)."""
    bus_width_bytes = bus_width // 8
    if cacheline_bytes % bus_width_bytes != 0:
        raise SystemExit(
            "--cacheline-bytes (%d) must be a multiple of the config's bus "
            "width in bytes (%d)" % (cacheline_bytes, bus_width_bytes))
    bl = cacheline_bytes // bus_width_bytes
    if bl <= 0 or (bl & (bl - 1)) != 0:
        raise SystemExit(
            "--cacheline-bytes (%d) implies BL=%d for this config's bus width "
            "(%d bytes), which isn't a power of two" % (cacheline_bytes, bl, bus_width_bytes))
    if protocol.upper() == "LPDDR5" and bl not in (16, 32):
        raise SystemExit(
            "--cacheline-bytes (%d) implies BL=%d, but LPDDR5 requires BL=16 or "
            "32 -- pick a cacheline size of %d or %d bytes for this config" %
            (cacheline_bytes, bl, 16 * bus_width_bytes, 32 * bus_width_bytes))
    return bl


def compute_utilization_targets(peak_interarrival_cycles, util_min, util_max, num_points):
    if num_points == 1:
        percentages = [util_max]
    else:
        step = (util_max - util_min) / (num_points - 1)
        percentages = [util_min + i * step for i in range(num_points)]
    # kept as a float -- the per-request randomization (trace_gen.Generator,
    # random_interarrival mode) draws from [1, roundup(2 * this)] itself
    interarrivals = [peak_interarrival_cycles / (u / 100.0) for u in percentages]
    return percentages, interarrivals


def make_run_config(base_config_path, run_id, traces_dir, effective_bl, row_buf_policy):
    """Write a copy of the base config into traces_dir with output_prefix
    and output_level overridden so this run's stats land in their own
    file without the epoch/txt clutter, BL overridden so every
    transaction the simulator executes is exactly one cacheline, and
    row_buf_policy overridden per traffic type (open-page for sequential,
    closed-page for random -- see ROW_BUF_POLICY_FOR_TRAFFIC)."""
    cfg = configparser.ConfigParser()
    cfg.read(base_config_path)
    if not cfg.has_section("other"):
        cfg.add_section("other")
    cfg.set("other", "output_prefix", run_id)
    cfg.set("other", "output_level", "0")
    cfg.set("dram_structure", "BL", str(effective_bl))
    cfg.set("system", "row_buf_policy", row_buf_policy)
    run_config_path = os.path.join(traces_dir, run_id + ".ini")
    with open(run_config_path, "w") as f:
        cfg.write(f)
    return run_config_path


def generate_trace(trace_path, stream_type, target_interarrival, read_fraction,
                    gb, num_requests, seed):
    random.seed(seed)
    gen = trace_gen.Generator(stream_type, target_interarrival, read_fraction,
                              gb, random_interarrival=True)
    last_clk = 0
    with open(trace_path, "w") as f:
        for _ in range(num_requests):
            op, addr, clk = gen.gen()
            f.write(trace_gen.get_string(op, addr, clk, "dramsim3", target_interarrival))
            last_clk = clk
    return last_clk


def run_simulation(executable, run_config_path, traces_dir, trace_path, num_cycles,
                   run_id, verbose):
    cmd = [executable, run_config_path, "-c", str(num_cycles),
           "-o", traces_dir, "-t", trace_path]
    if verbose:
        print("EXECUTING:", " ".join(cmd))
        subprocess.run(cmd, check=True)
    else:
        log_path = os.path.join(traces_dir, run_id + ".log")
        with open(log_path, "w") as log_f:
            subprocess.run(cmd, check=True, stdout=log_f, stderr=subprocess.STDOUT)


def compute_metrics(json_path, tck, cacheline_bytes, num_cycles):
    """Bandwidth counts all completed reads+writes (total bytes moved).
    Latency only counts reads: DRAMsim3 records read_latency as
    clk_at_reply - clk_at_submission (src/controller.cc), i.e. exactly
    submission-to-reply. write_latency measures something else (there's
    no "reply" for a write in this model), so it isn't a read-comparable
    latency and is intentionally excluded here, even for mixed traffic.

    Bandwidth is divided by num_cycles, the actual number of cycles the
    simulation ran for (trace span + drain), not the trace's nominal
    span alone -- otherwise the fixed drain window is silently dropped
    from the denominator and bandwidth is overstated (most noticeably
    at small --num-requests, where the drain window isn't negligible
    relative to the trace span)."""
    with open(json_path) as f:
        data = json.load(f)

    completed = 0
    read_lat_sum = 0
    read_lat_count = 0
    for chan in data.values():
        completed += chan.get("num_reads_done", 0) + chan.get("num_writes_done", 0)
        hist = chan.get("read_latency") or {}
        for latency_str, count in hist.items():
            read_lat_sum += int(latency_str) * count
            read_lat_count += count

    if num_cycles > 0:
        achieved_bw_gbps = completed * cacheline_bytes / (num_cycles * tck)
    else:
        achieved_bw_gbps = 0.0
    avg_read_latency_cycles = (read_lat_sum / read_lat_count) if read_lat_count else 0.0
    avg_read_latency_ns = avg_read_latency_cycles * tck

    return achieved_bw_gbps, avg_read_latency_ns, avg_read_latency_cycles, completed


def plot_curve(base_dir, config_name, traffic_label, traffic_desc, curves, latency_unit,
              peak_bandwidth_gbps):
    out_path = os.path.join(base_dir, "%s_%s_latency_bw.png" % (config_name, traffic_label))
    fig, ax = plt.subplots(figsize=(8, 6))
    for rw_label, _read_fraction, rw_desc in RW_MIXES:
        points = curves[rw_label]
        ordered = sorted(points, key=lambda p: p["achieved_bandwidth_gbps"])
        bws = [p["achieved_bandwidth_gbps"] for p in ordered]
        lats = [p["achieved_read_latency_" + latency_unit] for p in ordered]
        ax.plot(bws, lats, marker="o", label=rw_desc)

    ax.set_xlabel("Achieved Bandwidth (GB/s)")
    ax.set_ylabel("Average Read Latency (%s)" % ("ns" if latency_unit == "ns" else "cycles"))
    ax.set_title("[%s] %s — %s traffic" % (SIMULATOR_NAME, config_name, traffic_desc))
    ax.legend()
    ax.grid(True, alpha=0.3)

    # Always mark the theoretical peak bandwidth, and make sure the axis
    # extends at least peak+5 GB/s so the gap to it (if any) is visible.
    ax.set_xlim(left=0, right=max(ax.get_xlim()[1], peak_bandwidth_gbps + 5))
    ax.axvline(x=peak_bandwidth_gbps, color="red", linestyle="--", linewidth=1.5)
    ax.annotate("Maximum: %.3g GB/s" % peak_bandwidth_gbps,
               xy=(peak_bandwidth_gbps, 1), xycoords=("data", "axes fraction"),
               xytext=(-4, -4), textcoords="offset points",
               rotation=90, va="top", ha="right", color="red", fontsize=9)

    fig.tight_layout()
    fig.savefig(out_path, dpi=150)
    plt.close(fig)
    return out_path


def main():
    parser = argparse.ArgumentParser(
        description="Sweep interarrival time to generate latency-BW curves "
        "for a DRAMsim3 config, for random/sequential traffic at three "
        "read/write mixes.",
        formatter_class=argparse.ArgumentDefaultsHelpFormatter)
    parser.add_argument("config", help="config name (looked up in configs/) or path to a .ini file")
    parser.add_argument("--executable", default=os.path.join(REPO_ROOT, "build", "dramsim3main"),
                        help="path to the dramsim3main executable")
    parser.add_argument("-n", "--num-requests", type=int, default=5000,
                        help="number of requests to generate and simulate per point")
    parser.add_argument("--num-points", type=int, default=15,
                        help="number of utilization points to sweep")
    parser.add_argument("--util-min", type=float, default=10.0,
                        help="lowest target utilization, in percent")
    parser.add_argument("--util-max", type=float, default=100.0,
                        help="highest target utilization, in percent")
    parser.add_argument("--channels", type=int, default=1,
                        help="number of channels being benchmarked; used only to "
                        "compute the peak bandwidth that utilization targets are "
                        "relative to (independent of the config's own channel count)")
    parser.add_argument("--gb", type=int, default=4,
                        help="GBs of address space to generate addresses over")
    parser.add_argument("--cacheline-bytes", type=int, default=64,
                        help="size of one request in bytes; BL is derived (and "
                        "overridden in each run's config) so the simulator "
                        "actually transfers exactly this many bytes per request")
    parser.add_argument("--drain-cycles", type=int, default=5000,
                        help="extra simulated cycles after the trace's nominal span, "
                        "to let in-flight requests finish before stats are collected")
    parser.add_argument("--output-root", default=os.path.join(REPO_ROOT, "latency-bw-curves"),
                        help="root directory to write per-config output folders into")
    parser.add_argument("--seed", type=int, default=42, help="base RNG seed")
    parser.add_argument("--latency-unit", choices=["ns", "cycles"], default="ns",
                        help="unit for the latency axis of the graphs")
    parser.add_argument("-v", "--verbose", action="store_true",
                        help="stream simulator stdout instead of logging it to a file")
    args = parser.parse_args()

    if not os.path.isfile(args.executable):
        raise SystemExit("Executable not found: %s (build dramsim3main first)" % args.executable)

    config_path = resolve_config_path(args.config)
    config_name = os.path.basename(config_path)[:-4]
    params = get_config_params(config_path)

    effective_bl = burst_length_for_cacheline(args.cacheline_bytes, params["bus_width"],
                                              params["protocol"])
    bc = burst_cycle_for(effective_bl, params["protocol"])
    peak_interarrival_cycles = bc / args.channels
    peak_bandwidth_gbps = args.cacheline_bytes / (peak_interarrival_cycles * params["tck"])

    percentages, interarrivals = compute_utilization_targets(
        peak_interarrival_cycles, args.util_min, args.util_max, args.num_points)

    print("Config: %s (protocol=%s, bus_width=%d, tCK=%.4gns)" %
          (config_name, params["protocol"], params["bus_width"], params["tck"]))
    print("Cacheline: %d bytes -> BL=%d (config's native BL=%d, native request "
          "size=%d bytes; BL is overridden per-run to match --cacheline-bytes); "
          "benchmarked channels: %d" %
          (args.cacheline_bytes, effective_bl, params["bl"],
           params["native_request_size_bytes"], args.channels))
    print("Peak interarrival @100%% util: %.3g cycles (%.3g GB/s)" %
          (peak_interarrival_cycles, peak_bandwidth_gbps))

    base_dir = os.path.join(args.output_root, config_name)
    traces_dir = os.path.join(base_dir, "traces")
    os.makedirs(traces_dir, exist_ok=True)

    total_points = len(TRAFFIC_TYPES) * len(RW_MIXES) * len(percentages)
    print("Total points: %d (%d requests simulated per point)" %
          (total_points, args.num_requests))

    results = []
    point_index = 0
    for traffic_label, stream_type, traffic_desc in TRAFFIC_TYPES:
        row_buf_policy = ROW_BUF_POLICY_FOR_TRAFFIC[traffic_label]
        curves = {}
        for rw_label, read_fraction, rw_desc in RW_MIXES:
            points = []
            for util_pct, target_ia in zip(percentages, interarrivals):
                point_index += 1
                run_id = "%s_%s_util%05.1f" % (traffic_label, rw_label, util_pct)
                randint_max = trace_gen.roundup(2 * target_ia) if target_ia > 1 else 1
                print("[%d/%d] %s: target util=%.1f%% interarrival=%.3g cycles "
                      "(drawn from [1, %d])" %
                      (point_index, total_points, run_id, util_pct, target_ia, randint_max))

                trace_path = os.path.join(traces_dir, run_id + ".trace")
                last_clk = generate_trace(trace_path, stream_type, target_ia,
                                          read_fraction, args.gb, args.num_requests,
                                          args.seed + point_index)

                run_config_path = make_run_config(config_path, run_id, traces_dir, effective_bl,
                                                  row_buf_policy)
                num_cycles = int(last_clk) + args.drain_cycles
                run_simulation(args.executable, run_config_path, traces_dir,
                               trace_path, num_cycles, run_id, args.verbose)

                json_path = os.path.join(traces_dir, run_id + ".json")
                achieved_bw, read_lat_ns, read_lat_cycles, completed = compute_metrics(
                    json_path, params["tck"], args.cacheline_bytes, num_cycles)

                row = {
                    "config": config_name,
                    "traffic_type": traffic_label,
                    "row_buf_policy": row_buf_policy,
                    "rw_mix": rw_label,
                    "target_utilization_pct": round(util_pct, 3),
                    "target_interarrival_cycles": round(target_ia, 3),
                    "trace_span_cycles": last_clk,
                    "num_cycles_simulated": num_cycles,
                    "num_requests": args.num_requests,
                    "completed_requests": completed,
                    "achieved_bandwidth_gbps": achieved_bw,
                    "achieved_utilization_pct": round(achieved_bw / peak_bandwidth_gbps * 100.0, 3),
                    "achieved_read_latency_ns": read_lat_ns,
                    "achieved_read_latency_cycles": read_lat_cycles,
                }
                print("    -> achieved bandwidth=%.3g GB/s, achieved read latency=%.3g ns" %
                      (row["achieved_bandwidth_gbps"], row["achieved_read_latency_ns"]))
                results.append(row)
                points.append(row)
            curves[rw_label] = points

        out_path = plot_curve(base_dir, config_name, traffic_label, traffic_desc,
                              curves, args.latency_unit, peak_bandwidth_gbps)
        print("Wrote graph:", out_path)

    csv_path = os.path.join(base_dir, "results.csv")
    with open(csv_path, "w", newline="") as f:
        writer = csv.DictWriter(f, fieldnames=list(results[0].keys()))
        writer.writeheader()
        writer.writerows(results)
    print("Wrote results:", csv_path)


if __name__ == "__main__":
    try:
        import matplotlib
        matplotlib.use("Agg")
        import matplotlib.pyplot as plt
    except ImportError:
        sys.exit("matplotlib is required to generate graphs: pip install matplotlib")
    main()
