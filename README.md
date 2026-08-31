# Commerce

一个用于系统学习 Java 后端生态的电商项目。目前采用 Java 21、Spring Boot、Spring MVC、Spring Data JPA、Flyway 和 PostgreSQL，以模块化单体的方式逐步实现商品、库存、订单、缓存和消息等能力。

详细的架构与原理笔记见 [KNOWLEDGE.md](KNOWLEDGE.md)。

## 当前能力

- 商品创建、查询、分页、修改、删除和上下架
- 商品 SKU 的创建、查询、修改和删除
- 库存初始化、入库、预占、确认和释放
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
- Flyway
- H2（测试）
- Maven Wrapper
- Docker Compose

## 项目结构

```text
src/main/java/com/jarvis/commerce
├── common       通用异常、错误处理和分页响应
├── product      Product、SKU 及其接口
└── inventory    库存及其业务操作

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

## 后续路线

1. 订单和订单项
2. 库存预占记录与幂等
3. 用户与收货地址
4. 支付状态流转
5. Redis 商品缓存与购物车
6. MQ 延迟关单、库存释放和异步通知
7. 监控、压测与并发策略对比
