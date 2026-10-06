package com.ecommerce.inventoryservice.service;

import com.ecommerce.common.config.CacheConfig;
import com.ecommerce.common.exception.ConflictException;
import com.ecommerce.common.exception.ResourceNotFoundException;
import com.ecommerce.common.web.EntityTags;
import com.ecommerce.common.web.SortGuard;
import com.ecommerce.inventoryservice.Product;
import com.ecommerce.inventoryservice.ProductRepository;
import com.ecommerce.inventoryservice.dto.CreateProductRequest;
import com.ecommerce.inventoryservice.dto.ProductDTO;
import com.ecommerce.inventoryservice.dto.UpdateProductRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * The catalogue. Catalogue entries are reference data - read on every order, changed rarely - so a single
 * product read by id or by SKU is cached, one entry per key, and evicted by key (after the change commits) when
 * that product changes. Stock is not here: {@link InventoryService} owns it.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProductService {

    static final Set<String> SORTABLE_FIELDS = Set.of("id", "name", "price", "sku", "category", "createdAt", "updatedAt");

    private final ProductRepository productRepository;
    private final ProductEventPublisher eventPublisher;
    private final CacheManager cacheManager;

    @Transactional
    public ProductDTO createProduct(CreateProductRequest request) {
        log.info("Creating product with SKU: {}", request.getSku());
        if (productRepository.existsBySku(request.getSku())) {
            throw new ConflictException("Product with SKU " + request.getSku() + " already exists", "DUPLICATE_SKU");
        }
        Product saved = productRepository.saveAndFlush(Product.builder()
                .name(request.getName())
                .description(request.getDescription())
                .price(request.getPrice())
                .currency(request.getCurrency() == null ? "USD" : request.getCurrency())
                .sku(request.getSku())
                .category(request.getCategory())
                .build());
        eventPublisher.created(saved);
        return convertToDTO(saved);
    }

    @Transactional(readOnly = true)
    @Cacheable(value = CacheConfig.PRODUCTS_CACHE, key = "'id:' + #id")
    public ProductDTO getProductById(Long id) {
        return convertToDTO(productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product with id " + id + " not found")));
    }

    @Transactional(readOnly = true)
    @Cacheable(value = CacheConfig.PRODUCTS_CACHE, key = "'sku:' + #sku")
    public ProductDTO getProductBySku(String sku) {
        return convertToDTO(productRepository.findBySku(sku)
                .orElseThrow(() -> new ResourceNotFoundException("Product with SKU " + sku + " not found")));
    }

    /** The products that exist among the given SKUs (unknown SKUs are simply absent): one query, for pricing an order. */
    @Transactional(readOnly = true)
    public List<ProductDTO> lookupBySkus(Collection<String> skus) {
        return productRepository.findBySkuIn(skus).stream().map(this::convertToDTO).toList();
    }

    @Transactional(readOnly = true)
    public Page<ProductDTO> getAllProducts(Pageable pageable) {
        return productRepository.findAll(SortGuard.requireSortableBy(pageable, SORTABLE_FIELDS)).map(this::convertToDTO);
    }

    @Transactional(readOnly = true)
    public Page<ProductDTO> getProductsByCategory(String category, Pageable pageable) {
        return productRepository.findByCategory(category, SortGuard.requireSortableBy(pageable, SORTABLE_FIELDS)).map(this::convertToDTO);
    }

    @Transactional(readOnly = true)
    public Page<ProductDTO> searchProducts(String searchTerm, Pageable pageable) {
        return productRepository.searchByName(searchTerm, SortGuard.requireSortableBy(pageable, SORTABLE_FIELDS)).map(this::convertToDTO);
    }

    /** Partial update: only the fields that are sent change. {@code If-Match} refuses a stale update (412). */
    @Transactional
    public ProductDTO updateProduct(Long id, UpdateProductRequest request, String ifMatch) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product with id " + id + " not found"));
        EntityTags.verifyIfMatch(ifMatch, product.getVersion());

        if (request.getName() != null) {
            product.setName(request.getName());
        }
        if (request.getDescription() != null) {
            product.setDescription(request.getDescription());
        }
        if (request.getPrice() != null) {
            product.setPrice(request.getPrice());
        }
        if (request.getCurrency() != null) {
            product.setCurrency(request.getCurrency());
        }
        if (request.getCategory() != null) {
            product.setCategory(request.getCategory());
        }

        Product updated = productRepository.saveAndFlush(product);
        evict(updated.getId(), updated.getSku());
        eventPublisher.updated(updated);
        return convertToDTO(updated);
    }

    @Transactional
    public void deleteProduct(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product with id " + id + " not found"));
        productRepository.delete(product);
        productRepository.flush();
        evict(id, product.getSku());
        eventPublisher.deleted(id, product.getSku());
    }

    /** Evicts both cache entries of one product (by id and by SKU). Runs after the transaction commits. */
    private void evict(Long id, String sku) {
        Cache cache = cacheManager.getCache(CacheConfig.PRODUCTS_CACHE);
        if (cache != null) {
            cache.evict("id:" + id);
            cache.evict("sku:" + sku);
        }
    }

    private ProductDTO convertToDTO(Product product) {
        return ProductDTO.builder()
                .id(product.getId())
                .name(product.getName())
                .description(product.getDescription())
                .price(product.getPrice())
                .currency(product.getCurrency())
                .sku(product.getSku())
                .category(product.getCategory())
                .version(product.getVersion())
                .createdAt(product.getCreatedAt())
                .updatedAt(product.getUpdatedAt())
                .build();
    }
}
