package com.ecommerce.common.exception;

import org.springframework.http.HttpStatus;

/**
 * A JWE encrypt or decrypt operation failed (bad key, corrupt ciphertext, unsupported algorithm). This is
 * never the caller's fault - the request shape was fine - so unlike most {@link BusinessException}s it
 * reports 500, not 400.
 */
public class EncryptionException extends BusinessException {

    public EncryptionException(String message, Throwable cause) {
        super(message, "ENCRYPTION_ERROR", cause);
    }

    @Override
    public HttpStatus getHttpStatus() {
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }
}
