package com.yisoft.development.ecommerce.shared.infrastructure.adapter.in.web;

import io.micrometer.common.KeyValue;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationFilter;
import org.springframework.http.server.reactive.observation.ServerRequestObservationContext;
import org.springframework.stereotype.Component;

@Component
public class TraceIdObservationFilter implements ObservationFilter {

    public static final String TRACE_ID_KEY = "x_trace_id";

    @Override
    public Observation.Context map(Observation.Context context) {
        if (context instanceof ServerRequestObservationContext serverContext
                && serverContext.getAttributes().get(TraceIdContext.KEY) instanceof String traceId) {
            serverContext.addHighCardinalityKeyValue(KeyValue.of(TRACE_ID_KEY, traceId));
        }
        return context;
    }
}
