package com.yisoft.development.ecommerce.shared.infrastructure.adapter.in.web;

import com.yisoft.development.ecommerce.shared.infrastructure.config.ClockConfiguration;
import com.yisoft.development.ecommerce.shared.infrastructure.config.TraceIdLoggingConfiguration;
import com.yisoft.development.ecommerce.shared.infrastructure.config.ValidationConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webflux.test.autoconfigure.WebFluxTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@WebFluxTest
@Import({
        ErrorTestRoutes.class,
        GlobalErrorHandler.class,
        ProblemDetailMapper.class,
        RequestValidator.class,
        ClockConfiguration.class,
        ValidationConfiguration.class,
        TraceIdLoggingConfiguration.class
})
class GlobalErrorHandlerTests {

    private static final String HEADER = TraceIdContext.TRACE_ID_HEADER;

    @Autowired
    private WebTestClient webTestClient;

    @Test
    void validBodyReturnsNoContent() {
        webTestClient.post()
                .uri("/test/errors/validation")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"name\":\"Ana\",\"email\":\"ana@correo.com\"}")
                .exchange()
                .expectStatus().isNoContent();
    }

    @Test
    void invalidBodyReturnsValidationError() {
        webTestClient.post()
                .uri("/test/errors/validation")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"name\":\"\",\"email\":\"abc\"}")
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.type").isEqualTo(problemType("validation-error"))
                .jsonPath("$.errors.length()").isEqualTo(2)
                .jsonPath("$.errors[0].pointer").isEqualTo("#/email")
                .jsonPath("$.errors[1].pointer").isEqualTo("#/name")
                .jsonPath("$.errors[0].detail").exists();
    }

    @Test
    void malformedJsonReturnsMalformedRequest() {
        webTestClient.post()
                .uri("/test/errors/validation")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"name\":")
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.type").isEqualTo(problemType("malformed-request"));
    }

    @Test
    void unknownRouteReturnsNotFound() {
        webTestClient.get()
                .uri("/test/errors/missing")
                .exchange()
                .expectStatus().isNotFound()
                .expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody()
                .jsonPath("$.title").isEqualTo("Not Found");
    }

    @Test
    void domainErrorReturnsProblemDetail() {
        webTestClient.get()
                .uri("/test/errors/not-found")
                .exchange()
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.type").isEqualTo(problemType("test-not-found"))
                .jsonPath("$.title").isEqualTo("Test not found")
                .jsonPath("$.detail").isEqualTo("The test resource does not exist.");
    }

    @Test
    void conflictErrorReturns409() {
        webTestClient.get()
                .uri("/test/errors/conflict")
                .exchange()
                .expectStatus().isEqualTo(409)
                .expectBody()
                .jsonPath("$.type").isEqualTo(problemType("test-conflict"));
    }

    @Test
    void unexpectedErrorReturns500() {
        webTestClient.get()
                .uri("/test/errors/unexpected")
                .exchange()
                .expectStatus().is5xxServerError()
                .expectBody()
                .jsonPath("$.detail").isEqualTo(ProblemDetailMapper.INTERNAL_ERROR_DETAIL)
                .consumeWith(result -> assertThat(new String(result.getResponseBody(), StandardCharsets.UTF_8))
                        .doesNotContain(ErrorTestRoutes.SENSITIVE_MESSAGE));
    }

    @Test
    void invalidTraceIdReturnsProblemDetail() {
        webTestClient.get()
                .uri("/test/errors/not-found")
                .header(HEADER, "abc")
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.type").isEqualTo(problemType("invalid-trace-id"));
    }

    @Test
    void problemResponseHasCommonFields() {
        webTestClient.get()
                .uri("/test/errors/not-found")
                .exchange()
                .expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody()
                .jsonPath("$.type").exists()
                .jsonPath("$.title").exists()
                .jsonPath("$.status").isEqualTo(404)
                .jsonPath("$.instance").exists()
                .jsonPath("$.timestamp").exists()
                .jsonPath("$.traceId").exists()
                .jsonPath("$.properties").doesNotExist();
    }

    @Test
    void errorTraceIdMatchesHeader() {
        String traceId = UUID.randomUUID().toString();

        webTestClient.get()
                .uri("/test/errors/not-found")
                .header(HEADER, traceId)
                .exchange()
                .expectHeader().valueEquals(HEADER, traceId)
                .expectBody()
                .jsonPath("$.traceId").isEqualTo(traceId)
                .jsonPath("$.instance").isEqualTo("urn:uuid:" + traceId);
    }

    private static String problemType(String problemType) {
        return ProblemDetailMapper.PROBLEM_TYPE_BASE + problemType;
    }
}
