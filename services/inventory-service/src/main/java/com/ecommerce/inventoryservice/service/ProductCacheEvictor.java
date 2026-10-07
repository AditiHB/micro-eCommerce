package com.ecommerce.inventoryservice.service;

import com.ecommerce.common.config.CacheConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Evicts product cache entries off the caller's thread. A separate bean, not a private method on
 * {@link ProductService}: Spring's {@code @Async} proxy only intercepts calls that arrive through another bean,
 * so a call from within the same class would bypass it and run synchronously.
 *
 * <p>Must only be invoked once the owning transaction has actually committed (see
 * {@link ProductService#evict}). The cache itself is transaction-aware ({@code RedisConfig} sets
 * {@code setTransactionAware(true)}), so calling {@code evict} mid-transaction would normally defer the real
 * eviction until commit on its own - but that deferral is bound to the calling thread via
 * {@code TransactionSynchronizationManager}. Dispatched here, on a fresh executor thread with no transaction of
 * its own, the same call would instead evict immediately - which is exactly why this method must not be reached
 * until the commit this thread is waiting on has already happened.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ProductCacheEvictor {

    private final CacheManager cacheManager;

    @Async("cacheEvictionExecutor")
    public void evictAsync(Long id, String sku) {
        Cache cache = cacheManager.getCache(CacheConfig.PRODUCTS_CACHE);
        if (cache != null) {
            cache.evict("id:" + id);
            cache.evict("sku:" + sku);
            log.debug("Evicted cached product {} ({})", id, sku);
        }
    }
}
