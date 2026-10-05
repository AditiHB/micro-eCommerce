package com.ecommerce.productservice.exception;

import com.ecommerce.common.exception.ResourceNotFoundException;

/** The product does not exist (404). */
public class ProductNotFoundException extends ResourceNotFoundException {

    public ProductNotFoundException(String message) {
        super(message);
    }

    public ProductNotFoundException(Long id) {
        super("Product with id " + id + " not found");
    }
}
