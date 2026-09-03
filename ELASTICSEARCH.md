# Elasticsearch 商品搜索

## 定位

Elasticsearch 是面向搜索和分析的分布式文档数据库，不是 Java 专属软件。本项目通过 Spring Data Elasticsearch 访问它：

```text
PostgreSQL Product / SKU（事实源）
→ 事务提交后构建 ProductSearchDocument
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

可以直接查看索引内容：

```bash
curl 'http://localhost:9200/commerce-products/_search?pretty'
```

## 当前同步策略

商品或 SKU 创建、修改、上下架、删除时，应用注册事务 `afterCommit` 回调。只有 PostgreSQL 成功提交后才更新 ES，避免数据库回滚而索引提前改变。ES 更新失败只记录错误，不把已经成功的业务事务伪装成失败；管理员可以用全量重建恢复。

这个方案仍有失败窗口：数据库提交后、回调执行前进程宕机，或者 ES 长时间不可用，可能造成漏同步。因此它是接入搜索的第一阶段，不是最终可靠方案。下一步应写入 Product Outbox 事件，通过 RabbitMQ 重试同步，并保留全量重建用于最终修复。

## 测试环境

测试 profile 设置：

```yaml
commerce.search.enabled: false
spring.data.elasticsearch.repositories.enabled: false
```

业务集成测试不需要启动 ES，使用 `NoOpProductSearchIndexer`。搜索文档转换通过独立单元测试验证，真正的 ES Mapping 和查询还应增加 Testcontainers 集成测试。
