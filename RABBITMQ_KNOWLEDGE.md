# RabbitMQ 与 RocketMQ 学习笔记

## 1. 为什么单体项目也能使用 MQ

MQ 解决的是同步调用不擅长的问题，并不要求系统必须拆成微服务。生产者和消费者可以位于同一个 Spring Boot 应用中，RabbitMQ Broker 则作为独立基础设施运行。

适合 MQ 的任务通常具有一个或多个特征：调用方不需要立即得到结果、流量需要削峰、失败需要重试、任务需要延迟执行，或者一个业务事件需要触发多个相互独立的后续动作。

当前项目中，下单和库存预占继续采用同步调用及数据库事务，因为客户端必须立刻知道是否成功；支付超时关单可以异步执行，因为它发生在未来，并且允许短暂延迟。

## 2. RabbitMQ 核心模型

```text
Producer
   │ publish(exchange, routingKey, message)
   ▼
Exchange ──根据类型和 Binding 路由──▶ Queue
                                         │
                                         ▼
                                      Consumer
                                         │
                                   ACK / NACK / Reject
```

- Producer：生产消息的应用代码。
- Message：消息体以及 headers、contentType、messageId 等元数据。
- Exchange：接收生产者消息并决定路由，不负责长期保存消息。
- Queue：保存等待消费的消息。
- Binding：Exchange 与 Queue 之间的路由规则。
- Routing Key：生产者发送时附带的路由键。
- Consumer：订阅队列并执行具体业务。
- Virtual Host：RabbitMQ 内的逻辑隔离空间，类似一个独立命名空间。
- Connection：应用与 Broker 的 TCP 连接，创建成本较高。
- Channel：复用 Connection 的轻量逻辑连接，绝大多数发布和消费操作通过它完成。

常见 Exchange：

| 类型 | 路由规则 | 典型用途 |
|---|---|---|
| direct | Routing Key 完全匹配 | 明确命令、单一业务类型 |
| topic | 按 `*`、`#` 通配模式匹配 | 业务事件、多维订阅 |
| fanout | 忽略 Routing Key，广播到所有绑定队列 | 广播通知 |
| headers | 根据消息 Header 匹配 | 少量特殊路由场景 |

Exchange 和 Queue 解耦了发送方与消费方。订单模块只发布 `order.paid`，积分、物流和通知模块可以分别绑定自己的队列；增加消费者时不需要修改订单代码。

## 3. 消息可靠性的四段链路

“消息发出去了”不是一个原子事实，需要分别处理四段风险：

```text
本地数据库事务 → Producer → RabbitMQ → Queue → Consumer 业务事务
```

### 3.1 数据库与发送消息

数据库提交成功、应用却在发送前崩溃，会造成业务数据存在但消息丢失。Publisher Confirm 只能证明 Broker 收到了消息，不能把数据库事务和 RabbitMQ 变成同一个事务。

生产级方案通常使用 Transactional Outbox：业务数据与 outbox 事件写入同一个数据库事务，再由后台任务可靠投递 outbox，确认后标记已发送。

### 3.2 Producer 与 Broker

- Publisher Confirm：Broker 对成功接收的消息进行确认。
- Publisher Return：当消息到达 Exchange 但无法路由到任何 Queue 时退回生产者。
- `mandatory=true`：要求不可路由消息触发 Return，而不是静默丢弃。
- durable Exchange/Queue 与持久消息：降低 Broker 重启造成的丢失风险。

当前配置已经开启 correlated confirm、publisher returns 和 mandatory，后续实现生产者时还要注册 Confirm/Return 回调并记录失败投递。

### 3.3 Broker 与 Consumer

手动 ACK 表示消费者完成数据库事务后才确认：

- `basicAck`：处理成功，可以删除消息。
- `basicNack/basicReject + requeue=true`：重新入队；持续失败时可能形成无限循环。
- `requeue=false`：拒绝消息；配置 DLX 后进入死信队列。

消费者可能在数据库提交后、ACK 前崩溃，Broker 会再次投递，因此实际语义通常是“至少一次”，业务消费者必须幂等。

### 3.4 消费幂等

消息应携带稳定的 `eventId`。消费者可建立消费记录表并对 `(consumer_name, event_id)` 添加唯一约束，在同一个数据库事务中完成“记录已处理”和业务修改。仅用 Redis 锁不能完整替代持久化幂等记录，因为锁可能过期或 Redis 数据可能丢失。

## 4. TTL、死信和支付超时

RabbitMQ 消息成为死信的常见原因包括：消息过期、队列超过长度限制，以及消费者拒绝且不重新入队。死信交换机（DLX）会根据配置把死信重新路由。

当前项目预先声明了以下拓扑：

```text
commerce.payment.command
  └─ payment.timeout.schedule
       └─ commerce.payment.timeout.delay（TTL = 支付超时时间，无消费者）
            └─ 消息过期成为死信
                 └─ commerce.payment.event
                      └─ payment.timeout.due
                           └─ commerce.payment.timeout（未来消费者监听）
                                └─ 多次失败且拒绝
                                     └─ commerce.payment.dlx
                                          └─ commerce.payment.dead-letter
```

创建支付后将 `paymentNo` 和 `eventId` 发到延迟队列。消息到期进入消费队列，消费者重新读取数据库：只有支付仍为 `PENDING` 且确实到期时才关闭支付、取消订单并释放库存；已经成功或关闭的支付直接 ACK。这种“收到消息后再检查当前状态”的方式可以处理消息重复和支付成功/超时消息竞争。

RabbitMQ 官方说明，经典队列中的过期消息通常要到达队首才会被移除或死信。因此，不同延迟时间共用一条队列可能发生队首阻塞。当前项目所有支付统一 15 分钟，TTL + DLX 足够用于学习；大量任意精度定时任务应考虑专门延迟方案、时间轮或 RocketMQ 延迟消息。

参考：[RabbitMQ TTL](https://www.rabbitmq.com/docs/ttl)、[Dead Letter Exchanges](https://www.rabbitmq.com/docs/dlx)、[Publisher Confirms](https://www.rabbitmq.com/docs/confirms)。

## 5. RabbitMQ 与 RocketMQ 的概念映射

| RabbitMQ | RocketMQ | 含义差异 |
|---|---|---|
| Exchange + Routing Key | Topic + Tag/属性过滤 | RabbitMQ 把路由作为独立层，组合更灵活 |
| Queue | MessageQueue | RabbitMQ Queue 是消费载体；RocketMQ MessageQueue 是 Topic 的分区 |
| Consumer | Consumer Group | RocketMQ 以消费组协调负载和消费进度 |
| ACK/NACK | 消费成功/失败与重试 | 都要按至少一次投递设计幂等 |
| TTL + DLX | Delay Message | RocketMQ 将延迟消息作为核心消息类型 |
| 应用 Outbox/事务协调 | Transaction Message | RocketMQ 原生提供半消息和事务回查机制 |
| Dead-letter Queue | Dead-letter Topic/重试机制 | 都用于隔离超过重试上限的异常消息 |

注意：概念只能近似映射，不能把 RabbitMQ Queue 简单等同于 RocketMQ Topic。RabbitMQ 更强调 Exchange 到 Queue 的路由；RocketMQ 更强调 Topic 的分区、消费组和消费位点。

## 6. 两者详细对比

| 维度 | RabbitMQ | RocketMQ |
|---|---|---|
| 协议与定位 | AMQP 生态成熟，通用消息代理 | 面向大规模业务消息和事件流转 |
| 路由 | direct/topic/fanout/headers Exchange，灵活 | Topic、Tag 和属性过滤，结构更直接 |
| 延迟消息 | 常用 TTL + DLX；也有插件等方案 | 原生延迟/定时消息，更适合大量定时业务 |
| 事务消息 | 通常用 Outbox 或应用级方案 | 原生 half message、二阶段确认和事务回查 |
| 顺序消息 | 单队列单活跃消费者等方式保证局部顺序 | 原生 FIFO 消息组，仍是局部顺序 |
| 吞吐与堆积 | 适合多数业务消息，复杂路由突出 | 通常更擅长高吞吐和大量堆积 |
| 运维体验 | 管理 UI 直观，本地搭建简单 | 组件与概念相对更多，集群学习曲线更陡 |
| Spring 学习 | Spring AMQP 抽象成熟 | RocketMQ Spring 集成可用，API 体系不同 |
| 适合当前项目 | 非常适合学习完整可靠性链路 | 适合后续用同一场景做进阶对照 |

RocketMQ 官方将普通、FIFO、延迟和事务消息作为不同消息类型。事务消息通过 half message、提交/回滚以及事务状态回查，解决本地事务与消息发送之间的最终一致性，但下游消费仍然需要重试与幂等。参考：[RocketMQ 消息类型](https://rocketmq.apache.org/docs/domainModel/05message/)、[延迟消息](https://rocketmq.apache.org/docs/featureBehavior/02delaymessage/)、[事务消息](https://rocketmq.apache.org/docs/featureBehavior/04transactionmessage/)。

## 7. 当前项目为什么先选 RabbitMQ

目前目标是理解 MQ 通用原理，而不是立即追求极限吞吐。RabbitMQ 的 Exchange、Binding、Queue、ACK、Confirm、Return、Retry 和 DLX 可以把消息生命周期拆得很清楚，管理界面也便于观察每一步。

建议路线：

1. 启动 RabbitMQ，观察 Spring 自动声明的拓扑。
2. 实现最小生产者和消费者，先发送普通测试消息。
3. 创建支付后发送超时消息，并让消费者调用现有幂等状态检查。
4. 保留数据库定时扫描作为补偿兜底。
5. 增加发布确认、消费重试、死信告警和消费幂等表。
6. 引入 Outbox，解决本地事务提交后消息未发送的问题。
7. 最后用 RocketMQ 重做同一场景，对比原生延迟消息和事务消息。

## 8. 本项目的启动和观察命令

只启动 RabbitMQ：

```bash
make rabbit-up
make rabbit-status
```

启动 PostgreSQL、Redis 和 RabbitMQ：

```bash
make infra-up
```

查看 RabbitMQ 日志：

```bash
make rabbit-logs
```

管理界面：`http://localhost:15672`，开发环境账号和密码均为 `commerce`。AMQP 连接端口为 `5672`。

停止 RabbitMQ：

```bash
make rabbit-down
```

应用支持以下环境变量：

```text
RABBITMQ_HOST
RABBITMQ_PORT
RABBITMQ_USERNAME
RABBITMQ_PASSWORD
```

不要在生产环境使用仓库中的默认账号密码。生产环境还应使用独立 vhost、最小权限用户、TLS、监控告警和高可用队列策略。
