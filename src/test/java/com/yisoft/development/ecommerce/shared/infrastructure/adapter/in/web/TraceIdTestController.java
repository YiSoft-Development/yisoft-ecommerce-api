package com.yisoft.development.ecommerce.shared.infrastructure.adapter.in.web;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * Controlador que solo existe en pruebas para ejercitar {@link TraceIdFilter}.
 */
@Slf4j
@RestController
class TraceIdTestController {

    @GetMapping("/test/trace")
    Mono<ApiResponse<String>> trace() {
        return ApiResponse.from(Mono.just("ok")
                .doOnNext(value -> log.info("dentro")));
    }

    @GetMapping("/test/trace/empty")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    Mono<Void> empty() {
        return Mono.empty();
    }

    @GetMapping("/test/trace/delayed")
    Mono<ApiResponse<String>> delayed() {
        return ApiResponse.from(Mono.delay(Duration.ofMillis(50))
                .then(TraceIdContext.current()));
    }
}