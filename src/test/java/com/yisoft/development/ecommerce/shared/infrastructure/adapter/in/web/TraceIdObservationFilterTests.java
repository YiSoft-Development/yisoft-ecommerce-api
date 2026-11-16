package com.yisoft.development.ecommerce.shared.infrastructure.adapter.in.web;

import io.micrometer.observation.Observation;
import org.junit.jupiter.api.Test;
import org.springframework.http.server.reactive.observation.ServerRequestObservationContext;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpResponse;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TraceIdObservationFilterTests {

    private static final String TRACE_ID = "3f2b8c1e-7a4d-4e9b-9c1a-2d5e6f708192";

    private final TraceIdObservationFilter filter = new TraceIdObservationFilter();

    @Test
    void addsTraceIdToServerObservation() {
        ServerRequestObservationContext context = serverContext(Map.of(TraceIdContext.KEY, TRACE_ID));

        filter.map(context);

        assertThat(context.getHighCardinalityKeyValue(TraceIdObservationFilter.TRACE_ID_KEY))
                .isNotNull()
                .satisfies(keyValue -> assertThat(keyValue.getValue()).isEqualTo(TRACE_ID));
    }

    @Test
    void ignoresOtherContexts() {
        Observation.Context context = new Observation.Context();

        filter.map(context);

        assertThat(context.getHighCardinalityKeyValues()).isEmpty();
    }

    @Test
    void ignoresMissingTraceId() {
        ServerRequestObservationContext context = serverContext(Map.of());

        filter.map(context);

        assertThat(context.getHighCardinalityKeyValue(TraceIdObservationFilter.TRACE_ID_KEY)).isNull();
    }

    private static ServerRequestObservationContext serverContext(Map<String, Object> attributes) {
        return new ServerRequestObservationContext(
                MockServerHttpRequest.get("/test").build(),
                new MockServerHttpResponse(),
                new HashMap<>(attributes));
    }
}
