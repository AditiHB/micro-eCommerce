package com.ecommerce.productservice.exception;

import com.ecommerce.common.exception.ProblemDetailsAdvice;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Product-service's error handling: every failure is an RFC 9457 problem document, built by the shared
 * {@link ProblemDetailsAdvice}. {@link ProductNotFoundException} (404) and {@link DuplicateSkuException} (409) are
 * typed business exceptions and need no mapping here; add a handler to this class only for an exception type that
 * only this service knows about.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ProblemDetailsAdvice {
}
