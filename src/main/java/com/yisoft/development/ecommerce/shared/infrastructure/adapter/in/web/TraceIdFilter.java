package com.yisoft.development.ecommerce.shared.infrastructure.adapter.in.web;

import org.apache.commons.lang3.StringUtils;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

import static com.yisoft.development.ecommerce.shared.infrastructure.adapter.in.web.TraceIdContext.KEY;
import static com.yisoft.development.ecommerce.shared.infrastructure.adapter.in.web.TraceIdContext.TRACE_ID_HEADER;

/**
 * Filter to propagate traceId from headers
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@Component
public class TraceIdFilter implements WebFilter {

    private static final Pattern UUID_REGEX =
            Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain){
        String traceIdHeader = exchange.getRequest()
                .getHeaders()
                .getFirst(TRACE_ID_HEADER);

        String traceId = resolveTraceIdFromHeaderRequest(traceIdHeader);

        exchange.getResponse().beforeCommit(() -> {
            exchange.getResponse().getHeaders().set(TRACE_ID_HEADER, traceId);
            return Mono.empty();
        });

        exchange.getAttributes().put(KEY, traceId);

        if (isInvalid(traceIdHeader)){
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid TraceId"));
        }

        return chain.filter(exchange)
                .contextWrite(Context.of(KEY, traceId));
    }

    private static String resolveTraceIdFromHeaderRequest(String traceIdHeader){
        if (StringUtils.isBlank(traceIdHeader)){
            return UUID.randomUUID().toString();
        }

        if (UUID_REGEX.matcher(traceIdHeader).matches()){
            return traceIdHeader.toLowerCase(Locale.ROOT);
        } else {
            return UUID.randomUUID().toString();
        }
    }

    private static boolean isInvalid(String traceIdHeader){
        return !StringUtils.isBlank(traceIdHeader) && !UUID_REGEX.matcher(traceIdHeader).matches();
    }
}
