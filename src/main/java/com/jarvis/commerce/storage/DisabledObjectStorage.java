package com.jarvis.commerce.storage;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.net.URL;
import java.time.Duration;

@Component
@ConditionalOnProperty(name = "commerce.storage.enabled", havingValue = "false")
public class DisabledObjectStorage implements ObjectStorage {
    private StorageUnavailableException disabled() {
        return new StorageUnavailableException("Object storage is disabled");
    }

    @Override public URL presignUpload(String objectKey, Duration ttl) { throw disabled(); }
    @Override public URL presignDownload(String objectKey, Duration ttl) { throw disabled(); }
    @Override public ObjectMetadata stat(String objectKey) { throw disabled(); }
    @Override public void delete(String objectKey) { throw disabled(); }
}
