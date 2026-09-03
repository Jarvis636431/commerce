# 可观测性与日志系统

本项目使用 Spring Boot Actuator、Micrometer、Prometheus、Loki、Grafana Alloy 和 Grafana，形成适合本地学习的指标与日志链路。

```text
Commerce ── /actuator/prometheus ← Prometheus ──┐
                                                ├→ Grafana
Commerce ── logs/commerce.log → Alloy → Loki ──┘
```

Prometheus 采用拉模型：它每 15 秒访问应用的指标端点并保存时间序列。Alloy 持续读取滚动日志文件，为日志附加 `application`、`environment` 和 `level` 标签后推送到 Loki。Grafana 本身不存储数据，只查询 Prometheus 和 Loki 并展示结果。

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
- Loki 健康检查：`http://localhost:3100/ready`
- Alloy 调试界面：`http://localhost:12345`
- 应用原始指标：`http://localhost:8080/actuator/prometheus`

Grafana 会自动配置 Prometheus、Loki 两个数据源，并加载 `Commerce Overview` 仪表盘，无需手工导入。

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
