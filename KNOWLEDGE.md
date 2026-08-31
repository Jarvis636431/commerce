# Commerce Knowledge

本文档记录项目开发过程中涉及的核心架构、Spring、JPA 和电商领域知识。它不是接口使用手册；运行方式和 API 列表见 [README.md](README.md)。

## 1. 总体架构

项目当前采用模块化单体：应用作为一个进程部署，但代码按照业务能力拆分。

```text
commerce
├── common
├── product
├── inventory
├── order       后续实现
└── payment     后续实现
```

这样既保留单体应用事务简单、调试方便的优点，也通过清晰的模块边界为后续演进做准备。现在没有必要为了学习而过早拆成微服务。

### 一次请求的调用链

```text
HTTP Request
    ↓
Controller
    ↓
Service
    ↓
Repository
    ↓
Spring Data JPA / Hibernate
    ↓
Database
```

各层职责：

- Controller：处理 HTTP、JSON、参数校验和状态码。
- Service：编排业务用例，定义事务边界。
- Repository：抽象数据库访问。
- Entity：保存需要持久化的状态，并承载核心业务规则。
- Request/Response DTO：定义 API 输入输出，避免直接暴露 Entity。

## 2. Spring 注解的本质

注解通常不会自己执行功能。它们提供元数据，由 Spring、Hibernate、JUnit 等框架读取。

看到一个注解时，可以问：

1. 谁读取它？
2. 什么时候读取？
3. 它注册了对象、配置了行为，还是创建了代理？
4. 去掉它会发生什么？

常见注解：

| 注解 | 读取者 | 主要作用 |
|---|---|---|
| `@Service` | Spring 容器 | 注册 Service Bean |
| `@RestController` | Spring / Spring MVC | 注册 Bean 和 HTTP Controller |
| `@GetMapping` | Spring MVC | 注册 GET 路由 |
| `@Transactional` | Spring Transaction | 通过代理管理事务 |
| `@Entity` | JPA / Hibernate | 声明持久化实体 |
| `@Valid` | Spring MVC / Validation | 触发参数校验 |
| `@NotBlank` | Jakarta Validation | 声明具体校验规则 |
| `@Test` | JUnit | 声明测试方法 |

Spring 的主要风格是声明通用机制，核心业务规则仍应使用清晰的 Java 代码表达。

## 3. IOC 与依赖注入

Service 不主动创建 Repository：

```java
public ProductService(ProductRepository productRepository) {
    this.productRepository = productRepository;
}
```

Spring 容器负责创建对象并组装依赖。这叫控制反转（IOC）；把依赖传入对象的过程叫依赖注入（DI）。

项目采用构造器注入，因为它能明确表达依赖、允许字段使用 `final`，也更便于测试。

## 4. Product 与 SKU

Product 表示用户看到的商品概念，也常被称为 SPU；SKU 表示能够被明确购买、定价和管理库存的具体规格。

```text
Product：iPhone
├── SKU：黑色 + 256GB
├── SKU：白色 + 256GB
└── SKU：黑色 + 512GB
```

二者是一对多关系：

```text
Product 1 ── N SKU
```

在 JPA 中，当前使用从 SKU 指向 Product 的单向关系：

```java
@ManyToOne(fetch = FetchType.LAZY, optional = false)
@JoinColumn(name = "product_id", nullable = false)
private Product product;
```

价格属于 SKU，因为不同规格可能有不同价格。库存也对应 SKU，因为不同规格的剩余数量不同。

当前商品状态：

```text
DRAFT → ON_SALE → OFF_SALE
                 └──────→ ON_SALE
```

主要规则：

- 新商品默认为草稿。
- 至少有一个 SKU 才能上架。
- 上架商品不能编辑或删除。
- 上架商品的 SKU 不能增删改。

## 5. Repository 与 Spring Data JPA

Repository 只需要声明接口：

```java
public interface ProductRepository extends JpaRepository<Product, Long> {
}
```

Spring Data JPA 在运行时创建代理实现，并提供 `save`、`findById`、`findAll`、`delete` 等操作。

方法名也可以描述查询：

```java
Optional<Inventory> findBySkuId(Long skuId);
```

Spring Data 会解析 `findBySkuId` 并生成相应查询。

## 6. 数据库结构管理

Hibernate 配置为：

```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: validate
```

Hibernate只校验 Entity 与数据库结构，不自动修改表。表结构由 Flyway 迁移脚本管理：

```text
V1__create_product_tables.sql
V2__create_inventory_table.sql
```

已执行的迁移不应修改。结构变化时新增 V3、V4 等迁移文件。

## 7. 库存预占模型

库存不是一个含义模糊的数字，而是处于不同业务状态的数量。

当前模型：

```text
availableQuantity：可售数量
reservedQuantity：已被订单占用但尚未最终成交的数量
```

状态流转：

```text
入库：      外部 → available
下单预占：  available → reserved
支付成功：  reserved → 已售出
订单取消：  reserved → available
```

例如初始库存为 10，下单预占 3：

```text
预占前：available = 10, reserved = 0
预占后：available = 7,  reserved = 3
```

支付成功：

```text
available = 7, reserved = 0
```

订单取消：

```text
available = 10, reserved = 0
```

因此业务方法使用 `reserve`、`confirm`、`release`，而不是含义不清的 `updateQuantity`。

后续订单模块还需要增加库存预占记录，以回答“哪一个订单预占了哪一个 SKU 的多少库存”。

## 8. JPA 脏检查

脏检查负责发现受管理实体的状态发生变化，并自动生成 `UPDATE`。

```java
@Transactional
public void reserve(long skuId, int quantity) {
    Inventory inventory = repository.findBySkuId(skuId).orElseThrow();
    inventory.reserve(quantity);
}
```

执行过程：

```text
1. 在事务中查询 Inventory。
2. Hibernate 把实体加入持久化上下文并保存初始快照。
3. Java 方法修改实体字段。
4. flush 或提交时，Hibernate 比较当前状态与初始快照。
5. 如果不同，Hibernate 自动生成 UPDATE。
```

对于当前事务查询出的 Managed Entity，修改后通常不需要再次调用 `save()`。新创建的对象仍需要通过 `save()` 进入持久化上下文。

脏检查通常要求：

- 实体处于 Managed 状态。
- 修改发生在有效事务中。
- 事务最终 flush 或 commit。

### flush 与 commit

```text
flush：把持久化上下文中的变化同步为数据库 SQL。
commit：正式提交整个事务。
```

flush 后仍然可能回滚。项目在库存变更后显式 flush，以立即触发数据库约束和乐观锁检查，并在响应中返回最新的 `version` 和 `updatedAt`。

## 9. `@Version` 乐观锁

乐观锁用于防止两个并发事务相互覆盖更新结果。

```java
@Version
private long version;
```

Hibernate 会生成类似 SQL：

```sql
update inventory
set available_quantity = ?,
    reserved_quantity = ?,
    version = 1
where id = ?
  and version = 0;
```

假设请求 A 和请求 B 都读取到版本 0：

```text
A 先更新成功，数据库 version 变成 1。
B 仍然使用 version = 0 更新，影响行数为 0。
Hibernate 检测到冲突并抛出乐观锁异常。
```

项目将该异常转换为 HTTP 409。失败请求必须重新读取最新库存，然后重新判断或重试；不能在已经失败的事务里直接继续。

乐观锁与悲观锁的区别：

| 特性 | 乐观锁 | 悲观锁 |
|---|---|---|
| 读取时锁住数据库行 | 否 | 是 |
| 冲突表现 | 一个更新失败 | 后来的事务等待 |
| 常见实现 | version 条件 | `SELECT ... FOR UPDATE` |
| 适合场景 | 冲突较少 | 热点数据、冲突频繁 |

一句话记忆：

```text
脏检查发现“对象变了”，负责生成 UPDATE。
乐观锁检查“别人是否先改过”，防止覆盖更新。
```

## 10. 事务边界

`@Transactional` 通常标注在 Service 的业务用例方法上：

```java
@Transactional
public InventoryResponse reserve(...) {
}
```

Spring 通过代理在方法调用外层执行：

```text
开启事务 → 执行业务方法 → flush → commit
                              └→ 异常时 rollback
```

查询操作使用 `@Transactional(readOnly = true)` 表达只读意图。

需要注意，同一个类内部直接调用另一个 `@Transactional` 方法可能绕过 Spring 代理，因此不能仅凭注解位置假设事务一定生效。

## 11. 参数校验与错误响应

Request DTO 使用 Jakarta Validation：

```java
public record InventoryQuantityRequest(
    @Min(1) int quantity
) {
}
```

Controller 通过 `@Valid` 触发校验。统一异常处理将结果映射为：

```text
400：请求格式或字段校验失败
404：Product、SKU 或 Inventory 不存在
409：业务状态、库存不足、并发或唯一约束冲突
```

错误响应采用 Spring `ProblemDetail`，避免每个 Controller 重复编写 `try/catch`。

## 12. 测试策略

当前接口测试使用：

```java
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
```

它会经过 JSON 转换、参数校验、Controller、Service、Repository、Hibernate 和 H2 数据库，属于集成测试。每个测试结束后事务回滚，测试之间不会互相污染。

H2 测试适合快速反馈，但不能完全代替 PostgreSQL 集成测试，尤其是 SQL 方言、锁和真实并发行为。后续可加入 Testcontainers，在测试中启动真实 PostgreSQL。

## 13. 订单、订单项与成交快照

订单模块由三个核心概念组成：

```text
CustomerOrder  交易整体和状态
OrderItem      本次交易购买的具体 SKU
InventoryReservation  订单对应的库存预占归属
```

订单项不仅保存 `skuId`，还保存下单时的 SKU 编码、名称和单价。这是成交快照：商品之后即使改名或调价，历史订单仍然显示下单时的信息。

订单金额由服务端根据数据库中的 SKU 价格计算，不能相信客户端提交的价格：

```text
subtotal = unitPrice × quantity
totalAmount = 所有 subtotal 之和
```

订单创建在一个事务中完成：

```text
校验 SKU 和商品状态
→ 计算成交金额
→ 保存订单
→ 预占各 SKU 库存
→ 保存订单项快照
→ 保存库存预占记录
→ 提交事务
```

任意 SKU 库存不足时，整个事务回滚，前面已做的预占和订单记录都不能保留。

多个 SKU 按 ID 排序后再预占，使并发事务尽量采用一致的加锁/更新顺序，降低后续使用数据库锁时发生死锁的概率。

## 14. 订单状态机

当前订单状态：

```text
PENDING_PAYMENT ──支付成功──→ PAID ──确认完成──→ COMPLETED
       │
       └──取消/超时──→ CANCELLED
```

状态变化通过实体方法表达：

```java
order.markPaid();
order.cancel();
order.complete();
```

实体会检查来源状态，阻止 `CANCELLED → PAID` 等非法转换。状态机的重点不是枚举本身，而是明确允许哪些转换以及每个转换需要触发哪些副作用。

订单和库存的联动：

```text
创建订单：库存 reserve，预占记录 RESERVED
支付成功：库存 confirm，预占记录 CONFIRMED
取消订单：库存 release，预占记录 RELEASED
订单完成：库存不再变化
```

## 15. 订单与支付的边界

订单核心不等于支付。订单核心包括：

- 成交内容和价格快照
- 订单状态机
- 金额计算
- 库存一致性
- 取消和完成规则

支付模块负责支付尝试、第三方渠道交互和到账事实。订单不再公开直接支付接口，只有支付模块确认到账后才能调用订单服务推进状态并确认库存。

当前支付模块已经包含：

- 支付单及 `PENDING / SUCCESS / FAILED / CLOSED` 状态
- `Idempotency-Key` 创建幂等
- 订单金额复制到支付单
- 回调金额核对
- 外部交易号唯一约束
- 通知 ID 去重
- 相同渠道交易的重复成功通知幂等
- 支付关闭时取消订单并释放库存
- 失败支付重新尝试
- 支付到期时间和定时关闭

`mock-success` 和 `mock-failure` 只是模拟渠道通知。真实渠道接入仍需要验签，绝不能直接相信回调 JSON。

真实支付渠道接入还需要：

- 支付单和支付流水号
- 第三方支付请求
- 回调签名校验
- 重复回调幂等
- 支付金额核对
- 支付超时和关单
- 订单状态与支付状态的一致性

第三方回调可能重复到达，所以支付成功处理不能简单地假设只调用一次。后续需要使用支付流水唯一约束、幂等键和明确的状态判断保证重复通知不会重复确认库存或重复推进订单。

## 16. 支付超时

支付单创建时记录明确的 `expiresAt`，而不是仅依赖“创建时间大约超过 15 分钟”的隐式计算。当前默认规则：

```text
expiresAt = 创建时间 + 15 分钟
```

定时扫描器查找：

```text
status = PENDING and expiresAt <= now
```

每批最多取 100 条，并逐笔调用带事务的过期操作：

```text
Payment PENDING → CLOSED
Order PENDING_PAYMENT → CANCELLED
Inventory reserved → available
Reservation RESERVED → RELEASED
```

逐笔事务可以避免某一条异常导致整批支付全部回滚。支付和定时任务同时更新时，`@Version` 会让其中一个操作失败，避免支付成功和超时关闭同时覆盖对方。

项目将 `Clock` 注册为 Bean，业务代码不直接散落调用系统时间。这样测试可以传入明确时间验证边界，也方便以后统一时区。

成功通知即使在扫描器尚未运行，只要当前时间已经超过 `expiresAt`，也不能继续将支付标记为成功。当前策略会拒绝晚到成功通知；真实渠道还需要把“用户已扣款但订单已关闭”记录为异常支付，并进入退款或人工补偿流程。
