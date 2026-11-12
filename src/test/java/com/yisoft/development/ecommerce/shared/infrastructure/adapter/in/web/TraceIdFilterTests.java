package com.yisoft.development.ecommerce.shared.infrastructure.adapter.in.web;

import com.yisoft.development.ecommerce.shared.infrastructure.config.TraceIdLoggingConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webflux.test.autoconfigure.WebFluxTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.test.web.reactive.server.EntityExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pruebas de {@link TraceIdFilter}: generación, reutilización y rechazo del trace ID,
 * su presencia en header y cuerpo, el aislamiento entre peticiones concurrentes y su registro en logs.
 */
@WebFluxTest(controllers = TraceIdTestController.class)
@Import(TraceIdLoggingConfiguration.class)
@ExtendWith(OutputCaptureExtension.class)
class TraceIdFilterTests {

    private static final String HEADER = TraceIdContext.TRACE_ID_HEADER;
    private static final String UUID_REGEX =
            "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";
    private static final ParameterizedTypeReference<ApiResponse<String>> RESPONSE_TYPE =
            new ParameterizedTypeReference<>() {};

    @Autowired
    private WebTestClient webTestClient;

    @Test
    void generatesTraceIdWhenHeaderIsMissing() {
        webTestClient.get()
                .uri("/test/trace")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().value(HEADER, value -> assertThat(value).matches(UUID_REGEX));
    }

    @Test
    void reusesValidTraceIdFromRequest() {
        String traceId = UUID.randomUUID().toString();

        webTestClient.get()
                .uri("/test/trace")
                .header(HEADER, traceId)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals(HEADER, traceId);
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "1-1-1-1-1"})
    void rejectsInvalidTraceId(String invalidTraceId) {
        webTestClient.get()
                .uri("/test/trace")
                .header(HEADER, invalidTraceId)
                .exchange()
                .expectStatus().isBadRequest()
                .expectHeader().value(HEADER, value -> assertThat(value)
                        .matches(UUID_REGEX)
                        .isNotEqualTo(invalidTraceId));
    }

    @Test
    void bodyTraceIdMatchesHeader() {
        EntityExchangeResult<ApiResponse<String>> result = webTestClient.get()
                .uri("/test/trace")
                .exchange()
                .expectStatus().isOk()
                .expectBody(RESPONSE_TYPE)
                .returnResult();

        String headerTraceId = result.getResponseHeaders().getFirst(HEADER);
        ApiResponse<String> body = result.getResponseBody();

        assertThat(body).isNotNull();
        assertThat(body.traceId()).isEqualTo(headerTraceId);
        assertThat(body.data()).isEqualTo("ok");
    }

    @Test
    void noContentResponseHasTraceIdHeader() {
        webTestClient.get()
                .uri("/test/trace/empty")
                .exchange()
                .expectStatus().isNoContent()
                .expectHeader().value(HEADER, value -> assertThat(value).matches(UUID_REGEX))
                .expectBody().isEmpty();
    }

    @Test
    void concurrentRequestsKeepTheirOwnTraceId() throws Exception {
        List<String> traceIds = IntStream.range(0, 50)
                .mapToObj(index -> UUID.randomUUID().toString())
                .toList();

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<? extends Future<?>> futures = traceIds.stream()
                    .map(traceId -> executor.submit(() -> assertTraceIdIsKept(traceId)))
                    .toList();

            for (Future<?> future : futures) {
                future.get();
            }
        }
    }

    @Test
    void logIncludesTraceId(CapturedOutput output) {
        String traceId = UUID.randomUUID().toString();

        webTestClient.get()
                .uri("/test/trace")
                .header(HEADER, traceId)
                .exchange()
                .expectStatus().isOk();

        assertThat(output.getAll().lines())
                .anyMatch(line -> line.contains("[" + traceId + "]") && line.contains("dentro"));
    }

    private void assertTraceIdIsKept(String traceId) {
        ApiResponse<String> body = webTestClient.get()
                .uri("/test/trace/delayed")
                .header(HEADER, traceId)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals(HEADER, traceId)
                .expectBody(RESPONSE_TYPE)
                .returnResult()
                .getResponseBody();

        assertThat(body).isNotNull();
        assertThat(body.traceId()).isEqualTo(traceId);
        assertThat(body.data()).isEqualTo(traceId);
    }
}