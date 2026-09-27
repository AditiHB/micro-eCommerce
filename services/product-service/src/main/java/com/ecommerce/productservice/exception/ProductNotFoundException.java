package com.ecommerce.productservice.exception;

public class ProductNotFoundException extends RuntimeException {
    public ProductNotFoundException(String message) {
        super(message);
    }

    public ProductNotFoundException(Long id) {
        super("Product with id " + id + " not found");
    }

    public ProductNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
