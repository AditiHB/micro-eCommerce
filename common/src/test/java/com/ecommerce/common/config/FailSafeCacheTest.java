package com.ecommerce.common.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.concurrent.ConcurrentMapCache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("FailSafeCache")
class FailSafeCacheTest {

    private final Cache broken = mock(Cache.class);
    private final FailSafeCache cache = new FailSafeCache(broken);

    private RuntimeException redisDown() {
        return new IllegalStateException("Unable to connect to Redis");
    }

    @Test
    @DisplayName("a working cache behaves normally")
    void passesThrough() {
        FailSafeCache working = new FailSafeCache(new ConcurrentMapCache("customers"));

        working.put("1", "value");

        assertThat(working.get("1", String.class)).isEqualTo("value");
        assertThat(working.getName()).isEqualTo("customers");
        working.evict("1");
        assertThat(working.get("1")).isNull();
    }

    @Test
    @DisplayName("a failing read is a miss, not an error")
    void failingReadIsAMiss() {
        when(broken.get("1")).thenThrow(redisDown());
        when(broken.get("1", String.class)).thenThrow(redisDown());

        assertThat(cache.get("1")).isNull();
        assertThat(cache.get("1", String.class)).isNull();
    }

    @Test
    @DisplayName("a read-through that cannot reach the cache runs the loader directly")
    void loaderRunsWhenCacheIsDown() {
        when(broken.get(any(), any(java.util.concurrent.Callable.class))).thenThrow(redisDown());

        assertThat(cache.get("1", () -> "from the database")).isEqualTo("from the database");
    }

    @Test
    @DisplayName("but a failure of the caller's own loader is still a real error")
    void loaderFailureIsNotHidden() {
        when(broken.get(any(), any(java.util.concurrent.Callable.class)))
                .thenThrow(new Cache.ValueRetrievalException("1", () -> null, new IllegalArgumentException("db error")));

        assertThatThrownBy(() -> cache.get("1", () -> "x")).isInstanceOf(Cache.ValueRetrievalException.class);
    }

    @Test
    @DisplayName("failing writes and evictions are skipped: the change that triggered them is not undone or failed")
    void writesAreSkipped() {
        org.mockito.Mockito.doThrow(redisDown()).when(broken).put(any(), any());
        org.mockito.Mockito.doThrow(redisDown()).when(broken).evict(any());
        org.mockito.Mockito.doThrow(redisDown()).when(broken).clear();
        when(broken.putIfAbsent(any(), any())).thenThrow(redisDown());
        when(broken.evictIfPresent(any())).thenThrow(redisDown());

        org.assertj.core.api.Assertions.assertThatCode(() -> {
            cache.put("1", "v");
            cache.evict("1");
            cache.clear();
            assertThat(cache.putIfAbsent("1", "v")).isNull();
            assertThat(cache.evictIfPresent("1")).isFalse();
        }).doesNotThrowAnyException();
    }
}
