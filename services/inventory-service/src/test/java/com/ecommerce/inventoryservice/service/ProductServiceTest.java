package com.ecommerce.inventoryservice.service;

import com.ecommerce.common.exception.BusinessException;
import com.ecommerce.common.exception.ConflictException;
import com.ecommerce.common.exception.PreconditionFailedException;
import com.ecommerce.common.exception.ResourceNotFoundException;
import com.ecommerce.inventoryservice.Product;
import com.ecommerce.inventoryservice.ProductRepository;
import com.ecommerce.inventoryservice.dto.CreateProductRequest;
import com.ecommerce.inventoryservice.dto.ProductDTO;
import com.ecommerce.inventoryservice.dto.UpdateProductRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProductService")
class ProductServiceTest {

    @Mock
    private ProductRepository repository;
    @Mock
    private ProductEventPublisher events;
    @Mock
    private ProductCacheEvictor cacheEvictor;

    private ProductService service;

    @BeforeEach
    void setUp() {
        service = new ProductService(repository, events, cacheEvictor);
        org.mockito.Mockito.lenient().when(repository.saveAndFlush(any(Product.class))).thenAnswer(inv -> {
            Product p = inv.getArgument(0);
            if (p.getId() == null) {
                p.setId(1L);
                p.setVersion(0L);
            }
            return p;
        });
    }

    private Product stored() {
        return Product.builder().id(1L).name("Headphones").price(new BigDecimal("79.99")).currency("USD")
                .sku("SKU-001").category("Electronics").version(2L).build();
    }

    private CreateProductRequest createRequest() {
        return CreateProductRequest.builder().name("Headphones").price(new BigDecimal("79.99")).sku("SKU-001").category("Electronics").build();
    }

    @Test
    @DisplayName("creating a product stores it (USD by default) and announces product.created in the same transaction")
    void create() {
        when(repository.existsBySku("SKU-001")).thenReturn(false);

        ProductDTO created = service.createProduct(createRequest());

        assertThat(created.getCurrency()).isEqualTo("USD");
        assertThat(created.getId()).isEqualTo(1L);
        verify(events).created(any(Product.class));
    }

    @Test
    @DisplayName("a duplicate SKU is a 409 and nothing is announced")
    void duplicate() {
        when(repository.existsBySku("SKU-001")).thenReturn(true);

        assertThatThrownBy(() -> service.createProduct(createRequest())).isInstanceOf(ConflictException.class);

        verify(events, never()).created(any());
    }

    @Test
    @DisplayName("a partial update changes only the fields that are sent, evicts both cache keys and announces it")
    void partialUpdate() {
        Product product = stored();
        when(repository.findById(1L)).thenReturn(Optional.of(product));

        ProductDTO updated = service.updateProduct(1L, UpdateProductRequest.builder().price(new BigDecimal("89.99")).build(), "\"2\"");

        assertThat(updated.getPrice()).isEqualByComparingTo("89.99");
        assertThat(updated.getName()).as("untouched").isEqualTo("Headphones");
        verify(cacheEvictor).evictAsync(1L, "SKU-001");
        verify(events).updated(product);
    }

    @Test
    @DisplayName("an update with a stale If-Match is a 412: nothing changes, nothing is evicted or announced")
    void staleUpdate() {
        Product product = stored();
        when(repository.findById(1L)).thenReturn(Optional.of(product));

        assertThatThrownBy(() -> service.updateProduct(1L, UpdateProductRequest.builder().price(BigDecimal.ONE).build(), "\"1\""))
                .isInstanceOf(PreconditionFailedException.class);

        assertThat(product.getPrice()).isEqualByComparingTo("79.99");
        verify(cacheEvictor, never()).evictAsync(any(), any());
        verify(events, never()).updated(any());
    }

    @Test
    @DisplayName("deleting evicts both cache keys and announces product.deleted with the SKU")
    void delete() {
        Product product = stored();
        when(repository.findById(1L)).thenReturn(Optional.of(product));

        service.deleteProduct(1L);

        verify(repository).delete(product);
        verify(cacheEvictor).evictAsync(1L, "SKU-001");
        verify(events).deleted(1L, "SKU-001");
    }

    @Test
    @DisplayName("an unknown product is a 404 for read, update and delete")
    void unknown() {
        when(repository.findById(9L)).thenReturn(Optional.empty());
        when(repository.findBySku("NOPE")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getProductById(9L)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.getProductBySku("NOPE")).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.updateProduct(9L, new UpdateProductRequest(), null)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.deleteProduct(9L)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("the batch lookup returns whichever of the SKUs exist")
    void lookup() {
        when(repository.findBySkuIn(List.of("SKU-001", "NOPE"))).thenReturn(List.of(stored()));

        assertThat(service.lookupBySkus(List.of("SKU-001", "NOPE"))).extracting(ProductDTO::getSku).containsExactly("SKU-001");
    }

    @Test
    @DisplayName("sorting is limited to known fields: a client cannot sort by an arbitrary property")
    void sortAllowList() {
        assertThatThrownBy(() -> service.getAllProducts(PageRequest.of(0, 10, Sort.by("description"))))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo("INVALID_SORT_FIELD");
    }
}
