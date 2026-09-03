# MinIO 与商品图片

## 为什么使用对象存储

图片、视频和压缩包属于非结构化大对象。把二进制直接放进 PostgreSQL 会放大数据库、备份和主从复制压力；保存到应用本地磁盘又会让多实例之间的数据不一致。本项目因此采用：

```text
PostgreSQL：图片 ID、商品 ID、Object Key、MIME、大小、ETag、状态
MinIO：真正的图片二进制
```

MinIO 提供兼容 S3 的对象 API。应用通过 MinIO Java SDK 访问它，以后迁移到 S3、OSS 或 COS 时可以保留相同的对象存储设计。

> 版本说明：MinIO 社区服务端仓库已于 2026 年归档，当前 Compose 固定到最后一批安全修复版本，适合本地学习 S3 协议和对象存储模型。生产选型需要重新评估仍在维护的 S3 兼容实现或云厂商托管服务。Java SDK 仍独立发布。

## 为什么使用预签名 URL

客户端先请求 Java 服务创建上传任务，服务端完成身份、商品和文件声明校验，然后签发只有 15 分钟有效的 PUT URL。客户端直接把二进制上传到 MinIO，Java 服务不转发文件内容，因此不会长期占用应用线程、连接和内存。

```text
1. POST /api/products/{productId}/images/uploads
2. 客户端用返回的 URL 和 Content-Type 直接 PUT 图片
3. POST /api/products/{productId}/images/{imageId}/complete
4. 服务端 stat 对象，核对 MIME 和字节数
5. PENDING → READY
6. GET 图片列表时返回短期下载 URL
```

只有 `READY` 图片会出现在查询结果中。对象 Key 由服务端生成，格式为 `products/{productId}/{UUID}.{extension}`，客户端不能指定路径，从源头避免路径穿越和对象覆盖。

商品图片支持 `primary` 和 `sortOrder`。设置主图时会对 Product 加悲观锁，把同一商品的其他主图标记清除，从而保证并发请求串行修改。商品详情与 ES 搜索文档保存稳定的应用内 `/content` 地址；访问该地址时应用生成新的 MinIO 下载签名并返回 302，避免把会过期的签名 URL 放进 Redis 或 ES。

未在一小时内完成确认的 `PENDING` 图片由定时任务扫描。图片记录删除和 `storage.objects.delete` Outbox 事件处于同一本地事务，RabbitMQ 消费者异步、幂等删除 MinIO 对象。删除单张图片或整个商品也复用同一条链路，失败消息最终进入 `commerce.storage.dead-letter`。

## 本地运行

MinIO 9 使用 OkHttp 5。它的通用 `okhttp` artifact 只包含 Kotlin Multiplatform 元数据，Maven 不会像 Gradle 一样自动选择 JVM variant，因此项目显式声明了同版本的 `okhttp-jvm`。如果缺少它，编译时会看到“找不到 `okhttp3.HttpUrl`”错误。

启动 MinIO：

```bash
make minio-up
make minio-status
```

- S3 API：`http://localhost:9000`
- 管理控制台：`http://localhost:9001`
- 本地账号：`commerce`
- 本地密码：`commerce-minio-secret`
- Bucket：`commerce-product-images`（第一次访问时由应用创建）

## 调用示例

先获得上传任务：

```bash
curl -X POST http://localhost:8080/api/products/1/images/uploads \
  -H 'Authorization: Bearer ADMIN_ACCESS_TOKEN' \
  -H 'Content-Type: application/json' \
  -d '{"filename":"front.webp","contentType":"image/webp","size":12345}'
```

使用响应里的 `uploadUrl`，并保持 Content-Type 与声明一致：

```bash
curl -X PUT 'PRESIGNED_UPLOAD_URL' \
  -H 'Content-Type: image/webp' \
  --data-binary @front.webp
```

确认上传：

```bash
curl -X POST http://localhost:8080/api/products/1/images/IMAGE_ID/complete \
  -H 'Authorization: Bearer ADMIN_ACCESS_TOKEN'
```

公开查询已就绪图片：

```bash
curl http://localhost:8080/api/products/1/images
```

设置主图和顺序：

```bash
curl -X PUT http://localhost:8080/api/products/1/images/IMAGE_ID \
  -H 'Authorization: Bearer ADMIN_ACCESS_TOKEN' \
  -H 'Content-Type: application/json' \
  -d '{"primary":true,"sortOrder":0}'
```

## 当前边界

当前允许 JPEG、PNG、WebP 和 GIF，单张最多 10 MB；SVG 暂不允许，避免其脚本能力带来的内容安全风险。确认接口只验证对象元数据，还没有读取文件头识别真实格式。预签名 PUT 也无法在上传发生前严格阻止超大对象，因此确认失败时会删除非法对象。

下一阶段应增加魔数检测、PENDING 超时清理、商品删除后的孤儿对象清理、主图和排序字段，以及缩略图异步处理。生产环境还需要私有 Bucket、最小权限账号、HTTPS、Secret 管理、审计和生命周期规则。
