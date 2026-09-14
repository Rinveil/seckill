#!/usr/bin/env bash
# 部署到 Docker Desktop Kubernetes
# 用法：./infra/scripts/deploy-k8s.sh
# 前置：已开启 Desktop Kubernetes，且已构建好镜像。

set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT"

echo "==> context: $(kubectl config current-context)"
kubectl apply -f infra/k8s/

# 同 tag 镜像更新后必须重启，否则 IfNotPresent 不会换新层
echo "==> restart app deployments to pick up new images"
kubectl -n seckill rollout restart \
  deploy/seckill-user \
  deploy/seckill-activity \
  deploy/seckill-core \
  deploy/seckill-order \
  deploy/seckill-gateway \
  deploy/seckill-web || true

echo "==> waiting for deployments..."
kubectl -n seckill rollout status deploy/mysql --timeout=180s || true
kubectl -n seckill rollout status deploy/redis --timeout=120s || true
# RabbitMQ 可用 replicas=0 停用；仅在期望有副本时等待
for d in rabbitmq; do
  want=$(kubectl -n seckill get deploy "$d" -o jsonpath='{.spec.replicas}' 2>/dev/null || echo 0)
  if [[ "${want:-0}" != "0" ]]; then
    kubectl -n seckill rollout status "deploy/${d}" --timeout=240s || true
  else
    echo "==> skip wait ${d} (replicas=0)"
  fi
done
kubectl -n seckill rollout status deploy/seckill-user --timeout=240s
kubectl -n seckill rollout status deploy/seckill-activity --timeout=240s
kubectl -n seckill rollout status deploy/seckill-core --timeout=240s
kubectl -n seckill rollout status deploy/seckill-order --timeout=240s
kubectl -n seckill rollout status deploy/seckill-gateway --timeout=240s
kubectl -n seckill rollout status deploy/seckill-web --timeout=180s

echo "==> pods:"
kubectl -n seckill get pods
echo "==> open http://localhost:30080"
