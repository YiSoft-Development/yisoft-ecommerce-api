package com.yisoft.development.ecommerce.shared.infrastructure.adapter.in.web;

import lombok.Getter;

import java.util.List;

@Getter
public class RequestValidationException extends RuntimeException {

    private final List<FieldViolation> violations;

    public RequestValidationException(List<FieldViolation> fieldViolations) {
        super("The request contains invalid fields.");
        this.violations = List.copyOf(fieldViolations);
    }
}
