package com.clinicflow.patient.adapters.inbound.rest;

import com.clinicflow.patient.application.exceptions.PatientAccessDeniedException;
import com.clinicflow.patient.application.exceptions.PatientNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(PatientNotFoundException.class)
    ProblemDetail notFound(PatientNotFoundException ex) {
        ProblemDetail detail = ProblemDetail.forStatus(HttpStatus.NOT_FOUND);
        detail.setTitle("Resource not found");
        detail.setDetail("Patient not found");
        return detail;
    }

    @ExceptionHandler({PatientAccessDeniedException.class, AccessDeniedException.class})
    ProblemDetail denied(Exception ex) {
        ProblemDetail detail = ProblemDetail.forStatus(HttpStatus.FORBIDDEN);
        detail.setTitle("Access denied");
        detail.setDetail("You are not allowed to access this resource");
        return detail;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail validation(MethodArgumentNotValidException ex) {
        ProblemDetail detail = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        detail.setTitle("Validation failed");
        detail.setDetail("Request validation failed");
        return detail;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail domainValidation(IllegalArgumentException ex) {
        ProblemDetail detail = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        detail.setTitle("Validation failed");
        detail.setDetail("Request validation failed");
        return detail;
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail unexpected(Exception ex, HttpServletRequest request) {
        log.error("unhandled_exception path={}", request.getRequestURI(), ex);
        ProblemDetail detail = ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR);
        detail.setTitle("Internal server error");
        detail.setDetail("The request could not be completed");
        return detail;
    }
}
