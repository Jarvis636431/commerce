package com.jarvis.commerce.storage;

import io.minio.MinioClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MinioStorageConfiguration {

    @Bean
    @ConditionalOnProperty(name = "commerce.storage.enabled", havingValue = "true", matchIfMissing = true)
    MinioClient minioClient(@Value("${commerce.storage.endpoint}") String endpoint,
                            @Value("${commerce.storage.access-key}") String accessKey,
                            @Value("${commerce.storage.secret-key}") String secretKey) {
        return MinioClient.builder().endpoint(endpoint).credentials(accessKey, secretKey).build();
    }
}
