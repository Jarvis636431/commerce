# 可观测性与日志系统

本项目使用 Spring Boot Actuator、Micrometer、Prometheus、Loki、Grafana Alloy 和 Grafana，形成适合本地学习的指标与日志链路。

```text
Commerce ── /actuator/prometheus ← Prometheus ──┐
                                                ├→ Grafana
Commerce ── logs/commerce.log → Alloy → Loki ──┘
```

Prometheus 采用拉模型：它每 15 秒访问应用的指标端点并保存时间序列。Alloy 持续读取滚动日志文件，为日志附加 `application`、`environment` 和 `level` 标签后推送到 Loki。Grafana 本身不存储数据，只查询 Prometheus 和 Loki 并展示结果。

告警链路是在指标链路上继续延伸：

```text
Prometheus 定期计算告警规则
→ 满足阈值并持续达到 for 时间
→ Alertmanager 接收告警
→ 分组、抑制、静默和路由
→ Grafana 统一查看（生产中再连接邮件、Webhook、飞书等通知渠道）
```

## 这是不是 Java 生态

这套系统不是 Java 专属方案，而是语言无关的可观测性基础设施。Java 应用只是数据生产者之一：

| 组件 | 所属范围 | 主要职责 |
| --- | --- | --- |
| Spring Boot Actuator | Spring/Java 生态 | 暴露健康状态和应用指标端点 |
| Micrometer | JVM 生态 | 用统一 API 记录 Counter、Gauge、Timer 等指标 |
| SLF4J、Logback | Java 生态 | 生成并输出应用日志 |
| Prometheus | 通用基础设施 | 抓取、保存和查询数值型时间序列 |
| Alloy | 通用基础设施 | 在数据源附近采集、处理和转发可观测性数据 |
| Loki | 通用基础设施 | 集中保存和查询日志 |
| Grafana | 通用基础设施 | 查询数据源并制作仪表盘和告警 |

可以用六句话记住它们：

```text
Actuator：把 Spring Boot 的运行信息暴露出来
Micrometer：用统一方式产生指标
Prometheus：存指标
Alloy：搬运数据
Loki：存日志
Grafana：展示数据
```

## 为什么拆成多个服务

拆分依据不是技术名称，而是数据模型、处理阶段和负载特征。

### 指标和日志的数据模型不同

指标是带时间和标签的数字，例如请求总数、P95 延迟、内存使用量。它的数据量规律，主要用于数学计算、聚合和趋势分析。Prometheus 因此使用时间序列模型并提供 PromQL。

日志是一条条大小不一的文本事件，例如异常堆栈、订单号和失败原因。它的数据量更大，主要用于过滤、搜索和还原具体现场。Loki 因此采用日志流模型并提供 LogQL。

```text
指标回答：系统是否出问题、影响多大、趋势如何？
日志回答：某一次请求具体为什么失败？
```

如果强行交给一个存储引擎处理，内部仍要实现两套不同的数据结构和查询方式。

### 采集端和存储端的位置不同

Alloy 要部署在日志产生的位置附近，因为只有它容易访问本机文件、容器输出和系统日志。它负责记录文件读取位置、解析字段、添加标签、缓冲和失败重试。

Loki 位于中心位置，负责接收多台机器的日志、存储、索引和查询：

```text
服务器 A：应用 → Alloy ─┐
服务器 B：应用 → Alloy ─┼→ Loki
服务器 C：应用 → Alloy ─┘
```

让中心 Loki 直接读取所有机器的本地文件，会带来文件不可访问、容器地址变化、断网状态难维护等问题，因此“靠近数据源的 Agent + 中心存储”是更自然的边界。

### 展示层需要组合多个数据源

Grafana 的职责是 Dashboard、图表、查询交互、告警和权限。它可以同时查询 Prometheus、Loki、Tempo、PostgreSQL 等数据源，而不需要自己重新实现所有存储引擎。

这种拆分还带来三个工程收益：

- 指标、日志和展示可以分别扩容。例如日志每天 500 GB，而指标只有 5 GB，只扩容 Loki 即可。
- 日志洪峰拖慢 Loki 时，Prometheus 的指标采集仍可继续，形成故障隔离。
- 每层可以独立替换，例如用 VictoriaMetrics 替换 Prometheus，Grafana 和应用埋点不必一起重写。

商业平台看起来可能只有一个网站和一个 Agent，但它们内部通常仍包含指标、日志、Trace、查询、告警和存储等多个服务。它们是一体化的产品体验，并不意味着内部只有一个程序。

## 两条完整数据链路

### 指标链路

```text
业务代码 / Spring Boot
→ Micrometer 记录指标
→ Actuator 暴露 /actuator/prometheus
← Prometheus 每 15 秒拉取并保存
← Grafana 使用 PromQL 查询并画图
```

Prometheus 使用 Pull 拉取模型。应用只负责公开当前指标，不需要知道 Prometheus 部署在哪里。如果 Prometheus 短暂离线，业务请求仍可继续运行。

### 日志链路

```text
业务代码
→ SLF4J API
→ Logback 写入 logs/commerce.log
→ Alloy 读取新增内容并添加低基数标签
→ Loki 保存日志流
← Grafana 使用 LogQL 查询并展示
```

当前 Alloy 只读取新增内容，不需要每次重新发送整个文件。应用进行日志滚动后，它会继续追踪相应文件。

## 从异常到根因的排查链路

假设用户反馈创建订单失败：

1. 在 Grafana 指标面板查看错误率、请求速率和 P95 延迟，确认异常发生的时间和接口。
2. 查看 JVM、数据库连接池、Outbox 积压等指标，判断是资源问题还是异步链路问题。
3. 从 HTTP 响应取得 `X-Trace-Id`。
4. 在 Grafana Explore 查询 Loki：`{application="commerce"} |= "traceId=..."`。
5. 根据同一次请求的异常、业务参数和调用过程定位根因。

因此常见排障顺序是：

```text
指标发现异常 → Trace ID 缩小范围 → 日志解释原因 → 数据库/MQ 验证业务状态
```

当前 Trace ID 只能关联这个单体应用内部的日志。如果以后拆成微服务，还需要 OpenTelemetry 或 Micrometer Tracing 将上下文跨 HTTP、RabbitMQ 传播，并使用 Tempo、Jaeger、SkyWalking 等系统保存完整调用链。

## 启动

先启动完整基础设施，再启动本机上的 Spring Boot 应用：

```bash
make infra-up
make run
```

也可以只启动可观测性组件：

```bash
make observe-up
make observe-status
make observe-logs
```

访问地址：

- Grafana：`http://localhost:3000`，开发账号密码为 `admin/admin`
- Prometheus：`http://localhost:9090`
- Prometheus 告警规则：`http://localhost:9090/alerts`
- Alertmanager：`http://localhost:9093`
- Loki 健康检查：`http://localhost:3100/ready`
- Alloy 调试界面：`http://localhost:12345`
- 应用原始指标：`http://localhost:8080/actuator/prometheus`

Grafana 会自动配置 Prometheus、Loki 两个数据源，并加载 `Commerce Overview` 仪表盘，无需手工导入。

## 告警规则

告警规则保存在 `observability/prometheus/rules/commerce-alerts.yml`，当前包括：

| 告警 | 条件 | 级别 |
| --- | --- | --- |
| 应用不可用 | `up == 0` 持续 1 分钟 | critical |
| HTTP 错误率过高 | 5xx 比例超过 5% 持续 5 分钟 | warning |
| HTTP P95 延迟过高 | 超过 1 秒持续 5 分钟 | warning |
| Outbox 积压 | PENDING 超过 100 持续 5 分钟 | warning |
| Outbox 永久失败 | FAILED 大于 0 | critical |
| 支付超时处理失败 | 10 分钟内出现失败 | critical |
| 退款积压 | PENDING 与 PROCESSING 合计超过 20 持续 10 分钟 | warning |
| 退款失败 | 10 分钟内出现失败 | warning |

表达式负责定义“什么是不正常”，`for` 负责要求异常持续一段时间。瞬间抖动不会立即通知，可以减少误报。Alertmanager 不负责计算 PromQL，它负责接收已经触发的告警，然后分组、去重、静默和发送。

本地接收器故意不配置真实外部通知，触发的告警可以在 Prometheus、Alertmanager 和 Grafana 中查看。接入飞书或企业微信 Webhook 会向外部系统发送消息，应在明确提供地址后再配置，并通过环境变量或 Secret 管理 URL。

## 当前采集内容

Micrometer 自动提供 HTTP 请求、JVM、线程、进程、数据库连接池等指标。项目还增加了三个业务指标：

| 指标 | 类型 | 含义 |
| --- | --- | --- |
| `commerce_outbox_events{status}` | Gauge | 各状态 Outbox 事件数量 |
| `commerce_outbox_delivery_total{outcome}` | Counter | Outbox 投递成功和失败次数 |
| `commerce_payment_timeout_total{outcome}` | Counter | 支付超时消费的过期、跳过和失败次数 |

Java 中使用点号命名的 Meter，在 Prometheus 中会转换为下划线名称，Counter 还会带 `_total` 后缀。

每个 HTTP 请求都会获得 `X-Trace-Id`。客户端传入格式安全的值时会沿用，否则服务端生成新值；该值同时进入响应头、MDC 和日志。可以在 Grafana Explore 中按一次请求搜索：

```logql
{application="commerce"} |= "traceId=这里填响应头中的值"
```

## 常用查询

PromQL：

```promql
sum(rate(http_server_requests_seconds_count{application="commerce"}[5m]))

histogram_quantile(0.95,
  sum by (le) (rate(http_server_requests_seconds_bucket{application="commerce"}[5m]))
)

commerce_outbox_events{application="commerce", status="FAILED"}
```

PromQL 类似指标领域的 SQL，但查询对象不是业务记录，而是随时间变化的数值。常用结构包括：

```promql
# 标签过滤
metric_name{label="value"}

# Counter 最近五分钟的每秒增长速度
rate(metric_name[5m])

# Counter 最近一小时总增长量
increase(metric_name[1h])

# 按标签汇总
sum by (status) (rate(metric_name[5m]))
```

SQL 适合回答“哪些支付单失败了”，PromQL 适合回答“最近五分钟支付失败速度是否升高”。PromQL 用来发现趋势，日志用来解释现场，SQL 用来核查具体业务数据。

LogQL：

```logql
{application="commerce", level="ERROR"}

{application="commerce"} |= "Business rule conflict"
```

## 为什么 traceId 不做 Loki 标签

`application`、`environment`、`level` 的可能值很少，适合成为标签。`traceId`、用户 ID、订单号的值几乎每次请求都不同，作为 Prometheus 或 Loki 标签会制造大量时间序列/日志流，造成高基数问题。它们应保留在日志正文中，需要时通过文本过滤查找。

## 本地方案与生产方案的边界

当前 Loki 未启用认证，Grafana 使用默认开发密码，Prometheus 指标端点也被 Security 放行，只适用于本地学习。生产环境应把这些端口限制在内网，通过网关或监控网络保护指标端点，并使用 Secret 注入账号密码。

Outbox 状态 Gauge 在每次 Prometheus 抓取时查询数据库。当前数据规模下直观且足够；大规模系统应改成预聚合或低频缓存，避免监控反过来增加数据库压力。

目前的 `traceId` 用来关联应用日志，还不属于完整的分布式追踪。后续拆分服务时，可接入 Micrometer Tracing/OpenTelemetry 与 Tempo，并把同一个 trace context 跨 HTTP、RabbitMQ 继续传播。
