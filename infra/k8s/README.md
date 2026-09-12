# K8s 空壳部署（第 1 步）

## 前置

1. Docker Desktop → Settings → **Kubernetes** → Enable  
2. 内存建议 **8GB**  
3. `kubectl config current-context` 应为 `docker-desktop`

## 构建镜像（需确认后执行）

在仓库根目录：

```bash
chmod +x infra/scripts/*.sh
./infra/scripts/build-images.sh
```

默认 `--platform linux/arm64`，镜像 tag `0.1.0`。

## 部署

```bash
./infra/scripts/deploy-k8s.sh
# 或
kubectl apply -f infra/k8s/
```

## 访问

- B 端：http://localhost:30080  
- API（经 nginx 反代）：http://localhost:30080/api/...  

## 常用命令

```bash
kubectl -n seckill get pods
kubectl -n seckill logs deploy/seckill-gateway
kubectl delete namespace seckill   # 注意：PVC 数据会一并删掉（若用 Delete 回收）
```
