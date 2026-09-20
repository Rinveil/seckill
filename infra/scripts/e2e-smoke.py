#!/usr/bin/env python3
"""End-to-end functional smoke against http://localhost:30080/api."""
from __future__ import annotations

import json
import sys
import time
import urllib.error
import urllib.request
from datetime import datetime, timedelta, timezone
from typing import Any

BASE = "http://localhost:30080"
API = f"{BASE}/api"
RESULTS: list[dict[str, Any]] = []


def now_iso(offset_hours: float = 0) -> str:
    return (datetime.now(timezone.utc) + timedelta(hours=offset_hours)).strftime("%Y-%m-%dT%H:%M:%SZ")


def req(
    method: str,
    path: str,
    body: dict | None = None,
    token: str | None = None,
    timeout: float = 30,
) -> tuple[int, dict | str]:
    url = path if path.startswith("http") else f"{API}{path}"
    data = None
    headers = {"Accept": "application/json"}
    if body is not None:
        data = json.dumps(body).encode()
        headers["Content-Type"] = "application/json"
    if token:
        headers["Authorization"] = f"Bearer {token}"
    r = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(r, timeout=timeout) as resp:
            raw = resp.read().decode()
            try:
                return resp.status, json.loads(raw) if raw else {}
            except json.JSONDecodeError:
                return resp.status, raw
    except urllib.error.HTTPError as e:
        raw = e.read().decode()
        try:
            return e.code, json.loads(raw) if raw else {"message": str(e)}
        except json.JSONDecodeError:
            return e.code, raw
    except Exception as e:
        return 0, {"error": str(e)}


def record(step: str, expect: str, ok: bool, actual: str) -> None:
    RESULTS.append(
        {
            "step": step,
            "expect": expect,
            "actual": actual,
            "result": "PASS" if ok else "FAIL",
        }
    )
    flag = "PASS" if ok else "FAIL"
    print(f"[{flag}] {step}: {actual}")


def unwrap(payload: dict | str) -> dict:
    if isinstance(payload, dict):
        return payload
    return {"raw": payload}


def main() -> int:
    # 1 Health
    try:
        code, body = req("GET", f"{BASE}/", timeout=10)
        # homepage may be HTML
        ok = code == 200
        record("1.Health 前端首页", "HTTP 200", ok, f"HTTP {code}")
    except Exception as e:
        record("1.Health 前端首页", "HTTP 200", False, str(e))

    code, body = req("GET", "/activity/list")
    # may be 401 without token — still reachable
    ok = code in (200, 401, 403) or (isinstance(body, dict) and "code" in body)
    record("1.Health /api 可达", "网关返回 HTTP/业务包", ok, f"HTTP {code} body={str(body)[:120]}")

    # 2 Login admin
    code, body = req("POST", "/user/login", {"username": "admin", "password": "admin123"})
    data = unwrap(body)
    admin_token = (data.get("data") or {}).get("token") if isinstance(data.get("data"), dict) else None
    ok = code == 200 and data.get("code") == 0 and bool(admin_token)
    record("2.管理员登录", "code=0 且返回 token", ok, f"HTTP {code} code={data.get('code')} token={'yes' if admin_token else 'no'}")
    if not admin_token:
        print(json.dumps(RESULTS, ensure_ascii=False, indent=2))
        return 1

    # 3 Create / preheat / open activity
    create_body = {
        "title": f"smoke-{int(time.time())}",
        "priceFen": 990,
        "originPriceFen": 1990,
        "stock": 20,
        "startAt": now_iso(-0.1),
        "endAt": now_iso(2),
    }
    code, body = req("POST", "/activity", create_body, token=admin_token)
    data = unwrap(body)
    act = data.get("data") if isinstance(data.get("data"), dict) else {}
    act_id = act.get("id")
    ok = code == 200 and data.get("code") == 0 and act_id
    record("3a.创建活动 stock=20", "code=0 返回 id", ok, f"HTTP {code} code={data.get('code')} id={act_id}")
    if not act_id:
        print(json.dumps({"results": RESULTS}, ensure_ascii=False, indent=2))
        return 1

    code, body = req("POST", f"/activity/{act_id}/preheat", token=admin_token)
    data = unwrap(body)
    ok = code == 200 and data.get("code") == 0
    record("3b.预热活动", "code=0", ok, f"HTTP {code} code={data.get('code')} msg={data.get('message')}")

    code, body = req("POST", f"/activity/{act_id}/open", token=admin_token)
    data = unwrap(body)
    ok = code == 200 and data.get("code") == 0
    record("3c.开抢", "code=0 status OPEN", ok, f"HTTP {code} code={data.get('code')} data={data.get('data')}")

    # 4 Register USER and grab
    uname = f"smoke_u_{int(time.time())}"
    code, body = req("POST", "/user/register", {"username": uname, "password": "pass1234", "nickname": "smoke"})
    data = unwrap(body)
    user_token = (data.get("data") or {}).get("token") if isinstance(data.get("data"), dict) else None
    # register may or may not return token — login if needed
    if not user_token:
        code2, body2 = req("POST", "/user/login", {"username": uname, "password": "pass1234"})
        data2 = unwrap(body2)
        user_token = (data2.get("data") or {}).get("token") if isinstance(data2.get("data"), dict) else None
        data = data2
        code = code2
    ok = bool(user_token)
    record("4a.注册/登录 USER", "获得 user token", ok, f"user={uname} token={'yes' if user_token else 'no'} code={data.get('code')}")
    if not user_token:
        print(json.dumps({"results": RESULTS, "activityId": act_id}, ensure_ascii=False, indent=2))
        return 1

    code, body = req("POST", "/seckill/999999999", token=user_token)
    data = unwrap(body)
    ok = data.get("code") == 1003
    record("4a2.非法活动ID", "code=1003 NOT_STARTED（布隆或 Lua）", ok, f"code={data.get('code')} msg={data.get('message')}")

    code, body = req("POST", f"/seckill/{act_id}", token=user_token)
    data = unwrap(body)
    grab_data = data.get("data") if isinstance(data.get("data"), dict) else {}
    order_token = grab_data.get("orderToken")
    ok = code == 200 and data.get("code") == 0 and bool(order_token)
    record("4b.首次抢购", "code=0 返回 orderToken", ok, f"HTTP {code} code={data.get('code')} orderToken={order_token}")

    # MQ 异步落单可能延迟十几秒，轮询订单列表
    order_no = None
    status0 = None
    for i in range(40):
        time.sleep(1.0)
        code, body = req("GET", "/order/list", token=user_token)
        data = unwrap(body)
        orders = data.get("data") if isinstance(data.get("data"), list) else []
        if orders:
            order_no = orders[0].get("orderNo") or order_token
            status0 = orders[0].get("status")
            break
    ok = bool(order_no) and status0 == "CREATED"
    record(
        "4c.订单落库",
        "存在 CREATED 订单（允许 MQ 延迟）",
        ok,
        f"status={status0} orderNo={order_no} waited≈{i+1}s",
    )

    # 5 Pay
    if order_no:
        code, body = req("POST", f"/order/{order_no}/pay", token=user_token)
        data = unwrap(body)
        paid = data.get("data") if isinstance(data.get("data"), dict) else {}
        ok = code == 200 and data.get("code") == 0 and (paid.get("status") == "PAID" or True)
        # verify via list
        time.sleep(0.5)
        code2, body2 = req("GET", f"/order/{order_no}", token=user_token)
        data2 = unwrap(body2)
        st = (data2.get("data") or {}).get("status") if isinstance(data2.get("data"), dict) else None
        ok = ok and st == "PAID"
        record("5.支付订单", "status=PAID", ok, f"pay code={data.get('code')} status={st}")
    else:
        record("5.支付订单", "status=PAID", False, "无 orderNo")

    # 6 Duplicate grab
    code, body = req("POST", f"/seckill/{act_id}", token=user_token)
    data = unwrap(body)
    ok = data.get("code") == 1004  # DUPLICATE
    record("6.同用户再次抢购", "code=1004 DUPLICATE", ok, f"code={data.get('code')} msg={data.get('message')}")

    # 7 Second activity: grab + cancel → stock rollback
    create_body2 = {
        "title": f"smoke-cancel-{int(time.time())}",
        "priceFen": 500,
        "originPriceFen": 1000,
        "stock": 5,
        "startAt": now_iso(-0.1),
        "endAt": now_iso(2),
    }
    code, body = req("POST", "/activity", create_body2, token=admin_token)
    data = unwrap(body)
    act2 = (data.get("data") or {}).get("id") if isinstance(data.get("data"), dict) else None
    ok = bool(act2)
    record("7a.创建第二活动", "获得 id", ok, f"id={act2}")

    if act2:
        req("POST", f"/activity/{act2}/preheat", token=admin_token)
        req("POST", f"/activity/{act2}/open", token=admin_token)
        uname2 = f"smoke_c_{int(time.time())}"
        req("POST", "/user/register", {"username": uname2, "password": "pass1234", "nickname": "c"})
        code, body = req("POST", "/user/login", {"username": uname2, "password": "pass1234"})
        data = unwrap(body)
        token2 = (data.get("data") or {}).get("token") if isinstance(data.get("data"), dict) else None
        code, body = req("POST", f"/seckill/{act2}", token=token2)
        data = unwrap(body)
        ok = data.get("code") == 0
        record("7b.另一用户抢购", "code=0", ok, f"code={data.get('code')}")
        ono = None
        for _ in range(40):
            time.sleep(1.0)
            code, body = req("GET", "/order/list", token=token2)
            data = unwrap(body)
            orders = data.get("data") if isinstance(data.get("data"), list) else []
            if orders:
                ono = orders[0].get("orderNo")
                break
        if ono:
            # reconcile before cancel
            code, body = req("GET", f"/activity/{act2}/reconcile", token=admin_token)
            before = unwrap(body).get("data") or {}
            code, body = req("POST", f"/order/{ono}/cancel", token=token2)
            data = unwrap(body)
            ok = data.get("code") == 0
            record("7c.取消订单", "code=0", ok, f"code={data.get('code')} order={ono}")
            time.sleep(1.0)
            code, body = req("GET", f"/activity/{act2}/reconcile", token=admin_token)
            after = unwrap(body).get("data") or {}
            # after cancel, redis should increase by 1 vs before (or consistent)
            ok = bool(after.get("consistent"))
            record(
                "7d.取消后库存回滚/对账",
                "consistent=true",
                ok,
                f"before redis={before.get('redisStock')} after redis={after.get('redisStock')} consistent={after.get('consistent')} msg={after.get('message')}",
            )
        else:
            record("7c.取消订单", "code=0", False, "无订单")
            record("7d.取消后库存回滚/对账", "consistent=true", False, "跳过")

    # 8 Reconcile first activity
    code, body = req("GET", f"/activity/{act_id}/reconcile", token=admin_token)
    data = unwrap(body)
    view = data.get("data") if isinstance(data.get("data"), dict) else {}
    ok = data.get("code") == 0 and bool(view.get("consistent"))
    record(
        "8.对账一致性",
        "consistent=true",
        ok,
        f"consistent={view.get('consistent')} init={view.get('initStock')} redis={view.get('redisStock')} created={view.get('createdCount')} paid={view.get('paidCount')} msg={view.get('message')}",
    )

    # 9 Expire scan existence (optional short endAt activity create+open, don't wait 3min)
    create_body3 = {
        "title": f"smoke-expire-{int(time.time())}",
        "priceFen": 100,
        "originPriceFen": 200,
        "stock": 1,
        "startAt": now_iso(-0.1),
        "endAt": now_iso(0.02),  # ~72 seconds
    }
    code, body = req("POST", "/activity", create_body3, token=admin_token)
    data = unwrap(body)
    act3 = (data.get("data") or {}).get("id") if isinstance(data.get("data"), dict) else None
    if act3:
        req("POST", f"/activity/{act3}/preheat", token=admin_token)
        code, body = req("POST", f"/activity/{act3}/open", token=admin_token)
        data = unwrap(body)
        ok = data.get("code") == 0
        record("9.短 endAt 活动可开抢(到期关抢链路存在)", "open code=0", ok, f"id={act3} code={data.get('code')}（未等待到期）")
    else:
        record("9.短 endAt 活动", "可创建并开抢", False, str(body)[:200])

    out = {
        "timestamp": datetime.now().isoformat(),
        "activityId": act_id,
        "activityId2": act2 if "act2" in dir() else None,
        "results": RESULTS,
        "failed": sum(1 for r in RESULTS if r["result"] == "FAIL"),
        "passed": sum(1 for r in RESULTS if r["result"] == "PASS"),
    }
    with open("/tmp/seckill-smoke-results.json", "w") as f:
        json.dump(out, f, ensure_ascii=False, indent=2)
    print(json.dumps(out, ensure_ascii=False, indent=2))
    return 1 if out["failed"] else 0


if __name__ == "__main__":
    sys.exit(main())
