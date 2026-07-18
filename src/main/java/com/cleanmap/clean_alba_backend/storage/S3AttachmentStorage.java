package com.cleanmap.clean_alba_backend.storage;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.InputStream;

@Component
@ConditionalOnProperty(prefix = "attachment.storage", name = "type", havingValue = "s3")
public class S3AttachmentStorage implements AttachmentStorage {
    private final S3Client s3Client;
    private final AttachmentStorageProperties properties;

    public S3AttachmentStorage(S3Client s3Client, AttachmentStorageProperties properties) {
        this.s3Client = s3Client;
        this.properties = properties;
    }

    @Override
    public String store(Long reviewId, String originalFileName, String contentType, byte[] content) {
        String storageKey = AttachmentStorageKey.create(reviewId, originalFileName);
        try {
            s3Client.putObject(request -> request.bucket(properties.bucket()).key(storageKey)
                    .contentType(contentType), RequestBody.fromBytes(content));
            return storageKey;
        } catch (S3Exception exception) {
            throw new AttachmentStorageException("Unable to store attachment.", exception);
        }
    }

    @Override
    public InputStream load(String storageKey) {
        try {
            ResponseInputStream<?> response = s3Client.getObject(GetObjectRequest.builder()
                    .bucket(properties.bucket())
                    .key(storageKey)
                    .build());
            return response;
        } catch (NoSuchKeyException exception) {
            throw new AttachmentStorageObjectNotFoundException();
        } catch (S3Exception exception) {
            if (exception.statusCode() == 404) {
                throw new AttachmentStorageObjectNotFoundException();
            }
            throw new AttachmentStorageException("Unable to load attachment.", exception);
        }
    }

    @Override
    public void delete(String storageKey) {
        try {
            s3Client.deleteObject(request -> request.bucket(properties.bucket()).key(storageKey));
        } catch (S3Exception exception) {
            throw new AttachmentStorageException("Unable to delete attachment.", exception);
        }
    }

    @Override
    public boolean exists(String storageKey) {
        try {
            s3Client.headObject(HeadObjectRequest.builder().bucket(properties.bucket()).key(storageKey).build());
            return true;
        } catch (S3Exception exception) {
            if (exception.statusCode() == 404) {
                return false;
            }
            throw new AttachmentStorageException("Unable to check attachment.", exception);
        }
    }
}
