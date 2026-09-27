package com.ecommerce.apigateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.time.Instant;

@Component
@Slf4j
public class RequestLoggingFilter implements GlobalFilter, Ordered {

    private static final String TRACE_ID_HEADER = "X-Trace-Id";
    private static final String START_TIME_ATTR = "startTime";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String traceId = exchange.getRequest().getHeaders().getFirst(TRACE_ID_HEADER);
        final String finalTraceId;
        if (traceId == null) {
            finalTraceId = generateTraceId();
            exchange.getRequest().mutate().header(TRACE_ID_HEADER, finalTraceId).build();
        } else {
            finalTraceId = traceId;
        }

        URI uri = exchange.getRequest().getURI();
        String method = exchange.getRequest().getMethod() != null ? exchange.getRequest().getMethod().toString() : "UNKNOWN";

        long startTime = System.currentTimeMillis();
        exchange.getAttributes().put(START_TIME_ATTR, startTime);

        log.info("Incoming request [trace={}] {} {}", finalTraceId, method, uri.getPath());

        return chain.filter(exchange).doFinally(signalType -> {
            long endTime = System.currentTimeMillis();
            long duration = endTime - startTime;
            int status = exchange.getResponse().getStatusCode() != null ? exchange.getResponse().getStatusCode().value() : 0;

            log.info("Completed request [trace={}] {} {} -> Status: {} ({}ms)",
                finalTraceId, method, uri.getPath(), status, duration);
        });
    }

    private String generateTraceId() {
        return "trace-" + Instant.now().getEpochSecond() + "-" + System.nanoTime();
    }

    @Override
    public int getOrder() {
        return -1;
    }
}
