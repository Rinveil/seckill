#!/usr/bin/env python3
"""Controlled concurrency load test for POST /api/seckill/{activityId}.

Scenarios:
  prepare  — create activity + register users + write tokens file
  warm     — warm-up with subset of tokens
  burst    — each user grabs once (stock-sized unique users)
  hammer   — hammer grab endpoint for duration with shared token pool (will hit DUPLICATE)

Metrics written to /tmp/seckill-load-*.txt and JSON summary.
"""
from __future__ import annotations

import argparse
import json
import os
import statistics
import sys
import threading
import time
import urllib.error
import urllib.request
from collections import Counter
from concurrent.futures import ThreadPoolExecutor, as_completed
from datetime import datetime, timedelta, timezone
from typing import Any

BASE = os.environ.get("SECKILL_BASE", "http://localhost:30080")
API = f"{BASE}/api"


def now_iso(hours: float = 0) -> str:
    return (datetime.now(timezone.utc) + timedelta(hours=hours)).strftime("%Y-%m-%dT%H:%M:%SZ")


def http_json(method: str, path: str, body: dict | None = None, token: str | None = None, timeout: float = 20):
    url = path if path.startswith("http") else f"{API}{path}"
    data = None
    headers = {"Accept": "application/json"}
    if body is not None:
        data = json.dumps(body).encode()
        headers["Content-Type"] = "application/json"
    if token:
        headers["Authorization"] = f"Bearer {token}"
    req = urllib.request.Request(url, data=data, headers=headers, method=method)
    t0 = time.perf_counter()
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            raw = resp.read().decode()
            latency = (time.perf_counter() - t0) * 1000
            payload = json.loads(raw) if raw else {}
            return resp.status, payload, latency
    except urllib.error.HTTPError as e:
        latency = (time.perf_counter() - t0) * 1000
        raw = e.read().decode()
        try:
            payload = json.loads(raw) if raw else {}
        except json.JSONDecodeError:
            payload = {"message": raw}
        return e.code, payload, latency
    except Exception as e:
        latency = (time.perf_counter() - t0) * 1000
        return 0, {"error": str(e)}, latency


def login(username: str, password: str) -> str:
    code, body, _ = http_json("POST", "/user/login", {"username": username, "password": password})
    if code != 200 or body.get("code") != 0:
        raise RuntimeError(f"login failed {username}: {body}")
    return body["data"]["token"]


def register_or_login(username: str, password: str = "pass1234") -> str:
    last_err = None
    for attempt in range(5):
        try:
            code, body, _ = http_json(
                "POST",
                "/user/register",
                {"username": username, "password": password, "nickname": username},
            )
            if code == 200 and body.get("code") == 0 and isinstance(body.get("data"), dict) and body["data"].get("token"):
                return body["data"]["token"]
            # username exists (1005) or other → login
            if body.get("code") in (0, 1005, None) or code == 200:
                return login(username, password)
            # transient
            time.sleep(0.2 * (attempt + 1))
        except Exception as e:
            last_err = e
            time.sleep(0.3 * (attempt + 1))
    # final login attempt
    try:
        return login(username, password)
    except Exception as e:
        raise RuntimeError(f"register_or_login failed for {username}: {last_err or e}") from e


def percentile(sorted_vals: list[float], p: float) -> float:
    if not sorted_vals:
        return 0.0
    k = (len(sorted_vals) - 1) * p / 100.0
    f = int(k)
    c = min(f + 1, len(sorted_vals) - 1)
    if f == c:
        return sorted_vals[f]
    return sorted_vals[f] + (sorted_vals[c] - sorted_vals[f]) * (k - f)


def summarize(name: str, results: list[dict[str, Any]], duration: float, extra: dict | None = None) -> dict:
    codes = Counter(r.get("biz") for r in results)
    http_ok = sum(1 for r in results if r.get("http") == 200)
    success = codes.get(0, 0)
    latencies = sorted(r["latency_ms"] for r in results if "latency_ms" in r)
    total = len(results)
    qps = total / duration if duration > 0 else 0
    tps = success / duration if duration > 0 else 0
    summary = {
        "scenario": name,
        "duration_sec": round(duration, 3),
        "total_requests": total,
        "http_200": http_ok,
        "biz_success_code0": success,
        "biz_sold_out_1001": codes.get(1001, 0),
        "biz_duplicate_1004": codes.get(1004, 0),
        "biz_not_started_1003": codes.get(1003, 0),
        "biz_other": {str(k): v for k, v in codes.items() if k not in (0, 1001, 1004, 1003)},
        "http_qps": round(qps, 2),
        "business_tps": round(tps, 2),
        "success_rate": round(success / total, 4) if total else 0,
        "latency_ms": {
            "p50": round(percentile(latencies, 50), 2),
            "p95": round(percentile(latencies, 95), 2),
            "p99": round(percentile(latencies, 99), 2),
            "avg": round(statistics.mean(latencies), 2) if latencies else 0,
            "max": round(max(latencies), 2) if latencies else 0,
        },
    }
    if extra:
        summary.update(extra)
    return summary


def cmd_prepare(args: argparse.Namespace) -> None:
    admin = login("admin", "admin123")
    create = {
        "title": f"load-{args.stock}-{int(time.time())}",
        "priceFen": 990,
        "originPriceFen": 1990,
        "stock": args.stock,
        "startAt": now_iso(-0.1),
        "endAt": now_iso(3),
    }
    code, body, _ = http_json("POST", "/activity", create, token=admin)
    if body.get("code") != 0:
        raise SystemExit(f"create activity failed: {body}")
    act_id = body["data"]["id"]
    http_json("POST", f"/activity/{act_id}/preheat", token=admin)
    http_json("POST", f"/activity/{act_id}/open", token=admin)

    n_users = args.users
    tokens: list[str] = []
    prefix = args.prefix
    print(f"registering {n_users} users prefix={prefix} ...")
    with ThreadPoolExecutor(max_workers=min(32, n_users)) as ex:
        futs = {
            ex.submit(register_or_login, f"{prefix}{i}"): i for i in range(n_users)
        }
        for fut in as_completed(futs):
            tokens.append(fut.result())
            if len(tokens) % 50 == 0:
                print(f"  tokens {len(tokens)}/{n_users}")

    meta = {"activityId": act_id, "stock": args.stock, "users": n_users, "tokens": tokens, "adminToken": admin}
    path = args.out
    with open(path, "w") as f:
        json.dump(meta, f)
    print(f"prepared activityId={act_id} stock={args.stock} users={n_users} -> {path}")


def grab_once(activity_id: int, token: str) -> dict:
    http_code, body, latency = http_json("POST", f"/seckill/{activity_id}", token=token, timeout=30)
    return {
        "http": http_code,
        "biz": body.get("code") if isinstance(body, dict) else None,
        "message": body.get("message") if isinstance(body, dict) else str(body),
        "latency_ms": latency,
        "error": body.get("error") if isinstance(body, dict) else None,
    }


def cmd_burst(args: argparse.Namespace) -> None:
    with open(args.meta) as f:
        meta = json.load(f)
    act_id = meta["activityId"]
    tokens = meta["tokens"][: args.n]
    workers = args.concurrency
    print(f"burst: activity={act_id} requests={len(tokens)} concurrency={workers}")
    results: list[dict] = []
    t0 = time.perf_counter()
    with ThreadPoolExecutor(max_workers=workers) as ex:
        futs = [ex.submit(grab_once, act_id, tok) for tok in tokens]
        for fut in as_completed(futs):
            results.append(fut.result())
    duration = time.perf_counter() - t0
    summary = summarize(
        "burst",
        results,
        duration,
        {"concurrency": workers, "unique_users": len(tokens), "activityId": act_id},
    )
    # reconcile
    time.sleep(2)
    code, body, _ = http_json("GET", f"/activity/{act_id}/reconcile", token=meta["adminToken"])
    summary["reconcile"] = body.get("data") if isinstance(body, dict) else body
    out_path = f"/tmp/seckill-load-burst-{int(time.time())}.txt"
    text = json.dumps(summary, ensure_ascii=False, indent=2)
    with open(out_path, "w") as f:
        f.write(text)
        f.write("\n")
    print(text)
    print(f"wrote {out_path}")
    with open("/tmp/seckill-load-latest-burst.json", "w") as f:
        json.dump(summary, f, ensure_ascii=False, indent=2)


def cmd_hammer(args: argparse.Namespace) -> None:
    with open(args.meta) as f:
        meta = json.load(f)
    act_id = meta["activityId"]
    tokens = meta["tokens"]
    if not tokens:
        raise SystemExit("no tokens")
    stop_at = time.perf_counter() + args.duration
    results: list[dict] = []
    lock = threading.Lock()
    idx = {"i": 0}

    def worker():
        while time.perf_counter() < stop_at:
            with lock:
                i = idx["i"]
                idx["i"] = i + 1
            tok = tokens[i % len(tokens)]
            r = grab_once(act_id, tok)
            with lock:
                results.append(r)

    print(f"hammer: activity={act_id} concurrency={args.concurrency} duration={args.duration}s tokens={len(tokens)}")
    t0 = time.perf_counter()
    threads = [threading.Thread(target=worker, daemon=True) for _ in range(args.concurrency)]
    for t in threads:
        t.start()
    for t in threads:
        t.join()
    duration = time.perf_counter() - t0
    summary = summarize(
        "hammer",
        results,
        duration,
        {"concurrency": args.concurrency, "token_pool": len(tokens), "activityId": act_id},
    )
    time.sleep(2)
    code, body, _ = http_json("GET", f"/activity/{act_id}/reconcile", token=meta["adminToken"])
    summary["reconcile"] = body.get("data") if isinstance(body, dict) else body
    out_path = f"/tmp/seckill-load-hammer-{int(time.time())}.txt"
    text = json.dumps(summary, ensure_ascii=False, indent=2)
    with open(out_path, "w") as f:
        f.write(text)
        f.write("\n")
    print(text)
    print(f"wrote {out_path}")
    with open("/tmp/seckill-load-latest-hammer.json", "w") as f:
        json.dump(summary, f, ensure_ascii=False, indent=2)


def cmd_warm(args: argparse.Namespace) -> None:
    with open(args.meta) as f:
        meta = json.load(f)
    act_id = meta["activityId"]
    tokens = meta["tokens"][: args.n]
    workers = args.concurrency
    results: list[dict] = []
    t0 = time.perf_counter()
    with ThreadPoolExecutor(max_workers=workers) as ex:
        futs = [ex.submit(grab_once, act_id, tok) for tok in tokens]
        for fut in as_completed(futs):
            results.append(fut.result())
    duration = time.perf_counter() - t0
    summary = summarize("warm", results, duration, {"concurrency": workers, "activityId": act_id})
    out_path = f"/tmp/seckill-load-warm-{int(time.time())}.txt"
    with open(out_path, "w") as f:
        f.write(json.dumps(summary, ensure_ascii=False, indent=2))
        f.write("\n")
    print(json.dumps(summary, ensure_ascii=False, indent=2))
    print(f"wrote {out_path}")
    with open("/tmp/seckill-load-latest-warm.json", "w") as f:
        json.dump(summary, f, ensure_ascii=False, indent=2)


def main() -> None:
    p = argparse.ArgumentParser()
    sub = p.add_subparsers(dest="cmd", required=True)

    sp = sub.add_parser("prepare")
    sp.add_argument("--stock", type=int, default=200)
    sp.add_argument("--users", type=int, default=250)
    sp.add_argument("--prefix", default=f"ld{int(time.time())}_")
    sp.add_argument("--out", default="/tmp/seckill-load-meta.json")
    sp.set_defaults(func=cmd_prepare)

    sw = sub.add_parser("warm")
    sw.add_argument("--meta", default="/tmp/seckill-load-meta.json")
    sw.add_argument("--n", type=int, default=50)
    sw.add_argument("--concurrency", type=int, default=50)
    sw.set_defaults(func=cmd_warm)

    sb = sub.add_parser("burst")
    sb.add_argument("--meta", default="/tmp/seckill-load-meta.json")
    sb.add_argument("--n", type=int, default=200)
    sb.add_argument("--concurrency", type=int, default=100)
    sb.set_defaults(func=cmd_burst)

    sh = sub.add_parser("hammer")
    sh.add_argument("--meta", default="/tmp/seckill-load-meta.json")
    sh.add_argument("--concurrency", type=int, default=150)
    sh.add_argument("--duration", type=float, default=15)
    sh.set_defaults(func=cmd_hammer)

    args = p.parse_args()
    args.func(args)


if __name__ == "__main__":
    main()
