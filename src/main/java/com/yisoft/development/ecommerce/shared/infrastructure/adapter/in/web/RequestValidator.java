package com.yisoft.development.ecommerce.shared.infrastructure.adapter.in.web;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ElementKind;
import jakarta.validation.Path;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Comparator;
import java.util.List;

@Component
@RequiredArgsConstructor
public class RequestValidator {

    private static final Comparator<FieldViolation> BY_POINTER =
            Comparator.comparing(FieldViolation::pointer).thenComparing(FieldViolation::detail);

    private final Validator validator;

    public <T> Mono<T> validate(T body) {
        List<FieldViolation> violations = validator.validate(body).stream()
                .map(RequestValidator::toFieldViolation)
                .sorted(BY_POINTER)
                .toList();

        if (violations.isEmpty()) {
            return Mono.just(body);
        }
        return Mono.error(new RequestValidationException(violations));
    }

    private static FieldViolation toFieldViolation(ConstraintViolation<?> violation) {
        return new FieldViolation(toPointer(violation.getPropertyPath()), violation.getMessage());
    }

    private static String toPointer(Path path) {
        StringBuilder pointer = new StringBuilder("#");
        for (Path.Node node : path) {
            if (node.getIndex() != null) {
                pointer.append('/').append(node.getIndex());
            } else if (node.getKey() != null) {
                pointer.append('/').append(node.getKey());
            }
            if (node.getName() != null && node.getKind() != ElementKind.CONTAINER_ELEMENT) {
                pointer.append('/').append(node.getName());
            }
        }
        return pointer.toString();
    }
}
