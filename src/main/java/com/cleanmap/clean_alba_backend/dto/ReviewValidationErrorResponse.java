package com.cleanmap.clean_alba_backend.dto;

import java.util.Map;

public record ReviewValidationErrorResponse(int status, String message, Map<String, String> errors) {
}
