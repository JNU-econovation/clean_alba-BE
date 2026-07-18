package com.cleanmap.clean_alba_backend.storage;

public class AttachmentStorageObjectNotFoundException extends RuntimeException {
    public AttachmentStorageObjectNotFoundException() {
        super("Stored attachment object not found.");
    }
}
