package com.cleanmap.clean_alba_backend.storage;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

@Configuration
@EnableConfigurationProperties(AttachmentStorageProperties.class)
public class AttachmentStorageConfiguration {
    @Bean
    @ConditionalOnProperty(prefix = "attachment.storage", name = "type", havingValue = "s3")
    S3Client s3Client(AttachmentStorageProperties properties) {
        if (properties.bucket() == null || properties.bucket().isBlank()
                || properties.region() == null || properties.region().isBlank()) {
            throw new IllegalStateException("S3 attachment storage requires bucket and region configuration.");
        }
        return S3Client.builder()
                .region(Region.of(properties.region()))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }
}
