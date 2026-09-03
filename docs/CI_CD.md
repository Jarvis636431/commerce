# CI/CD 与容器交付

## 边界

本项目把流水线分成两个职责：

```text
CI：代码是否可合并
→ Maven verify
→ 单元和接口测试
→ Docker 镜像可构建性验证

CD：已验证代码如何形成可追踪制品
→ 构建 OCI 镜像
→ 生成分支、Git Tag 和 Commit SHA 标签
→ 生成 SBOM 与 provenance
→ 推送 GitHub Container Registry
```

这里的 CD 是 Continuous Delivery，而不是自动部署生产。镜像发布后仍需要明确的环境配置、审批和部署平台，仓库不会仅因提交代码就修改真实服务器。

## CI 工作流

`.github/workflows/ci.yml` 在 Pull Request 和 main 分支 Push 时运行：

1. 使用 Eclipse Temurin Java 21。
2. 缓存 Maven 依赖。
3. 执行 `./mvnw -B -ntp verify`。
4. 失败时上传 Surefire 报告，便于排查测试。
5. 测试通过后使用多阶段 Dockerfile 构建镜像，但不发布。

建议在 GitHub 仓库的 Branch protection rules 中把 `Java 21 build and test` 和 `Validate container image` 设置为 main 分支必需检查，并禁止直接绕过 Pull Request。

## 镜像发布

`.github/workflows/cd.yml` 在以下场景发布到 GHCR：

- main 分支：`main`、`latest`、`sha-<commit>`。
- `v*` 标签：版本标签与 `sha-<commit>`。
- 手工触发：使用当前引用生成适用标签。

CD 在发布前会独立执行一次 Maven verify。即使发布工作流与 main 分支 CI 同时触发，测试失败也不会产生新的远程镜像。

镜像名称自动转换为小写：

```text
ghcr.io/jarvis636431/commerce
```

工作流使用 GitHub 自动提供的 `GITHUB_TOKEN`，只授予读取仓库和写入 Packages 的最小权限，不需要创建个人访问令牌。发布的镜像同时包含 SBOM 和构建 provenance。

发布版本示例：

```bash
git tag v0.1.0
git push origin v0.1.0
```

拉取镜像：

```bash
docker pull ghcr.io/jarvis636431/commerce:v0.1.0
```

GHCR 新 Package 首次发布后可能是私有状态。是否公开应由你在 GitHub Package 设置中明确决定。

## Dockerfile

Dockerfile 使用两个阶段：构建阶段包含 JDK 和 Maven，运行阶段只有 JRE 与应用 JAR。应用使用非 root 的 `commerce` 用户运行，限制 JVM 最大 RAM 百分比，并通过 `/actuator/health` 提供容器健康检查。

本地验证：

```bash
docker build -t commerce:local .
docker run --rm -p 8080:8080 \
  -e DB_URL=jdbc:postgresql://host.docker.internal:5432/commerce \
  -e REDIS_HOST=host.docker.internal \
  -e RABBITMQ_HOST=host.docker.internal \
  -e ELASTICSEARCH_URIS=http://host.docker.internal:9200 \
  -e MINIO_ENDPOINT=http://host.docker.internal:9000 \
  -e AUTH_JWT_SECRET='replace-with-at-least-32-random-bytes' \
  commerce:local
```

## Secret 与环境配置

镜像不包含生产 Secret。数据库密码、RabbitMQ 密码、MinIO Secret 和 JWT 密钥必须在部署环境注入。GitHub Actions 当前只发布通用镜像，因此不需要保存这些业务 Secret。

真正部署到测试或生产环境时，应新增独立的 Deployment workflow，并使用 GitHub Environments 实现环境 Secret、审批和部署记录；不要直接把服务器密码写进 YAML。
