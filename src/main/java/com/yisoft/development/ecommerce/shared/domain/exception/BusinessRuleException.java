package com.yisoft.development.ecommerce.shared.domain.exception;

public non-sealed class BusinessRuleException extends DomainException {

    public BusinessRuleException(String problemType, String title, String detail) {
        super(problemType, title, detail);
    }
}
