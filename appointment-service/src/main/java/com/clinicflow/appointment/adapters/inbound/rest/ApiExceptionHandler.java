package com.clinicflow.appointment.adapters.inbound.rest;

import com.clinicflow.appointment.application.exceptions.AppointmentAccessDeniedException;
import com.clinicflow.appointment.application.exceptions.AppointmentNotFoundException;
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

    @ExceptionHandler(AppointmentNotFoundException.class)
    ProblemDetail notFound(AppointmentNotFoundException ex) {
        ProblemDetail detail = ProblemDetail.forStatus(HttpStatus.NOT_FOUND);
        detail.setTitle("Resource not found");
        detail.setDetail("Appointment not found");
        return detail;
    }

    @ExceptionHandler({AppointmentAccessDeniedException.class, AccessDeniedException.class})
    ProblemDetail denied(Exception ex) {
        ProblemDetail detail = ProblemDetail.forStatus(HttpStatus.FORBIDDEN);
        detail.setTitle("Access denied");
        detail.setDetail("You are not allowed to access this resource");
        return detail;
    }

    @ExceptionHandler({IllegalStateException.class, IllegalArgumentException.class, MethodArgumentNotValidException.class})
    ProblemDetail badRequest(Exception ex) {
        ProblemDetail detail = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        detail.setTitle("Invalid request");
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
