package com.cleanmap.clean_alba_backend.storage;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@ConditionalOnProperty(prefix = "attachment.storage", name = "type", havingValue = "memory")
public class InMemoryAttachmentStorage implements AttachmentStorage {
    private final Map<String, byte[]> objects = new ConcurrentHashMap<>();

    @Override
    public String store(Long reviewId, String originalFileName, String contentType, byte[] content) {
        String storageKey = AttachmentStorageKey.create(reviewId, originalFileName);
        objects.put(storageKey, content.clone());
        return storageKey;
    }

    @Override
    public InputStream load(String storageKey) {
        byte[] content = objects.get(storageKey);
        if (content == null) {
            throw new AttachmentStorageObjectNotFoundException();
        }
        return new ByteArrayInputStream(content);
    }

    @Override
    public void delete(String storageKey) {
        objects.remove(storageKey);
    }

    @Override
    public boolean exists(String storageKey) {
        return objects.containsKey(storageKey);
    }
}
