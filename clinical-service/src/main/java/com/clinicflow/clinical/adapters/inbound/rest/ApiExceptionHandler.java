package com.clinicflow.clinical.adapters.inbound.rest;

import com.clinicflow.clinical.application.exceptions.ActiveClinicalCaseExistsException;
import com.clinicflow.clinical.application.exceptions.CarePlanNotFoundException;
import com.clinicflow.clinical.application.exceptions.ClinicalAccessDeniedException;
import com.clinicflow.clinical.application.exceptions.ClinicalCaseNotFoundException;
import com.clinicflow.clinical.application.exceptions.SessionRecordNotFoundException;
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

    @ExceptionHandler({ClinicalCaseNotFoundException.class, CarePlanNotFoundException.class,
            SessionRecordNotFoundException.class})
    ProblemDetail notFound(RuntimeException ex) {
        ProblemDetail detail = ProblemDetail.forStatus(HttpStatus.NOT_FOUND);
        detail.setTitle("Clinical resource not found");
        detail.setDetail("Clinical resource not found");
        return detail;
    }

    @ExceptionHandler(ActiveClinicalCaseExistsException.class)
    ProblemDetail conflict(ActiveClinicalCaseExistsException ex) {
        ProblemDetail detail = ProblemDetail.forStatus(HttpStatus.CONFLICT);
        detail.setTitle("Active clinical case exists");
        detail.setDetail("Patient already has an active clinical case for this psychologist");
        return detail;
    }

    @ExceptionHandler({ClinicalAccessDeniedException.class, AccessDeniedException.class})
    ProblemDetail denied(Exception ex) {
        ProblemDetail detail = ProblemDetail.forStatus(HttpStatus.FORBIDDEN);
        detail.setTitle("Access denied");
        detail.setDetail("You do not have access to this clinical resource");
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
        log.error("clinical_unexpected_error path={}", request.getRequestURI(), ex);
        ProblemDetail detail = ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR);
        detail.setTitle("Internal server error");
        detail.setDetail("Unexpected clinical service error");
        return detail;
    }
}
