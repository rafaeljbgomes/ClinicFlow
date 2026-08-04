package com.clinicflow.notification.adapters.inbound.rest;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toList;

final class ApiProblems {
    private static final String CORRELATION_HEADER = "X-Correlation-Id";
    private static final String CORRELATION_MDC_KEY = "correlation.id";

    private ApiProblems() {
    }

    static ProblemDetail create(HttpStatus status, String code, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create("urn:clinicflow:problem:" + code));
        problem.setTitle(title);
        problem.setInstance(URI.create(currentRequest().map(HttpServletRequest::getRequestURI).orElse("/")));
        problem.setProperty("code", code);
        problem.setProperty("correlationId", correlationId());
        return problem;
    }

    static ProblemDetail validation(MethodArgumentNotValidException exception) {
        ProblemDetail problem = create(
                HttpStatus.BAD_REQUEST,
                "validation_failed",
                "Validation failed",
                "Request validation failed");
        Map<String, List<String>> fieldErrors = exception.getBindingResult() == null
                ? Map.of()
                : exception.getBindingResult().getFieldErrors().stream()
                .collect(groupingBy(
                        error -> error.getField(),
                        LinkedHashMap::new,
                        mapping(error -> error.getDefaultMessage() == null ? "Invalid value" : error.getDefaultMessage(),
                                toList())));
        if (!fieldErrors.isEmpty()) {
            problem.setProperty("fieldErrors", fieldErrors);
        }
        return problem;
    }

    private static String correlationId() {
        String correlationId = MDC.get(CORRELATION_MDC_KEY);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = currentRequest().map(request -> request.getHeader(CORRELATION_HEADER)).orElse(null);
        }
        return correlationId == null || correlationId.isBlank() ? UUID.randomUUID().toString() : correlationId;
    }

    private static java.util.Optional<HttpServletRequest> currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return java.util.Optional.of(attributes.getRequest());
        }
        return java.util.Optional.empty();
    }
}
