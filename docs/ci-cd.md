# 本机部署（当前方案）

已弃用 GitHub self-hosted Runner。日常：

```text
改代码 → commit → push origin main
       → Agent / 本机执行 ./infra/scripts/deploy-local.sh <模块>
```

```bash
# 全量
./infra/scripts/ci-deploy.sh

# 按模块（更快）
./infra/scripts/deploy-local.sh user web
./infra/scripts/deploy-local.sh activity order
```

入口：http://localhost:30080  
前置：Docker Desktop 已启动且 Kubernetes Ready。

## 注意

- 镜像只在本机 tag（`0.1.0`），同 tag 更新后脚本会 `rollout restart`
- **压测已结束**：不再跑压测；历史报告见 [test-report.md](./test-report.md)
