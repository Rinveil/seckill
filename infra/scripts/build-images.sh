#!/usr/bin/env bash
# 构建 arm64 镜像并载入本地 Docker（供 Desktop K8s 使用）
# 用法：在仓库根目录执行 ./infra/scripts/build-images.sh
# 注意：会执行 docker build（内含 mvn），耗时较长；请确认后再跑。

set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT"

PLATFORM="${PLATFORM:-linux/arm64}"
TAG="${TAG:-0.1.0}"

MODULES=(
  seckill-gateway
  seckill-user
  seckill-activity
  seckill-core
  seckill-order
)

echo "==> platform=${PLATFORM} tag=${TAG}"

for m in "${MODULES[@]}"; do
  echo "==> building ${m}:${TAG}"
  docker build \
    --platform "${PLATFORM}" \
    -f infra/docker/Dockerfile.java \
    --build-arg "MODULE=${m}" \
    -t "${m}:${TAG}" \
    .
done

echo "==> building seckill-web:${TAG}"
docker build \
  --platform "${PLATFORM}" \
  -f infra/docker/Dockerfile.web \
  -t "seckill-web:${TAG}" \
  .

echo "==> done. images:"
docker images | grep -E 'seckill-|REPOSITORY' | head -20
