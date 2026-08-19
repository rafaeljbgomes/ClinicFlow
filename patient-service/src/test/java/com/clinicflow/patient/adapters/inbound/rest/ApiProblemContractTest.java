package com.clinicflow.patient.adapters.inbound.rest;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.validation.MapBindingResult;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class ApiProblemContractTest {
    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    @Test
    void mapsFrameworkRequestFailuresToStableProblemCodes() {
        assertProblem(handler.malformed(new HttpMessageNotReadableException(
                "bad json", new MockHttpInputMessage(new byte[0]))),
                HttpStatus.BAD_REQUEST, "malformed_request");
        assertProblem(handler.invalidParameter(new MethodArgumentTypeMismatchException(
                "not-a-uuid", UUID.class, "id", mock(MethodParameter.class), new IllegalArgumentException())),
                HttpStatus.BAD_REQUEST, "invalid_parameter");
        assertProblem(handler.missingParameter(new MissingServletRequestParameterException("id", "UUID")),
                HttpStatus.BAD_REQUEST, "missing_parameter");
        assertProblem(handler.methodNotAllowed(new HttpRequestMethodNotSupportedException("TRACE")),
                HttpStatus.METHOD_NOT_ALLOWED, "method_not_allowed");
        assertProblem(handler.unsupportedMediaType(new HttpMediaTypeNotSupportedException("text/plain")),
                HttpStatus.UNSUPPORTED_MEDIA_TYPE, "unsupported_media_type");
    }

    @Test
    void exposesGroupedFieldErrorsWithoutLosingTheProblemContract() {
        MapBindingResult bindingResult = new MapBindingResult(new HashMap<>(), "request");
        bindingResult.rejectValue("email", "invalid", "Must be a valid email");
        ProblemDetail problem = handler.validation(new MethodArgumentNotValidException(
                mock(MethodParameter.class), bindingResult));

        assertProblem(problem, HttpStatus.BAD_REQUEST, "validation_failed");
        assertThat(problem.getProperties()).containsEntry(
                "fieldErrors", Map.of("email", java.util.List.of("Must be a valid email")));
    }

    private static void assertProblem(ProblemDetail problem, HttpStatus status, String code) {
        assertThat(problem.getStatus()).isEqualTo(status.value());
        assertThat(problem.getType()).hasToString("urn:clinicflow:problem:" + code);
        assertThat(problem.getTitle()).isNotBlank();
        assertThat(problem.getDetail()).isNotBlank();
        assertThat(problem.getInstance()).isNotNull();
        assertThat(problem.getProperties())
                .containsEntry("code", code)
                .containsKey("correlationId");
    }
}
