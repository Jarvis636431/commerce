# Elasticsearch 商品搜索

## 定位

Elasticsearch 是面向搜索和分析的分布式文档数据库，不是 Java 专属软件。本项目通过 Spring Data Elasticsearch 访问它：

```text
PostgreSQL Product / SKU（事实源）
→ 同一本地事务写入 Product Outbox Event
→ Outbox Relay → RabbitMQ 商品索引队列
→ 消费者读取数据库最新状态并构建 ProductSearchDocument
→ Elasticsearch commerce-products 索引（查询副本）
→ GET /api/search/products?q=关键字
```

PostgreSQL 决定商品是否真实存在、价格和状态是否有效；Elasticsearch 文档可以删除并从数据库重新构建。下单时仍必须读取数据库中的 SKU 和库存，不能相信搜索结果，因为索引可能存在短暂延迟。

## 为什么搜索不用数据库 LIKE

数据库 `LIKE '%java%'` 能完成简单模糊匹配，但不擅长相关性评分、分词、多字段权重、高亮、同义词和复杂搜索聚合。Elasticsearch 使用倒排索引，把“词 → 包含它的文档”提前建立映射，查询时不必逐行扫描商品正文。

当前搜索给商品名称更高权重：

```text
name^3 > skuNames^2 / skuCodes^2 > description
```

搜索接口只返回 `ON_SALE` 商品。文档还冗余保存 SKU 名称、编码、最低价和最高价，查询时无需像关系数据库一样连接 Product 与 SKU。

## 本地启动

只启动 Elasticsearch：

```bash
make es-up
make es-status
```

启动全部基础设施和应用：

```bash
make infra-up
make run
```

检查服务：

```bash
curl http://localhost:9200
curl http://localhost:9200/_cluster/health
```

本地使用单节点 Elasticsearch 9.4.5、512 MB JVM 堆，并关闭安全认证，只适合开发环境。

## 使用接口

首次接入或者需要修复数据时，由管理员执行全量重建：

```bash
curl -X POST http://localhost:8080/api/admin/search/products/rebuild \
  -H 'Authorization: Bearer ADMIN_ACCESS_TOKEN'
```

搜索已上架商品：

```bash
curl 'http://localhost:8080/api/search/products?q=Java&page=0&size=20'
```

价格过滤、排序和高亮：

```bash
curl 'http://localhost:8080/api/search/products?q=Java&minPrice=50&maxPrice=200&sort=PRICE_ASC&page=0&size=20'
```

`sort` 支持 `RELEVANCE`、`PRICE_ASC`、`PRICE_DESC` 和 `NEWEST`。价格条件采用区间相交语义：只要商品的 SKU 价格范围与查询范围有交集就会命中。响应中的 `score` 是相关性分数，`highlights` 返回名称、描述和 SKU 名称里的匹配片段。

可以直接查看索引内容：

```bash
curl 'http://localhost:9200/commerce-products/_search?pretty'
```

## 可靠增量同步

商品或 SKU 创建、修改、上下架、删除时，业务数据和 `PRODUCT_INDEX_UPSERT` / `PRODUCT_INDEX_DELETE` Outbox 事件在同一个 PostgreSQL 事务中提交。这样不会出现“商品成功但同步任务没有落库”的事务空隙。

Relay 获得 RabbitMQ Publisher Confirm 后标记事件已发送；消费者失败会按监听器策略重试，耗尽后进入搜索 DLQ。消息只携带商品 ID，消费者总是重新读取数据库最新状态，因此重复 UPSERT 是幂等的，删除不存在的文档也是幂等的。多条快速变更最终都会收敛到 PostgreSQL 的最新状态。

这仍属于最终一致性：数据库提交后到消费者完成前，搜索结果允许短暂陈旧。指标 `commerce_search_index_total{outcome="success|failure"}`、结构化日志和 Prometheus 告警用于发现失败；管理员全量重建仍是批量修复和灾难恢复手段。

当前使用 Elasticsearch 标准分词器，英文和数字检索可以直接学习验证。中文分词插件与 Elasticsearch 版本强绑定，后续再引入 IK 或其他中文分析器，并通过新索引 + alias 切换完成 Mapping 迁移。

## 测试环境

测试 profile 设置：

```yaml
commerce.search.enabled: false
spring.data.elasticsearch.repositories.enabled: false
```

业务集成测试不需要启动 ES，使用 `NoOpProductSearchIndexer`。搜索文档转换与消息消费者通过独立单元测试验证，真正的 ES Mapping 和查询还应增加 Testcontainers 集成测试。
