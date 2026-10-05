package com.ecommerce.productservice.controller;

import com.ecommerce.common.constants.ApiConstants;
import com.ecommerce.common.exception.BusinessException;
import com.ecommerce.common.web.EntityTags;
import com.ecommerce.productservice.dto.CreateProductRequest;
import com.ecommerce.productservice.dto.ProductDTO;
import com.ecommerce.productservice.dto.UpdateProductRequest;
import com.ecommerce.productservice.service.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** The product catalogue. Read by every authenticated caller; changed by back office. It has no stock operations: inventory-service owns stock. */
@RestController
@Validated
@RequestMapping(ApiConstants.API_PREFIX + ApiConstants.PRODUCTS_ENDPOINT)
@RequiredArgsConstructor
@Slf4j
public class ProductController {

    static final int MAX_LOOKUP = 50;

    private final ProductService productService;

    @PostMapping
    public ResponseEntity<ProductDTO> createProduct(@Valid @RequestBody CreateProductRequest request) {
        log.info("POST /products - Create product");
        ProductDTO created = productService.createProduct(request);
        return withEtag(ResponseEntity.created(URI.create(ApiConstants.API_PREFIX + ApiConstants.PRODUCTS_ENDPOINT + "/" + created.getId())), created);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProductDTO> getProduct(@PathVariable Long id) {
        return withEtag(ResponseEntity.ok(), productService.getProductById(id));
    }

    @GetMapping("/sku/{sku}")
    public ResponseEntity<ProductDTO> getProductBySku(@PathVariable String sku) {
        return withEtag(ResponseEntity.ok(), productService.getProductBySku(sku));
    }

    /**
     * The catalogue entries for a comma-separated list of SKUs, in one call - what an order uses to price its lines.
     * SKUs that do not exist are simply absent from the result.
     */
    @GetMapping("/lookup")
    public ResponseEntity<List<ProductDTO>> lookup(@RequestParam String skus) {
        Set<String> wanted = new LinkedHashSet<>(Arrays.stream(skus.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList());
        if (wanted.isEmpty() || wanted.size() > MAX_LOOKUP) {
            throw new BusinessException("Provide between 1 and " + MAX_LOOKUP + " SKUs", "INVALID_LOOKUP");
        }
        return ResponseEntity.ok(productService.lookupBySkus(wanted));
    }

    @GetMapping
    public ResponseEntity<Page<ProductDTO>> getAllProducts(Pageable pageable) {
        return ResponseEntity.ok(productService.getAllProducts(pageable));
    }

    @GetMapping("/category/{category}")
    public ResponseEntity<Page<ProductDTO>> getProductsByCategory(@PathVariable String category, Pageable pageable) {
        return ResponseEntity.ok(productService.getProductsByCategory(category, pageable));
    }

    @GetMapping("/search")
    public ResponseEntity<Page<ProductDTO>> searchProducts(@RequestParam String term, Pageable pageable) {
        return ResponseEntity.ok(productService.searchProducts(term, pageable));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ProductDTO> updateProduct(
            @PathVariable Long id,
            @Valid @RequestBody UpdateProductRequest request,
            @RequestHeader(name = HttpHeaders.IF_MATCH, required = false) String ifMatch) {
        log.info("PUT /products/{} - Update product", id);
        return withEtag(ResponseEntity.ok(), productService.updateProduct(id, request, ifMatch));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProduct(@PathVariable Long id) {
        log.info("DELETE /products/{} - Delete product", id);
        productService.deleteProduct(id);
        return ResponseEntity.noContent().build();
    }

    private static ResponseEntity<ProductDTO> withEtag(ResponseEntity.BodyBuilder builder, ProductDTO product) {
        return builder.eTag(EntityTags.of(product.getVersion())).body(product);
    }
}
