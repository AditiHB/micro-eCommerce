package com.ecommerce.common.exception;

public class ResourceNotFoundException extends BusinessException {
    public ResourceNotFoundException(String resourceName, Long id) {
        super(resourceName + " not found with id: " + id, "RESOURCE_NOT_FOUND");
    }

    public ResourceNotFoundException(String message) {
        super(message, "RESOURCE_NOT_FOUND");
    }
}
