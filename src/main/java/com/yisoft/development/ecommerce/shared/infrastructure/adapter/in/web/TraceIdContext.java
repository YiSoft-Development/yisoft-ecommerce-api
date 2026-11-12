package com.yisoft.development.ecommerce.shared.infrastructure.adapter.in.web;

import lombok.experimental.UtilityClass;
import reactor.core.publisher.Mono;

@UtilityClass
public final class TraceIdContext {

    public static final String TRACE_ID_HEADER = "X-Trace-Id";
    public static final String KEY = "traceId";
    public static final String MDC_KEY = "xTraceId";

    public static Mono<String> current(){
        return Mono.deferContextual(ctx -> Mono.just(ctx.get(KEY)));
    }
}
