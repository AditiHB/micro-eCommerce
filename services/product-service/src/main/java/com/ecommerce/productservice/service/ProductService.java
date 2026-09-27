package com.ecommerce.productservice.service;

import com.ecommerce.productservice.dto.CreateProductRequest;
import com.ecommerce.productservice.dto.ProductDTO;
import com.ecommerce.productservice.dto.UpdateProductRequest;
import com.ecommerce.productservice.entity.Product;
import com.ecommerce.productservice.event.ProductCreatedEvent;
import com.ecommerce.productservice.event.ProductDeletedEvent;
import com.ecommerce.productservice.event.ProductUpdatedEvent;
import com.ecommerce.productservice.exception.DuplicateSkuException;
import com.ecommerce.productservice.exception.ProductNotFoundException;
import com.ecommerce.productservice.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductService {
    private final ProductRepository productRepository;
    private final ProductEventPublisher eventPublisher;

    @Transactional
    public ProductDTO createProduct(CreateProductRequest request) {
        log.info("Creating new product with SKU: {}", request.getSku());

        if (productRepository.existsBySku(request.getSku())) {
            throw new DuplicateSkuException(request.getSku());
        }

        Product product = Product.builder()
                .name(request.getName())
                .description(request.getDescription())
                .price(request.getPrice())
                .sku(request.getSku())
                .category(request.getCategory())
                .quantityAvailable(request.getQuantityAvailable())
                .build();

        Product savedProduct = productRepository.save(product);
        log.info("Product created successfully with id: {}", savedProduct.getId());

        eventPublisher.publishProductCreatedEvent(ProductCreatedEvent.builder()
                .productId(savedProduct.getId())
                .name(savedProduct.getName())
                .sku(savedProduct.getSku())
                .price(savedProduct.getPrice())
                .category(savedProduct.getCategory())
                .quantityAvailable(savedProduct.getQuantityAvailable())
                .eventTime(LocalDateTime.now())
                .build());

        return convertToDTO(savedProduct);
    }

    @Transactional(readOnly = true)
    public ProductDTO getProductById(Long id) {
        log.info("Fetching product with id: {}", id);
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ProductNotFoundException(id));
        return convertToDTO(product);
    }

    @Transactional(readOnly = true)
    public ProductDTO getProductBySku(String sku) {
        log.info("Fetching product with SKU: {}", sku);
        Product product = productRepository.findBySku(sku)
                .orElseThrow(() -> new ProductNotFoundException("Product with SKU " + sku + " not found"));
        return convertToDTO(product);
    }

    @Transactional(readOnly = true)
    public Page<ProductDTO> getAllProducts(Pageable pageable) {
        log.info("Fetching all products with pagination");
        return productRepository.findAll(pageable)
                .map(this::convertToDTO);
    }

    @Transactional(readOnly = true)
    public Page<ProductDTO> getProductsByCategory(String category, Pageable pageable) {
        log.info("Fetching products by category: {}", category);
        return productRepository.findByCategory(category, pageable)
                .map(this::convertToDTO);
    }

    @Transactional(readOnly = true)
    public Page<ProductDTO> searchProducts(String searchTerm, Pageable pageable) {
        log.info("Searching products with term: {}", searchTerm);
        return productRepository.searchByName(searchTerm, pageable)
                .map(this::convertToDTO);
    }

    @Transactional(readOnly = true)
    public Page<ProductDTO> getAvailableProducts(Pageable pageable) {
        log.info("Fetching available products");
        return productRepository.findAvailableProducts(pageable)
                .map(this::convertToDTO);
    }

    @Transactional(readOnly = true)
    public List<ProductDTO> getLowStockProducts() {
        log.info("Fetching low stock products");
        return productRepository.findLowStockProducts()
                .stream()
                .map(this::convertToDTO)
                .toList();
    }

    @Transactional
    public ProductDTO updateProduct(Long id, UpdateProductRequest request) {
        log.info("Updating product with id: {}", id);
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ProductNotFoundException(id));

        if (request.getName() != null) {
            product.setName(request.getName());
        }
        if (request.getDescription() != null) {
            product.setDescription(request.getDescription());
        }
        if (request.getPrice() != null) {
            product.setPrice(request.getPrice());
        }
        if (request.getCategory() != null) {
            product.setCategory(request.getCategory());
        }
        if (request.getQuantityAvailable() != null) {
            product.setQuantityAvailable(request.getQuantityAvailable());
        }

        Product updatedProduct = productRepository.save(product);
        log.info("Product updated successfully with id: {}", updatedProduct.getId());

        eventPublisher.publishProductUpdatedEvent(ProductUpdatedEvent.builder()
                .productId(updatedProduct.getId())
                .name(updatedProduct.getName())
                .price(updatedProduct.getPrice())
                .category(updatedProduct.getCategory())
                .quantityAvailable(updatedProduct.getQuantityAvailable())
                .eventTime(LocalDateTime.now())
                .build());

        return convertToDTO(updatedProduct);
    }

    @Transactional
    public void deleteProduct(Long id) {
        log.info("Deleting product with id: {}", id);
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ProductNotFoundException(id));

        String sku = product.getSku();
        productRepository.deleteById(id);
        log.info("Product deleted successfully with id: {}", id);

        eventPublisher.publishProductDeletedEvent(ProductDeletedEvent.builder()
                .productId(id)
                .sku(sku)
                .eventTime(LocalDateTime.now())
                .build());
    }

    @Transactional
    public void reserveInventory(Long productId, Integer quantityToReserve) {
        log.info("Reserving inventory for product: {}, quantity: {}", productId, quantityToReserve);
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException(productId));

        if (product.getQuantityAvailable() < quantityToReserve) {
            throw new IllegalArgumentException("Insufficient inventory for product: " + productId);
        }

        product.setQuantityAvailable(product.getQuantityAvailable() - quantityToReserve);
        productRepository.save(product);
        log.info("Inventory reserved successfully for product: {}", productId);
    }

    @Transactional
    public void releaseInventory(Long productId, Integer quantityToRelease) {
        log.info("Releasing inventory for product: {}, quantity: {}", productId, quantityToRelease);
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException(productId));

        product.setQuantityAvailable(product.getQuantityAvailable() + quantityToRelease);
        productRepository.save(product);
        log.info("Inventory released successfully for product: {}", productId);
    }

    private ProductDTO convertToDTO(Product product) {
        return ProductDTO.builder()
                .id(product.getId())
                .name(product.getName())
                .description(product.getDescription())
                .price(product.getPrice())
                .sku(product.getSku())
                .category(product.getCategory())
                .quantityAvailable(product.getQuantityAvailable())
                .createdAt(product.getCreatedAt())
                .updatedAt(product.getUpdatedAt())
                .build();
    }
}
