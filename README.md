# Commerce

一个用于系统学习 Java 后端生态的电商项目。目前采用 Java 21、Spring Boot、Spring MVC、Spring Data JPA、Flyway 和 PostgreSQL，以模块化单体的方式逐步实现商品、库存、订单、缓存和消息等能力。

详细的架构与原理笔记见 [KNOWLEDGE.md](KNOWLEDGE.md)，Redis 专题见 [REDIS_KNOWLEDGE.md](REDIS_KNOWLEDGE.md)。

## 当前能力

- 商品创建、查询、分页、修改、删除和上下架
- 商品 SKU 的创建、查询、修改和删除
- 库存初始化、入库、预占、确认和释放
- 订单创建、查询、支付确认、取消和完成
- 订单项成交快照和订单级库存预占记录
- 支付单、幂等创建、模拟支付通知和重复通知去重
- 支付超时扫描、自动取消订单和库存释放
- 用户创建、查询、资料修改和启用/禁用
- 用户收货地址管理、默认地址和订单地址快照
- 基于 Redis Hash 的用户购物车、原子数量累加和 30 天 TTL
- 从购物车结算创建订单，提交后按快照安全移除购物车项
- 商品详情 Cache Aside、空值缓存和随机 TTL
- 参数校验与统一的 400、404、409 错误响应
- Flyway 数据库版本管理
- JPA 乐观锁
- 基于 H2 的接口集成测试

## 技术栈

- Java 21
- Spring Boot 4
- Spring MVC
- Spring Data JPA / Hibernate
- PostgreSQL
- Redis / Spring Data Redis
- Flyway
- H2（测试）
- Maven Wrapper
- Docker Compose

## 项目结构

```text
src/main/java/com/jarvis/commerce
├── common       通用异常、错误处理和分页响应
├── user         用户资料和用户状态
├── cart         Redis 购物车及商品实时展示
├── product      Product、SKU 及其接口
├── inventory    库存及其业务操作
├── order        订单、订单项和库存预占记录
└── payment      支付单和支付通知处理

src/main/resources
├── application.yaml
└── db/migration 数据库迁移脚本
```

项目按业务模块组织，而不是把所有 Controller、Service 和 Repository 分别放在全局目录中。每个模块内部保留清晰的调用方向：

```text
Controller → Service → Repository → JPA/Hibernate → Database
```

## 本地运行

需要安装：

- JDK 21
- Docker Desktop 或兼容的 Docker 环境
- Make（可选，命令也可以直接执行）

启动 PostgreSQL：

```bash
make db-up
```

查看数据库状态：

```bash
make db-status
```

启动应用：

```bash
make run
```

应用默认地址为 `http://localhost:8080`，健康检查：

```bash
curl http://localhost:8080/actuator/health
```

停止 PostgreSQL：

```bash
make db-down
```

## 运行测试

测试默认使用 H2 内存数据库，不要求启动 PostgreSQL：

```bash
make test
```

等价的原始命令：

```bash
./mvnw test
```

## 快速体验

创建商品：

```bash
curl -X POST http://localhost:8080/api/products \
  -H 'Content-Type: application/json' \
  -d '{"name":"Java 后端课程","description":"学习 Spring Boot"}'
```

假设商品 ID 为 `1`，创建 SKU：

```bash
curl -X POST http://localhost:8080/api/products/1/skus \
  -H 'Content-Type: application/json' \
  -d '{"code":"JAVA-001","name":"标准版","price":99.90}'
```

假设 SKU ID 为 `1`，初始化库存：

```bash
curl -X PUT http://localhost:8080/api/skus/1/inventory \
  -H 'Content-Type: application/json' \
  -d '{"quantity":10}'
```

上架商品：

```bash
curl -X POST http://localhost:8080/api/products/1/on-sale
```

预占库存：

```bash
curl -X POST http://localhost:8080/api/skus/1/inventory/reserve \
  -H 'Content-Type: application/json' \
  -d '{"quantity":3}'
```

查询库存：

```bash
curl http://localhost:8080/api/skus/1/inventory
```

## API 概览

### Product

```http
POST   /api/products
GET    /api/products/{id}
GET    /api/products?page=0&size=20
PUT    /api/products/{id}
DELETE /api/products/{id}
POST   /api/products/{id}/on-sale
POST   /api/products/{id}/off-sale
```

### User

```http
POST /api/users
GET  /api/users/{id}
GET  /api/users?page=0&size=20
PUT  /api/users/{id}
POST /api/users/{id}/disable
POST /api/users/{id}/enable
```

### Address

```http
POST   /api/users/{userId}/addresses
GET    /api/users/{userId}/addresses
GET    /api/users/{userId}/addresses/{addressId}
PUT    /api/users/{userId}/addresses/{addressId}
POST   /api/users/{userId}/addresses/{addressId}/default
DELETE /api/users/{userId}/addresses/{addressId}
```

### SKU

```http
POST   /api/products/{productId}/skus
GET    /api/products/{productId}/skus
PUT    /api/products/{productId}/skus/{skuId}
DELETE /api/products/{productId}/skus/{skuId}
```

### Inventory

```http
PUT  /api/skus/{skuId}/inventory
GET  /api/skus/{skuId}/inventory
POST /api/skus/{skuId}/inventory/increase
POST /api/skus/{skuId}/inventory/reserve
POST /api/skus/{skuId}/inventory/confirm
POST /api/skus/{skuId}/inventory/release
```

### Cart

```http
GET    /api/users/{userId}/cart
POST   /api/users/{userId}/cart/items
PUT    /api/users/{userId}/cart/items/{skuId}
DELETE /api/users/{userId}/cart/items/{skuId}
DELETE /api/users/{userId}/cart
POST   /api/users/{userId}/cart/checkout
```

### Order

```http
POST /api/orders
GET  /api/orders/{id}
GET  /api/orders?page=0&size=20&userId=1
POST /api/orders/{id}/pay
POST /api/orders/{id}/cancel
POST /api/orders/{id}/complete
```

订单不能再通过公开接口直接标记支付成功，必须由支付模块确认到账后推进。

### Payment

```http
POST /api/payments                         Idempotency-Key 请求头必填
GET  /api/payments/{paymentNo}
POST /api/payments/{paymentNo}/mock-success
POST /api/payments/{paymentNo}/mock-failure
POST /api/payments/{paymentNo}/retry
POST /api/payments/{paymentNo}/close
```

支付默认在创建 15 分钟后过期。应用每 60 秒扫描一次到期支付，可通过以下配置调整：

```yaml
commerce:
  payment:
    timeout: PT15M
    timeout-scan-interval: 60000
```

## 后续路线

1. 真实支付渠道、签名验证和支付查询补偿
2. Redis 商品缓存
3. MQ 延迟关单、库存释放和异步通知
4. Spring Security 与 JWT
5. 监控、压测与并发策略对比
