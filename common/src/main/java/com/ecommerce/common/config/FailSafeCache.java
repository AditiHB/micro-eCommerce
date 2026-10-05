package com.ecommerce.common.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;

/**
 * A cache is an optimisation, never a dependency: if it fails - Redis unreachable, a command timing out - the
 * operation is treated as a miss (reads), or skipped (writes and evictions), and the request carries on against
 * the database.
 *
 * <p>Spring's {@code CacheErrorHandler} already does this for annotation-driven calls, but not for a cache used
 * directly, nor for the put/evict that a transaction-aware cache runs <em>after the commit</em> (an exception
 * there would fail a request whose change had already been committed). Wrapping the cache itself covers every path.
 */
@Slf4j
final class FailSafeCache implements Cache {

    private final Cache delegate;

    FailSafeCache(Cache delegate) {
        this.delegate = delegate;
    }

    @Override
    public String getName() {
        return delegate.getName();
    }

    @Override
    public Object getNativeCache() {
        return delegate.getNativeCache();
    }

    @Override
    public ValueWrapper get(Object key) {
        try {
            return delegate.get(key);
        } catch (RuntimeException e) {
            return failed("get", key, e, null);
        }
    }

    @Override
    public <T> T get(Object key, Class<T> type) {
        try {
            return delegate.get(key, type);
        } catch (RuntimeException e) {
            return failed("get", key, e, null);
        }
    }

    @Override
    public <T> T get(Object key, Callable<T> valueLoader) {
        try {
            return delegate.get(key, valueLoader);
        } catch (ValueRetrievalException loaderFailed) {
            throw loaderFailed; // the caller's own loader failed: that is a real error, not a cache problem
        } catch (RuntimeException e) {
            log.warn("Cache '{}' failed on get({}): {} - loading directly", getName(), key, e.toString());
            try {
                return valueLoader.call();
            } catch (Exception loaderFailed) {
                throw new ValueRetrievalException(key, valueLoader, loaderFailed);
            }
        }
    }

    @Override
    public CompletableFuture<?> retrieve(Object key) {
        try {
            return delegate.retrieve(key);
        } catch (RuntimeException e) {
            return failed("retrieve", key, e, CompletableFuture.completedFuture(null));
        }
    }

    @Override
    public <T> CompletableFuture<T> retrieve(Object key, java.util.function.Supplier<CompletableFuture<T>> valueLoader) {
        try {
            return delegate.retrieve(key, valueLoader);
        } catch (RuntimeException e) {
            return failed("retrieve", key, e, valueLoader.get());
        }
    }

    @Override
    public void put(Object key, Object value) {
        try {
            delegate.put(key, value);
        } catch (RuntimeException e) {
            failed("put", key, e, null);
        }
    }

    @Override
    public ValueWrapper putIfAbsent(Object key, Object value) {
        try {
            return delegate.putIfAbsent(key, value);
        } catch (RuntimeException e) {
            return failed("putIfAbsent", key, e, null);
        }
    }

    @Override
    public void evict(Object key) {
        try {
            delegate.evict(key);
        } catch (RuntimeException e) {
            failed("evict", key, e, null);
        }
    }

    @Override
    public boolean evictIfPresent(Object key) {
        try {
            return delegate.evictIfPresent(key);
        } catch (RuntimeException e) {
            return failed("evictIfPresent", key, e, false);
        }
    }

    @Override
    public void clear() {
        try {
            delegate.clear();
        } catch (RuntimeException e) {
            failed("clear", null, e, null);
        }
    }

    @Override
    public boolean invalidate() {
        try {
            return delegate.invalidate();
        } catch (RuntimeException e) {
            return failed("invalidate", null, e, false);
        }
    }

    private <T> T failed(String operation, Object key, RuntimeException e, T fallback) {
        log.warn("Cache '{}' failed on {}({}): {} - continuing without it", getName(), operation, key, e.toString());
        return fallback;
    }
}
