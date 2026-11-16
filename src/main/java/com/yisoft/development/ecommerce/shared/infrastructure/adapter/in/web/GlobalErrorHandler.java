package com.yisoft.development.ecommerce.shared.infrastructure.adapter.in.web;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.codec.HttpMessageWriter;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.reactive.result.view.ViewResolver;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebExceptionHandler;
import reactor.core.publisher.Mono;

import java.util.List;

@Slf4j
@Component
@Order(-2)
@RequiredArgsConstructor
public class GlobalErrorHandler implements WebExceptionHandler {

    private final ProblemDetailMapper problemDetailMapper;
    private final ServerCodecConfigurer serverCodecConfigurer;

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable error) {
        if (exchange.getResponse().isCommitted()) {
            return Mono.error(error);
        }

        String traceId = exchange.getAttribute(TraceIdContext.KEY);
        ProblemDetail problem = problemDetailMapper.toProblemDetail(error, traceId);
        logError(exchange, problem, traceId, error);

        return ServerResponse.status(problem.getStatus())
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyValue(problem)
                .flatMap(response -> response.writeTo(exchange, new ResponseContext(serverCodecConfigurer.getWriters())));
    }

    private static void logError(ServerWebExchange exchange, ProblemDetail problem, String traceId, Throwable error) {
        String method = exchange.getRequest().getMethod().name();
        String path = exchange.getRequest().getPath().value();
        Integer status = problem.getStatus();
        String title = problem.getTitle();
        if (status >= 500) {
            log.error("[{}] {} {} -> {}", traceId, method, path, status, error);
        } else {
            log.warn("[{}] {} {} -> {} {}", traceId, method, path, status, title);
        }
    }

    private record ResponseContext(List<HttpMessageWriter<?>> messageWriters) implements ServerResponse.Context {

        @Override
        public List<ViewResolver> viewResolvers() {
            return List.of();
        }
    }
}
