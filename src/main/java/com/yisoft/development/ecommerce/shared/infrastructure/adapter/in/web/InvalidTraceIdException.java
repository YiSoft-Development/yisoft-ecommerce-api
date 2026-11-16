package com.yisoft.development.ecommerce.shared.infrastructure.adapter.in.web;

public class InvalidTraceIdException extends RuntimeException {
    public InvalidTraceIdException() {
        super("X-Trace-Id must be a valid UUID");
    }
}
