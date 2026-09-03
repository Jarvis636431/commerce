# Contributing

## 开发流程

1. 从最新的 `main` 创建短生命周期分支。
2. 只在一个 Pull Request 中处理一个明确主题。
3. 修改数据库结构时新增 Flyway Migration，不修改已经发布的 Migration。
4. 新增业务规则时同步增加测试和相关文档。
5. 提交前执行本地校验。

```bash
./mvnw test
docker build -t commerce:local .
```

## 提交边界

- Controller 负责协议、参数校验和状态码，不承载核心业务规则。
- Service 定义事务边界并编排领域操作。
- Repository 只负责持久化查询，不被其他模块的 Controller 直接调用。
- PostgreSQL 是业务事实源；Redis、Elasticsearch、RabbitMQ 和 MinIO 的一致性策略必须明确记录。
- 外部调用失败不能被静默吞掉，应当重试、补偿、告警或明确返回错误。
- 不提交密码、Token、证书、真实用户数据、日志和 IDE 文件。

## Pull Request 检查

- CI 中的 Maven verify 和容器构建必须通过。
- 说明修改的业务目标、事务边界和失败场景。
- 涉及 API、配置、架构或运行方式时更新文档。
- 涉及消息消费者时验证重复消费幂等性。
- 涉及金额、库存、优惠券时验证边界和并发行为。
