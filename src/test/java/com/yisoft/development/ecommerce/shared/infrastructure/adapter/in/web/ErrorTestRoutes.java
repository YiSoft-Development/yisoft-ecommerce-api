package com.yisoft.development.ecommerce.shared.infrastructure.adapter.in.web;

import com.yisoft.development.ecommerce.shared.domain.exception.ConflictException;
import com.yisoft.development.ecommerce.shared.domain.exception.NotFoundException;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

@TestConfiguration
class ErrorTestRoutes {

    static final String SENSITIVE_MESSAGE = "Connection to db-prod-01 refused";

    record TestRequest(@NotBlank String name, @Email String email) {
    }

    @Bean
    RouterFunction<ServerResponse> errorTestRouter(RequestValidator requestValidator) {
        return RouterFunctions.route()
                .POST("/test/errors/validation", request -> request.bodyToMono(TestRequest.class)
                        .flatMap(requestValidator::validate)
                        .flatMap(body -> ServerResponse.noContent().build()))
                .GET("/test/errors/not-found", request -> Mono.error(
                        new NotFoundException("test-not-found", "Test not found", "The test resource does not exist.")))
                .GET("/test/errors/conflict", request -> Mono.error(
                        new ConflictException("test-conflict", "Test conflict", "The test resource already exists.")))
                .GET("/test/errors/unexpected", request -> Mono.error(new IllegalStateException(SENSITIVE_MESSAGE)))
                .build();
    }
}
