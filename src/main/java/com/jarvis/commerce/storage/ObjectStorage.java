package com.jarvis.commerce.storage;

import java.net.URL;
import java.time.Duration;

public interface ObjectStorage {
    URL presignUpload(String objectKey, Duration ttl);
    URL presignDownload(String objectKey, Duration ttl);
    ObjectMetadata stat(String objectKey);
    void delete(String objectKey);
}
