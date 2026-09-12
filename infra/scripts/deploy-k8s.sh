#!/usr/bin/env bash
# 部署到 Docker Desktop Kubernetes
# 用法：./infra/scripts/deploy-k8s.sh
# 前置：已开启 Desktop Kubernetes，且已构建好镜像。

set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT"

echo "==> context: $(kubectl config current-context)"
kubectl apply -f infra/k8s/

echo "==> waiting for deployments..."
kubectl -n seckill rollout status deploy/mysql --timeout=180s || true
kubectl -n seckill rollout status deploy/redis --timeout=120s || true
kubectl -n seckill rollout status deploy/rabbitmq --timeout=180s || true
kubectl -n seckill rollout status deploy/seckill-user --timeout=180s
kubectl -n seckill rollout status deploy/seckill-activity --timeout=180s
kubectl -n seckill rollout status deploy/seckill-core --timeout=180s
kubectl -n seckill rollout status deploy/seckill-order --timeout=180s
kubectl -n seckill rollout status deploy/seckill-gateway --timeout=180s
kubectl -n seckill rollout status deploy/seckill-web --timeout=120s

echo "==> pods:"
kubectl -n seckill get pods
echo "==> open http://localhost:30080"
