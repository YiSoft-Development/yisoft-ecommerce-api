package com.yisoft.development.ecommerce.shared.infrastructure.health;

import com.yisoft.development.ecommerce.shared.infrastructure.adapter.in.web.TraceIdContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest
class ActuatorHealthTests {

    @Autowired
    private ApplicationContext applicationContext;

    private WebTestClient webTestClient;

    @BeforeEach
    void setUp() {
        webTestClient = WebTestClient.bindToApplicationContext(applicationContext).build();
    }

    @Test
    void healthIsUp() {
        webTestClient.get()
                .uri("/actuator/health")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("UP");
    }

    @Test
    void livenessIsUp() {
        webTestClient.get()
                .uri("/actuator/health/liveness")
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void readinessIsUp() {
        webTestClient.get()
                .uri("/actuator/health/readiness")
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void healthHasTraceIdHeader() {
        webTestClient.get()
                .uri("/actuator/health")
                .exchange()
                .expectHeader().exists(TraceIdContext.TRACE_ID_HEADER);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/actuator/env", "/actuator/beans"})
    void otherActuatorEndpointsAreNotExposed(String path) {
        webTestClient.get()
                .uri(path)
                .exchange()
                .expectStatus().isNotFound();
    }
}
