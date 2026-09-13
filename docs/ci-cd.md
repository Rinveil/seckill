# 推送 GitHub → 本机自动编译部署

Docker Desktop 里的 Kubernetes **不会**直接监听 GitHub。  
本方案用 **GitHub Actions + Mac self-hosted runner**：`push` 到 `main` 后，在你这台 Mac 上执行 `docker build` + `kubectl apply/rollout`。

```text
git push origin main
    → GitHub Actions 触发
    → Mac self-hosted runner 领取任务
    → ./infra/scripts/ci-deploy.sh
    → 本地镜像 + Docker Desktop K8s 滚动更新
```

无 Runner 时本机手动：`./infra/scripts/ci-deploy.sh` 或按模块 `./infra/scripts/deploy-local.sh order web`。  
入口：http://localhost:30080

## 一次性配置 Runner（本机）

1. 打开仓库：**Settings → Actions → Runners → New self-hosted runner**  
   地址示例：`https://github.com/Rinveil/seckill/settings/actions/runners/new`
2. 选择 **macOS**，按页面命令下载并配置，**labels 务必包含**：
   - `self-hosted`
   - `macOS`
   - `ARM64`
3. 安装并启动服务（页面会给出 `./svc.sh install` / `./svc.sh start`）
4. 确认 Docker Desktop **已启动**且 **Kubernetes 为 running**
5. Runner 在线后，任意 push 到 `main` 即可自动部署

### 校验

```bash
# Runner 在 GitHub 页面显示 Idle/Online
# 本地：
kubectl get nodes
docker info >/dev/null && echo docker_ok
```

工作流文件：`.github/workflows/deploy-local-k8s.yml`  
也可在 Actions 页手动 **Run workflow**。

## 注意

- Runner 与 Docker Desktop K8s **必须是同一台 Mac**（镜像不推远程仓库，仅本地 tag）
- Mac 合盖休眠时 Runner/集群可能停；演示前保持开机
- 同 tag（`0.1.0`）更新后脚本会 `rollout restart` 业务 Deployment
- **压测**仍需人工确认，不会由本工作流触发
