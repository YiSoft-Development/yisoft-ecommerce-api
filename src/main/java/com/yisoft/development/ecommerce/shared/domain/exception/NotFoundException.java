package com.yisoft.development.ecommerce.shared.domain.exception;

public non-sealed class NotFoundException extends DomainException {

    public NotFoundException(String problemType, String title, String detail) {
        super(problemType, title, detail);
    }
}
