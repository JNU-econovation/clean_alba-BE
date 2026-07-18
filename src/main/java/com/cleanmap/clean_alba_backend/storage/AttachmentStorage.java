package com.cleanmap.clean_alba_backend.storage;

import java.io.InputStream;

public interface AttachmentStorage {
    String store(Long reviewId, String originalFileName, String contentType, byte[] content);

    InputStream load(String storageKey);

    void delete(String storageKey);

    boolean exists(String storageKey);
}
