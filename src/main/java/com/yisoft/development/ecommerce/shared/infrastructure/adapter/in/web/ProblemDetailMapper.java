package com.yisoft.development.ecommerce.shared.infrastructure.adapter.in.web;

import com.yisoft.development.ecommerce.shared.domain.exception.BusinessRuleException;
import com.yisoft.development.ecommerce.shared.domain.exception.ConflictException;
import com.yisoft.development.ecommerce.shared.domain.exception.DomainException;
import com.yisoft.development.ecommerce.shared.domain.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebInputException;

import java.net.URI;
import java.time.Clock;

@Component
@RequiredArgsConstructor
public class ProblemDetailMapper {

    public static final String PROBLEM_TYPE_BASE = "tag:yisoft-development.com,2026:problems/";

    static final URI ABOUT_BLANK = URI.create("about:blank");

    static final String INTERNAL_ERROR_DETAIL = "An unexpected error occurred.";
    static final String MALFORMED_REQUEST_DETAIL = "The request body could not be read.";

    private final Clock clock;

    public ProblemDetail toProblemDetail(Throwable error, String traceId) {
        ProblemDetail problem = switch (error) {
            case DomainException domainError -> fromDomain(domainError);
            case RequestValidationException validationError -> fromValidation(validationError);
            case InvalidTraceIdException invalidTraceId ->
                    typed(HttpStatus.BAD_REQUEST, "invalid-trace-id", "Invalid trace ID", invalidTraceId.getMessage());
            case ServerWebInputException _ ->
                    typed(HttpStatus.BAD_REQUEST, "malformed-request", "Malformed request", MALFORMED_REQUEST_DETAIL);
            case ResponseStatusException statusError -> blank(statusError.getStatusCode(), statusError.getReason());
            default -> blank(HttpStatus.INTERNAL_SERVER_ERROR, INTERNAL_ERROR_DETAIL);
        };

        problem.setProperty("timestamp", clock.instant());
        if (traceId != null) {
            problem.setInstance(URI.create("urn:uuid:" + traceId));
            problem.setProperty("traceId", traceId);
        }
        return problem;
    }

    private static ProblemDetail fromDomain(DomainException error) {
        HttpStatusCode status = switch (error) {
            case NotFoundException _ -> HttpStatus.NOT_FOUND;
            case ConflictException _ -> HttpStatus.CONFLICT;
            case BusinessRuleException _ -> HttpStatusCode.valueOf(422);
        };
        return typed(status, error.getProblemType(), error.getTitle(), error.getMessage());
    }

    private static ProblemDetail fromValidation(RequestValidationException error) {
        ProblemDetail problem =
                typed(HttpStatus.BAD_REQUEST, "validation-error", "Validation failed", error.getMessage());
        problem.setProperty("errors", error.getViolations());
        return problem;
    }

    private static ProblemDetail typed(HttpStatusCode status, String problemType, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create(PROBLEM_TYPE_BASE + problemType));
        problem.setTitle(title);
        return problem;
    }

    private static ProblemDetail blank(HttpStatusCode status, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(ABOUT_BLANK);
        HttpStatus resolved = HttpStatus.resolve(status.value());
        if (resolved != null) {
            problem.setTitle(resolved.getReasonPhrase());
        }
        return problem;
    }
}
