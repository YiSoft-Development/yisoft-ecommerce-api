package com.yisoft.development.ecommerce.shared.domain.exception;

public non-sealed class ConflictException extends DomainException {

    public ConflictException(String problemType, String title, String detail) {
        super(problemType, title, detail);
    }
}
