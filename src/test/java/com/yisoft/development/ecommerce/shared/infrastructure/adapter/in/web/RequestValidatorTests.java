package com.yisoft.development.ecommerce.shared.infrastructure.adapter.in.web;

import com.yisoft.development.ecommerce.shared.infrastructure.config.ValidationConfiguration;
import jakarta.validation.Valid;
import jakarta.validation.ValidatorFactory;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

class RequestValidatorTests {

    record Item(@Positive int quantity) {
    }

    record SignUpRequest(@NotBlank String name, @Email String email, List<@Valid Item> items) {
    }

    record TagsRequest(List<@NotBlank String> tags) {
    }

    private static ValidatorFactory validatorFactory;

    private RequestValidator requestValidator;

    @BeforeAll
    static void createValidatorFactory() {
        validatorFactory = new ValidationConfiguration().validatorFactory();
    }

    @AfterAll
    static void closeValidatorFactory() {
        validatorFactory.close();
    }

    @BeforeEach
    void setUp() {
        requestValidator = new RequestValidator(validatorFactory.getValidator());
    }

    @Test
    void validBodyPasses() {
        SignUpRequest request = new SignUpRequest("Ana", "ana@correo.com", List.of(new Item(1)));

        StepVerifier.create(requestValidator.validate(request))
                .expectNext(request)
                .verifyComplete();
    }

    @Test
    void invalidBodyFails() {
        assertViolations(requestValidator.validate(invalidRequest()),
                violations -> assertThat(violations).hasSize(3));
    }

    @Test
    void violationsAreSortedByPointer() {
        assertViolations(requestValidator.validate(invalidRequest()),
                violations -> assertThat(violations)
                        .extracting(FieldViolation::pointer)
                        .containsExactly("#/email", "#/items/0/quantity", "#/name"));
    }

    @Test
    void nestedListPathBecomesJsonPointer() {
        SignUpRequest request = new SignUpRequest("Ana", "ana@correo.com", List.of(new Item(1), new Item(0)));

        assertViolations(requestValidator.validate(request),
                violations -> assertThat(violations)
                        .extracting(FieldViolation::pointer)
                        .containsExactly("#/items/1/quantity"));
    }

    @Test
    void containerElementPathBecomesJsonPointer() {
        assertViolations(requestValidator.validate(new TagsRequest(List.of("ok", " "))),
                violations -> assertThat(violations)
                        .extracting(FieldViolation::pointer)
                        .containsExactly("#/tags/1"));
    }

    @Test
    void messagesAreInEnglish() {
        Locale previous = Locale.getDefault();
        Locale.setDefault(Locale.forLanguageTag("es-MX"));
        try {
            SignUpRequest request = new SignUpRequest("", "ana@correo.com", List.of());

            assertViolations(requestValidator.validate(request),
                    violations -> assertThat(violations)
                            .extracting(FieldViolation::detail)
                            .containsExactly("must not be blank"));
        } finally {
            Locale.setDefault(previous);
        }
    }

    private static SignUpRequest invalidRequest() {
        return new SignUpRequest("", "abc", List.of(new Item(0)));
    }

    private static void assertViolations(Mono<?> result, Consumer<List<FieldViolation>> assertion) {
        StepVerifier.create(result)
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOfSatisfying(RequestValidationException.class,
                                exception -> assertion.accept(exception.getViolations())))
                .verify();
    }
}
