# 推送 GitHub → 本机自动编译部署

self-hosted runner 已配置后，日常流程：

```text
改代码 → commit → push origin main
         → GitHub Actions 触发
         → Mac runner 执行 ./infra/scripts/ci-deploy.sh
         → 本机 docker build + kubectl 滚动更新
```

**Agent / 开发者不需要再手动跑部署**（除非 Runner 离线或用户明确要求）。  
入口：http://localhost:30080

工作流：`.github/workflows/deploy-local-k8s.yml`  
也可在 Actions 页手动 **Run workflow**。

无 Runner 时兜底：`./infra/scripts/ci-deploy.sh` 或 `./infra/scripts/deploy-local.sh user web`。

## Runner 维护

- 状态：`~/actions-runner` 下 `./svc.sh status`；GitHub → Settings → Actions → Runners 应为 Idle
- Docker Desktop **已启动**且 **Kubernetes 为 running**
- labels：`self-hosted`、`macOS`、`ARM64`（与 workflow `runs-on` 一致）

### 校验

```bash
kubectl get nodes
docker info >/dev/null && echo docker_ok
# GitHub Actions 页看最近一次 Deploy to local Docker Desktop K8s
```

## 注意

- Runner 与 Docker Desktop K8s **必须是同一台 Mac**（镜像不推远程仓库，仅本地 tag）
- Mac 合盖休眠时 Runner/集群可能停；演示前保持开机
- 同 tag（`0.1.0`）更新后脚本会 `rollout restart` 业务 Deployment
- **压测**仍需人工确认，不会由本工作流触发
