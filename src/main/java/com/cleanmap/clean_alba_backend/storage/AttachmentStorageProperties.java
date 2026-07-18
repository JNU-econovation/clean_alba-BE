package com.cleanmap.clean_alba_backend.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "attachment.storage")
public record AttachmentStorageProperties(String type, String bucket, String region) {
}
