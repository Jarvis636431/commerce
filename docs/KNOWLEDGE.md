# Commerce Knowledge

本文档记录项目开发过程中涉及的核心架构、Spring、JPA 和电商领域知识。它不是接口使用手册；运行方式和 API 列表见 [README.md](README.md)。

## 1. 总体架构

项目当前采用模块化单体：应用作为一个进程部署，但代码按照业务能力拆分。

```text
commerce
├── common
├── user
├── product
├── inventory
├── order
└── payment
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
## 17. 用户业务模型与认证的边界

当前用户模块负责用户的业务身份，只保存用户名、邮箱、手机号和状态。它不负责登录，也不保存密码。这能避免过早把两个概念混在一起：

- 用户业务模型回答“这笔订单属于谁”。
- 认证系统回答“当前请求是谁发出的，以及他是否有权限操作”。

用户状态使用 `ACTIVE / DISABLED`，禁用是状态转换而不是物理删除。电商用户一旦产生订单、支付等历史数据，通常不能直接删除，否则会破坏审计和关联关系。

用户名和邮箱在进入数据库前会进行规范化：去除首尾空格并转为小写。Service 中的重复检查用于返回友好的业务错误，数据库唯一约束则负责抵御并发请求，因此两层校验都需要保留。

用户实体也使用 `@Version` 乐观锁，防止管理员禁用用户和用户修改资料等并发操作互相覆盖。后续引入 Spring Security 时，再增加密码哈希、登录凭证、角色和权限，不应在用户表中保存明文密码。

## 18. 收货地址与订单快照

收货地址是用户可以持续修改的资料，订单则是已经发生的交易事实。订单如果只保存 `addressId`，用户修改或删除地址后，历史订单展示的收货信息也会变化或丢失。

因此创建订单时会验证地址确实属于当前用户，然后把以下字段复制到订单：

```text
receiverName / phone / province / city / district / detailAddress / postalCode
```

这就是订单地址快照。地址表负责当前资料，订单快照负责历史事实，两者生命周期不同。订单仍保存 `userId` 表示业务归属，但不会依赖地址表来还原历史收货信息。

第一条地址自动成为默认地址；设置新的默认地址时，Service 会先清除旧默认地址。当前单体事务可以保证普通操作的一致性，将来高并发场景还需要数据库约束或串行化策略保证一个用户最多只有一个默认地址。

V7 迁移给历史订单增加的用户与地址字段保持可空，以便已有开发数据能够平滑升级；通过新接口创建的订单则必须提供有效的 `userId` 和属于该用户的 `addressId`。

## 19. Redis 购物车

购物车使用 Redis Hash：

```text
key   = cart:{userId}
field = skuId
value = quantity
TTL   = 30 天
```

Redis 只保存 SKU ID 和数量。查询购物车时再批量读取数据库中的最新名称、价格、上下架状态和可用库存；购物车里的价格不是成交依据，创建订单时仍会重新读取数据库并生成订单项快照。

“加入同一个 SKU”通过 Lua 脚本在 Redis 内一次完成读取、数量累加、99 件上限判断和 TTL 刷新。这些步骤作为一个原子操作执行，避免两个请求同时读取旧数量后互相覆盖。

加入购物车不预占库存。购物车可能保存数天，如果此时占库存会导致其他用户无法购买；只有真正创建订单时才检查并预占库存。因此购物车响应中的 `purchasable` 只是当前时刻的展示结果，不承诺结算时一定还有库存。

生产环境使用 `RedisCartStore`，测试 profile 使用实现同一接口的 `InMemoryCartStore`。这样业务测试不依赖外部 Redis，同时存储实现可以独立进行 Redis 集成测试。Redis 无法连接时，接口返回 503，而不是把基础设施故障伪装成空购物车。

## 20. 购物车结算与跨存储一致性

购物车在 Redis，订单和库存则在 PostgreSQL，两者不能加入同一个数据库本地事务。如果先删除购物车再创建订单，订单回滚会造成购物车丢失；如果在订单事务提交前删除，也仍可能出现数据库最终提交失败的问题。

当前结算流程是：

```text
读取购物车快照
→ 数据库事务内创建订单并预占库存
→ 数据库提交成功
→ afterCommit 回调删除购物车中的已结算项
```

删除时使用 Lua 脚本比较 SKU 当前数量与结算快照数量，只有完全相同才删除。如果用户在结算期间修改了数量，新值不会被旧结算请求误删。若订单创建失败，事务回滚且不会触发删除，购物车保持不变。

如果 PostgreSQL 已提交但 Redis 删除失败，系统选择保留购物车并记录警告，因为“购物车仍有商品”比“订单已经成功却向客户端报失败”更容易补偿。生产级版本还应给结算请求增加幂等键，防止用户因购物车残留而重复创建订单，并可通过 Outbox 或后台补偿任务重试清理。

## 21. 商品详情缓存

商品详情采用 Cache Aside：先读 `product:detail:{id}`，未命中时读取 PostgreSQL 并写回 Redis。正常数据默认缓存 10 分钟，再增加 0～2 分钟随机抖动，减少大量 Key 同时过期形成缓存雪崩。

不存在的商品使用特殊值缓存 30 秒，阻止相同无效 ID 持续穿透数据库。空值 TTL 必须较短，否则随后创建的数据可能仍被旧空值遮挡。

商品修改、上下架和删除完成后，不直接更新缓存，而是在数据库事务 `afterCommit` 阶段删除缓存。下一次查询重新从数据库构建，避免数据库回滚时缓存却已经提前发生变化。

商品缓存是数据库数据的可丢弃副本，所以 Redis 读取、写入或删除失败时记录警告并回源数据库，属于失败开放。这个可靠性策略和购物车不同：购物车目前没有数据库副本，Redis 故障必须返回 503。

## 22. Spring Security 与双 Token

Access Token 是 15 分钟有效的 HMAC-SHA256 JWT，包含用户 ID、用户名和角色。Resource Server 负责验证签名、issuer 和过期时间，并把角色映射为 `ROLE_USER` 或 `ROLE_ADMIN`。服务端不保存 Access Token，因此普通注销后它仍会存活到过期；这是短期无状态 Token 的取舍。

Refresh Token 是 256 位安全随机数，有效期 30 天。客户端拿到原文，数据库只保存 SHA-256 哈希。刷新时旧 Token 标记为已使用并生成替代 Token；再次提交已使用的旧 Token 会被视为疑似泄漏，整个 Token Family 都被撤销。

密码通过 Spring `PasswordEncoder` 使用带算法标识的 BCrypt 哈希保存。登录错误统一返回“账号或密码错误”，避免向攻击者泄露具体是账号不存在还是密码错误。

测试 profile 默认放行旧业务接口，保证现有模块测试聚焦业务；安全测试显式关闭放行配置，真实验证 401、403、JWT、刷新轮换和角色权限。生产配置中商品读取及认证接口公开，用户、订单、支付和管理写接口受到角色保护。

当前签名使用共享 HMAC 密钥，适合单体学习项目。生产环境必须通过 `AUTH_JWT_SECRET` 注入至少 32 字节的随机密钥，不能使用仓库中的开发默认值。未来演进到独立 SSO 时应改用非对称密钥和公开 JWK，让资源服务只持有公钥。

## 23. 把认证身份接入业务

登录成功只是认证的开始。用户业务接口统一使用 `/api/me/**`，由 `CurrentUser` 从 Spring Security 的 `SecurityContext` 中读取已经验签的 JWT `sub`，得到当前用户 ID。客户端不再提交 `userId`，避免它通过篡改参数冒充其他用户。

只隐藏 `userId` 还不够，Service 和 Repository 查询资源时也必须同时匹配资源 ID 与当前用户 ID，例如：

```text
findByIdAndUserId(orderId, currentUserId)
findByPaymentNoAndOrderUserId(paymentNo, currentUserId)
```

查不到时统一返回 404，不向调用者透露该资源是否属于其他用户。这防止了水平越权（IDOR）：攻击者即使猜到递增 ID 或支付号，也不能读取、取消或重试别人的资源。

管理员接口仍保留 `/api/users/{userId}/**`、`/api/orders/**` 和 `/api/payments/**`，并由 Spring Security 限制为 `ADMIN`。支付成功/失败模拟接口不暴露在 `/api/me/payments` 下，因为真实系统中支付状态应由经过签名验证的渠道回调推进，不能由普通用户自行声明成功。

## 24. Transactional Outbox 与支付超时架构

支付超时事件不再在事务 `afterCommit` 回调中直接发送。创建支付时，`payment_order` 和 `outbox_event` 在同一个 PostgreSQL 本地事务中保存，从而保证“业务数据存在”与“待发送事件存在”不会只成功一个。

后台 `OutboxRelay` 分批抢占 `PENDING`、到期的 `RETRY`，以及租约过期的 `PROCESSING` 事件。抢占使用悲观锁，随后立即提交事务并释放数据库连接；RabbitMQ 网络调用发生在事务之外。投递端等待 Publisher Confirm，并检查消息是否因为无法路由而 Return。成功标记 `SENT`，失败记录原因并指数退避，超过最大次数进入 `FAILED`。

```text
payment_order + outbox_event（同一本地事务）
→ Relay 抢占与租约
→ RabbitMQ Confirm/Return
→ SENT 或 RETRY/FAILED
→ TTL + DLX
→ 超时消费者
→ 支付状态检查、关单、库存释放
```

Relay 在 RabbitMQ ACK 后、更新 `SENT` 前宕机会造成重复消息，因此该架构提供的是至少一次投递。消费者通过支付状态机实现幂等：支付已成功、已关闭或尚未到期时不会重复关单。数据库超时扫描仍作为最终补偿，用于应对 Outbox 长期 `FAILED`、RabbitMQ 长时间不可用等异常情况。

详细字段、状态机、配置和失败窗口见 [RabbitMQ 专题](RABBITMQ_KNOWLEDGE.md)。

## 25. 可观测性：指标、日志与 Trace ID

可观测性不是简单地“装一个 Grafana”。Grafana 负责展示，真正的数据来自不同后端：Prometheus 保存数值型时间序列，Loki 保存日志，Alloy 负责采集日志。应用通过 Actuator 和 Micrometer 暴露标准指标，同时记录带 Trace ID 的滚动文件日志。

指标适合回答“发生了多少、是否变慢、是否持续恶化”，例如请求速率、P95 延迟、JVM 堆内存和 Outbox 积压；日志适合回答“这一次为什么失败”，保留具体异常和业务上下文。排障通常先从仪表盘发现异常，再用响应中的 `X-Trace-Id` 定位同一次请求的日志。

Counter 只递增，适合累计成功/失败次数，查询时通常使用 `rate` 或 `increase`；Gauge 可升可降，适合当前队列积压和连接数；Timer 同时描述调用次数与耗时分布。P95 表示 95% 的请求耗时不超过该值，比平均值更容易暴露长尾延迟。

标签能切分指标和日志，但不能无限添加。状态、结果、日志级别属于低基数标签；traceId、用户 ID、订单号属于高基数值，只应放在日志正文，而不应成为标签。否则时间序列和日志流数量会快速膨胀，显著增加内存、索引和查询成本。

当前链路和运行方式详见 [可观测性专题](OBSERVABILITY.md)。它是单机学习环境，生产中还要加入告警规则、集中式 Secret、鉴权、长期存储和完整的 OpenTelemetry/Tempo 分布式追踪。

## 26. 告警闭环

Prometheus 不只存储指标，还按 `evaluation_interval` 周期执行告警规则。表达式满足条件后先进入 Pending，持续达到 `for` 后才进入 Firing。恢复正常后进入 Resolved。`for` 可以过滤短暂毛刺，但设置太长也会推迟真正故障的发现。

Alertmanager 接收 Firing/Resolved 告警，负责去重、分组、静默、抑制和路由。例如同一服务的数十条实例告警可以合成一条通知；当 critical 根因告警出现时，可以抑制由它引发的 warning 告警。Grafana 负责统一查看这些状态，而真实通知渠道属于 Alertmanager 路由配置。

告警应尽量描述用户或业务影响，而不是“CPU 一高就报警”。当前项目首先监控可用性、5xx、P95、Outbox 永久失败和退款积压。这些信号比孤立的资源波动更接近真实故障。

## 27. 退款与部分退款模型

创建退款单要求支付状态为 `SUCCESS`。请求不传 `amount` 时退还全部剩余额度，传入金额时创建部分退款；金额必须大于零且最多保留两位小数。服务端始终根据支付金额和历史退款重新计算剩余额度，不能相信客户端声明的“可退金额”。退款请求使用 `Idempotency-Key`，相同 Key、订单和金额返回原退款单，避免客户端重试造成重复退款。

```text
订单 PAID / COMPLETED
→ 同一事务创建 RefundOrder(PENDING) + OutboxEvent
→ 订单 REFUNDING
→ Outbox Relay → RabbitMQ → 退款消费者
→ 渠道接受请求：RefundOrder PROCESSING
→ 渠道成功回调：RefundOrder SUCCESS
   ├── 累计金额 = 实付金额 → 订单 REFUNDED
   └── 累计金额 < 实付金额 → 订单恢复 PAID / COMPLETED
→ 渠道失败回调：RefundOrder FAILED → 订单恢复 PAID / COMPLETED
```

一笔支付现在可以关联多张退款单。退款额度计算为：

```text
剩余可退金额 = 实付金额 - Σ(PENDING、PROCESSING、SUCCESS 的退款金额)
```

`FAILED` 不占用额度。当前为了保持订单状态明确，同一支付同一时刻只允许一张活动退款单；前一张成功或失败后才能继续申请。创建时对 `payment_order` 加数据库悲观写锁，因此两个并发请求必须串行完成额度检查和退款单写入，不会同时读取相同余额后造成超退。这是“锁定额度所属的聚合根”，而不是只依赖退款单自己的乐观锁。

退款单保存 `orderStatusBeforeRefund`，因为部分退款成功或退款失败时必须知道订单原来是已支付还是已完成。只有累计成功退款等于实付金额，订单才进入 `REFUNDED`。渠道通知使用全局唯一 `notificationId` 去重，同一通知重复到达只返回当前结果；通知 ID 被其他退款占用则产生冲突。实体使用 `@Version` 防止两个不同回调同时推进同一退款单。

用户接口为 `/api/me/refunds`，用户 ID 取自验签后的 Access Token，并通过关联的支付单和订单检查所有权。`GET /api/me/refunds?orderId=...` 可以查看一个订单的全部退款记录。管理接口 `/api/refunds` 受 ADMIN 权限保护；`mock-success` 和 `mock-failure` 仅用于模拟真实支付渠道的异步退款回调。

退款创建与 `refund.requested` Outbox 事件处于同一个数据库事务，保证退款单和待发送事件不会只成功一个。Relay 等到 RabbitMQ Confirm 后才标记事件已发送。消费者通过 `RefundGateway` 调用渠道；渠道抛出异常时异常继续交给 Spring AMQP，经过有限重试仍失败的消息进入退款死信队列。

重复投递可能发生，因此消费者对 `PENDING`、`PROCESSING` 都可以使用退款号作为渠道幂等标识重新提交；终态退款直接跳过。先调用渠道、后记录 `PROCESSING` 可以避免数据库先提交后进程宕机导致请求永远未发送。两者之间宕机可能重复调用渠道，所以真实 `RefundGateway` 必须以退款号实现幂等。

当前模拟渠道只记录“已接受退款”，真实退款结果仍由异步成功/失败回调推进。V11 使用 Java Flyway Migration 动态查找并移除旧的 `payment_id` 唯一约束，这是因为 PostgreSQL 和 H2 自动生成的约束名称不同；之后重新建立普通外键和查询索引。当前仍未实现按订单项退款、运费分摊和人工死信重放。

## 28. Elasticsearch 查询副本与可靠同步

商品搜索采用“事实源 + 查询副本”结构。PostgreSQL 保存可信的 Product、SKU、价格和上下架状态；Elasticsearch 保存为搜索优化的扁平文档，把一个商品及其多个 SKU 名称、编码和价格范围冗余到同一份 `ProductSearchDocument` 中。冗余减少搜索时的跨表查询，但也意味着它必须通过同步机制维护。

```text
Product / SKU 事务
├── 更新 PostgreSQL 业务数据
└── 写 Product Outbox Event
    → Outbox Relay
    → RabbitMQ product-index queue
    → ProductIndexConsumer
    → 读取 PostgreSQL 最新聚合
    → Elasticsearch upsert / delete
```

业务数据和 Outbox 事件使用同一本地事务，解决“数据库成功、同步任务丢失”的双写问题。RabbitMQ 和消费者允许重复投递：UPSERT 总是按商品 ID 读取数据库最新状态并覆盖同 ID 文档，DELETE 删除不存在的文档也不会产生额外副作用，因此操作具备幂等性。失败由 RabbitMQ 重试并最终进入 DLQ，全量重建作为最终修复手段。

搜索使用 `multi_match`，名称权重最高，其次是 SKU 名称/编码和描述；只有 `ON_SALE` 文档可见。价格筛选判断查询区间是否与商品所有 SKU 构成的最低价—最高价区间相交。排序支持相关性、价格升降序和更新时间，命中片段通过 highlight 返回给前端。

ES 更新是最终一致的，搜索页短暂看到旧数据是允许的，但下单绝不能直接信任搜索文档，必须回到 PostgreSQL 和库存模块重新校验 SKU、价格、状态与库存。索引同步成功/失败 Counter、日志、Prometheus 告警和管理员全量重建共同组成运维闭环。

当前先使用标准分词器学习完整链路。中文搜索通常需要专用分析器、词典和停用词；分析器属于 Mapping 的一部分，修改时通常创建带版本号的新索引、重建数据，再原子切换 alias，而不是直接修改线上旧索引。

## 29. 对象存储与预签名直传

对象存储用 Bucket 组织对象，用 Object Key 唯一定位内容。它不像本地文件系统那样依赖目录层级，`products/1/uuid.webp` 本质上只是带前缀的 Key。数据库只保存业务关系和对象元数据，真正的图片字节保存在 MinIO。

商品图片使用 `PENDING → READY` 两阶段模型。创建上传任务时先生成服务端控制的 Object Key 和短期 PUT URL；客户端直传后必须调用确认接口。确认阶段通过 `statObject` 比对实际 Content-Type 和大小，校验通过后图片才对查询接口可见。这避免了数据库已经显示图片、对象却从未上传的问题。

预签名 URL 把“允许谁、在多长时间内、对哪个对象执行什么动作”编码进签名。客户端可以直接访问私有 Bucket，但拿不到长期 Access Key。URL 泄漏后在有效期内仍可能被使用，因此有效期要短、Key 要不可猜、Bucket 保持私有，并用 HTTPS 防止传输中泄漏。

PostgreSQL 和 MinIO 无法参加同一个普通数据库事务，所以删除、上传确认仍可能产生不一致。当前通过明确状态、幂等删除和失败时清理降低风险；后续需要定时清除过期 PENDING、扫描无数据库引用的孤儿对象，并为删除任务增加重试。详细接口和运行方式见 [MinIO 专题](MINIO.md)。
