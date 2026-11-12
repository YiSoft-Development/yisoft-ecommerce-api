package com.yisoft.development.ecommerce.shared.infrastructure.adapter.in.web;

import reactor.core.publisher.Mono;

/**
 * Generic class to wrap a response
 * @param traceId
 * @param data
 * @param <T>
 */
public record ApiResponse <T> (String traceId, T data) {

    public static <T> Mono<ApiResponse<T>> from(Mono<T> data){
        return TraceIdContext.current().zipWith(data, ApiResponse::new);
    }
}

