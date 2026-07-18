package com.cleanmap.clean_alba_backend.storage;

import java.util.UUID;

final class AttachmentStorageKey {
    private AttachmentStorageKey() {
    }

    static String create(Long reviewId, String originalFileName) {
        String fileName = originalFileName == null ? "file" : originalFileName.replaceAll("[^A-Za-z0-9._-]", "_");
        if (fileName.isBlank() || ".".equals(fileName) || "..".equals(fileName)) {
            fileName = "file";
        }
        return "reviews/" + reviewId + "/" + UUID.randomUUID() + "-" + fileName;
    }
}
