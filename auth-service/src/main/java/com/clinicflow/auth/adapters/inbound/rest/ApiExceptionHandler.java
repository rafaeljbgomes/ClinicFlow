package com.clinicflow.auth.adapters.inbound.rest;

import com.clinicflow.auth.application.exceptions.DuplicateEmailException;
import com.clinicflow.auth.application.exceptions.InvalidCredentialsException;
import com.clinicflow.auth.application.exceptions.UserNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(DuplicateEmailException.class)
    ProblemDetail duplicateEmail(DuplicateEmailException ex) {
        return ApiProblems.create(HttpStatus.CONFLICT, "duplicate_email", "Registration failed",
                "The registration request could not be completed");
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    ProblemDetail invalidCredentials(InvalidCredentialsException ex) {
        return ApiProblems.create(HttpStatus.UNAUTHORIZED, "invalid_credentials", "Authentication failed",
                "Invalid credentials");
    }

    @ExceptionHandler(UserNotFoundException.class)
    ProblemDetail notFound(UserNotFoundException ex) {
        return ApiProblems.create(HttpStatus.NOT_FOUND, "user_not_found", "Resource not found", "User not found");
    }

    @ExceptionHandler(AccessDeniedException.class)
    ProblemDetail accessDenied(AccessDeniedException ex) {
        return ApiProblems.create(HttpStatus.FORBIDDEN, "access_denied", "Access denied",
                "You are not allowed to access this resource");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail validation(MethodArgumentNotValidException ex) {
        return ApiProblems.validation(ex);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail domainValidation(IllegalArgumentException ex) {
        return ApiProblems.create(HttpStatus.BAD_REQUEST, "invalid_request", "Validation failed",
                "Request validation failed");
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ProblemDetail malformed(HttpMessageNotReadableException ex) {
        return ApiProblems.create(HttpStatus.BAD_REQUEST, "malformed_request", "Malformed request",
                "The request body is not valid JSON");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ProblemDetail invalidParameter(MethodArgumentTypeMismatchException ex) {
        return ApiProblems.create(HttpStatus.BAD_REQUEST, "invalid_parameter", "Invalid parameter",
                "A request parameter has an invalid value");
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    ProblemDetail missingParameter(MissingServletRequestParameterException ex) {
        return ApiProblems.create(HttpStatus.BAD_REQUEST, "missing_parameter", "Missing parameter",
                "A required request parameter is missing");
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ProblemDetail methodNotAllowed(HttpRequestMethodNotSupportedException ex) {
        return ApiProblems.create(HttpStatus.METHOD_NOT_ALLOWED, "method_not_allowed", "Method not allowed",
                "The HTTP method is not supported for this resource");
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ProblemDetail unsupportedMediaType(HttpMediaTypeNotSupportedException ex) {
        return ApiProblems.create(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "unsupported_media_type", "Unsupported media type",
                "The request media type is not supported");
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail unexpected(Exception ex, HttpServletRequest request) {
        log.error("unhandled_exception path={}", request.getRequestURI(), ex);
        return ApiProblems.create(HttpStatus.INTERNAL_SERVER_ERROR, "internal_error", "Internal server error",
                "The request could not be completed");
    }
}
