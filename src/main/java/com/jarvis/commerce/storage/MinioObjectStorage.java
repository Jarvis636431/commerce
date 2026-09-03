package com.jarvis.commerce.storage;

import io.minio.BucketExistsArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.Http.Method;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URL;
import java.time.Duration;

@Component
@ConditionalOnProperty(name = "commerce.storage.enabled", havingValue = "true", matchIfMissing = true)
public class MinioObjectStorage implements ObjectStorage {
    private final MinioClient client;
    private final String bucket;
    private volatile boolean bucketReady;

    public MinioObjectStorage(MinioClient client, @Value("${commerce.storage.bucket}") String bucket) {
        this.client = client;
        this.bucket = bucket;
    }

    @Override
    public URL presignUpload(String objectKey, Duration ttl) {
        return presign(objectKey, ttl, Method.PUT);
    }

    @Override
    public URL presignDownload(String objectKey, Duration ttl) {
        return presign(objectKey, ttl, Method.GET);
    }

    @Override
    public ObjectMetadata stat(String objectKey) {
        ensureBucket();
        try {
            var response = client.statObject(StatObjectArgs.builder().bucket(bucket).object(objectKey).build());
            return new ObjectMetadata(response.size(), response.contentType(), response.etag());
        } catch (Exception exception) {
            throw unavailable("Cannot inspect object " + objectKey, exception);
        }
    }

    @Override
    public void delete(String objectKey) {
        ensureBucket();
        try {
            client.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(objectKey).build());
        } catch (Exception exception) {
            throw unavailable("Cannot delete object " + objectKey, exception);
        }
    }

    private URL presign(String objectKey, Duration ttl, Method method) {
        ensureBucket();
        try {
            String value = client.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(method).bucket(bucket).object(objectKey)
                    .expiry(Math.toIntExact(ttl.toSeconds())).build());
            return URI.create(value).toURL();
        } catch (Exception exception) {
            throw unavailable("Cannot create a presigned object URL", exception);
        }
    }

    private synchronized void ensureBucket() {
        if (bucketReady) return;
        try {
            boolean exists = client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
            if (!exists) client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
            bucketReady = true;
        } catch (Exception exception) {
            throw unavailable("Cannot initialize object storage bucket " + bucket, exception);
        }
    }

    private StorageUnavailableException unavailable(String message, Exception cause) {
        return new StorageUnavailableException(message, cause);
    }
}
