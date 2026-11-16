package com.yisoft.development.ecommerce.shared.infrastructure.adapter.in.web;

import com.yisoft.development.ecommerce.shared.domain.exception.BusinessRuleException;
import com.yisoft.development.ecommerce.shared.domain.exception.ConflictException;
import com.yisoft.development.ecommerce.shared.domain.exception.NotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebInputException;
import org.springframework.web.server.UnsupportedMediaTypeStatusException;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ProblemDetailMapperTests {

    private static final Instant NOW = Instant.parse("2026-11-16T06:30:00Z");
    private static final String TRACE_ID = "3f2b8c1e-7a4d-4e9b-9c1a-2d5e6f708192";
    private static final URI ABOUT_BLANK = URI.create("about:blank");

    private final ProblemDetailMapper mapper = new ProblemDetailMapper(Clock.fixed(NOW, ZoneOffset.UTC));

    static final class TestNotFoundException extends NotFoundException {

        TestNotFoundException() {
            super("test-not-found", "Test not found", "The test resource does not exist.");
        }
    }

    @Test
    void mapsNotFound() {
        ProblemDetail problem = mapper.toProblemDetail(new TestNotFoundException(), TRACE_ID);

        assertThat(problem.getStatus()).isEqualTo(404);
        assertThat(problem.getType()).isEqualTo(problemType("test-not-found"));
        assertThat(problem.getTitle()).isEqualTo("Test not found");
        assertThat(problem.getDetail()).isEqualTo("The test resource does not exist.");
    }

    @Test
    void mapsConflict() {
        ProblemDetail problem = mapper.toProblemDetail(
                new ConflictException("test-conflict", "Test conflict", "The test resource already exists."), TRACE_ID);

        assertThat(problem.getStatus()).isEqualTo(409);
        assertThat(problem.getType()).isEqualTo(problemType("test-conflict"));
        assertThat(problem.getTitle()).isEqualTo("Test conflict");
    }

    @Test
    void mapsBusinessRule() {
        ProblemDetail problem = mapper.toProblemDetail(
                new BusinessRuleException("test-rule", "Test rule", "The test rule was violated."), TRACE_ID);

        assertThat(problem.getStatus()).isEqualTo(422);
        assertThat(problem.getType()).isEqualTo(problemType("test-rule"));
        assertThat(problem.getTitle()).isEqualTo("Test rule");
    }

    @Test
    void mapsValidation() {
        List<FieldViolation> violations = List.of(
                new FieldViolation("#/email", "must be a well-formed email address"),
                new FieldViolation("#/name", "must not be blank"));

        ProblemDetail problem = mapper.toProblemDetail(new RequestValidationException(violations), TRACE_ID);

        assertThat(problem.getStatus()).isEqualTo(400);
        assertThat(problem.getType()).isEqualTo(problemType("validation-error"));
        assertThat(problem.getTitle()).isEqualTo("Validation failed");
        assertThat(problem.getProperties()).containsEntry("errors", violations);
    }

    @Test
    void mapsInvalidTraceId() {
        ProblemDetail problem = mapper.toProblemDetail(new InvalidTraceIdException(), TRACE_ID);

        assertThat(problem.getStatus()).isEqualTo(400);
        assertThat(problem.getType()).isEqualTo(problemType("invalid-trace-id"));
    }

    @Test
    void mapsMalformedRequest() {
        ProblemDetail problem = mapper.toProblemDetail(new ServerWebInputException("Unexpected character"), TRACE_ID);

        assertThat(problem.getStatus()).isEqualTo(400);
        assertThat(problem.getType()).isEqualTo(problemType("malformed-request"));
        assertThat(problem.getDetail()).doesNotContain("Unexpected character");
    }

    @Test
    void mapsUnsupportedMediaType() {
        ProblemDetail problem = mapper.toProblemDetail(new UnsupportedMediaTypeStatusException("text/plain"), TRACE_ID);

        assertThat(problem.getStatus()).isEqualTo(415);
        assertThat(problem.getType()).isEqualTo(ABOUT_BLANK);
        assertThat(problem.getTitle()).isEqualTo("Unsupported Media Type");
    }

    @Test
    void mapsRouteNotFound() {
        ProblemDetail problem = mapper.toProblemDetail(new ResponseStatusException(HttpStatus.NOT_FOUND), TRACE_ID);

        assertThat(problem.getStatus()).isEqualTo(404);
        assertThat(problem.getType()).isEqualTo(ABOUT_BLANK);
        assertThat(problem.getTitle()).isEqualTo("Not Found");
    }

    @Test
    void mapsUnexpectedErrorWithoutLeakingMessage() {
        ProblemDetail problem = mapper.toProblemDetail(
                new IllegalStateException("SELECT * FROM customers failed"), TRACE_ID);

        assertThat(problem.getStatus()).isEqualTo(500);
        assertThat(problem.getType()).isEqualTo(ABOUT_BLANK);
        assertThat(problem.getTitle()).isEqualTo("Internal Server Error");
        assertThat(problem.getDetail())
                .isEqualTo(ProblemDetailMapper.INTERNAL_ERROR_DETAIL)
                .doesNotContain("SELECT");
    }

    @Test
    void addsCommonFields() {
        ProblemDetail problem = mapper.toProblemDetail(new TestNotFoundException(), TRACE_ID);

        assertThat(problem.getInstance()).isEqualTo(URI.create("urn:uuid:" + TRACE_ID));
        assertThat(problem.getProperties())
                .containsEntry("traceId", TRACE_ID)
                .containsEntry("timestamp", NOW);
    }

    private static URI problemType(String problemType) {
        return URI.create(ProblemDetailMapper.PROBLEM_TYPE_BASE + problemType);
    }
}
