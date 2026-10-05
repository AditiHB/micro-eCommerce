package com.ecommerce.productservice.exception;

import com.ecommerce.common.exception.ConflictException;

/** A product with this SKU already exists (409). */
public class DuplicateSkuException extends ConflictException {

    public DuplicateSkuException(String sku) {
        super("Product with SKU " + sku + " already exists", "DUPLICATE_SKU");
    }
}
