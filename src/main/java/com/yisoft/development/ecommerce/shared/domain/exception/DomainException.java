package com.yisoft.development.ecommerce.shared.domain.exception;

import lombok.Getter;

@Getter
public abstract sealed class DomainException
        extends RuntimeException
        permits BusinessRuleException, ConflictException, NotFoundException{

    private final String problemType;
    private final String title;

    protected DomainException(String problemType, String title, String detail) {
        super(detail);
        this.problemType = problemType;
        this.title = title;
    }
}
