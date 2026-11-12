package com.yisoft.development.ecommerce.shared.infrastructure.config;

import com.yisoft.development.ecommerce.shared.infrastructure.adapter.in.web.TraceIdContext;
import io.micrometer.context.ContextRegistry;
import org.slf4j.MDC;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Hooks;

/**
 * TraceId MDC Configuration
 */
@Configuration
public class TraceIdLoggingConfiguration {



    public TraceIdLoggingConfiguration() {
        Hooks.enableAutomaticContextPropagation();

        ContextRegistry.getInstance()
                .registerThreadLocalAccessor(
                        TraceIdContext.KEY,
                        () -> MDC.get(TraceIdContext.MDC_KEY),
                        value -> MDC.put(TraceIdContext.MDC_KEY, value),
                        () -> MDC.remove(TraceIdContext.MDC_KEY)
                );
    }
}
