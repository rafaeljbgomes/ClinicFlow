package com.clinicflow.clinical.adapters.inbound.rest;

import com.clinicflow.clinical.application.exceptions.ActiveClinicalCaseExistsException;
import com.clinicflow.clinical.application.exceptions.AppointmentAlreadyRecordedException;
import com.clinicflow.clinical.application.exceptions.AppointmentNotFoundException;
import com.clinicflow.clinical.application.exceptions.AppointmentPatientMismatchException;
import com.clinicflow.clinical.application.exceptions.CarePlanNotFoundException;
import com.clinicflow.clinical.application.exceptions.ClinicalAccessDeniedException;
import com.clinicflow.clinical.application.exceptions.ClinicalCaseNotFoundException;
import com.clinicflow.clinical.application.exceptions.OwnershipCheckUnavailableException;
import com.clinicflow.clinical.application.exceptions.PatientNotFoundException;
import com.clinicflow.clinical.application.exceptions.SessionRecordNotFoundException;
import com.clinicflow.clinical.application.exceptions.StaleResourceVersionException;
import com.clinicflow.clinical.domain.InvalidNoteTransitionException;
import com.clinicflow.clinical.domain.SessionRecordNotSignableException;
import com.clinicflow.clinical.domain.SignedSessionRecordException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler({ClinicalCaseNotFoundException.class, CarePlanNotFoundException.class,
            SessionRecordNotFoundException.class})
    ProblemDetail notFound(RuntimeException ex) {
        return ApiProblems.create(HttpStatus.NOT_FOUND, "clinical_resource_not_found",
                "Clinical resource not found", "Clinical resource not found");
    }

    @ExceptionHandler(PatientNotFoundException.class)
    ProblemDetail patientNotFound(PatientNotFoundException ex) {
        return ApiProblems.create(HttpStatus.NOT_FOUND, "patient_not_found", "Resource not found", "Patient not found");
    }

    @ExceptionHandler(AppointmentNotFoundException.class)
    ProblemDetail appointmentNotFound(AppointmentNotFoundException ex) {
        return ApiProblems.create(HttpStatus.NOT_FOUND, "appointment_not_found", "Resource not found",
                "Appointment not found");
    }

    @ExceptionHandler(AppointmentPatientMismatchException.class)
    ProblemDetail appointmentPatientMismatch(AppointmentPatientMismatchException ex) {
        return ApiProblems.create(HttpStatus.BAD_REQUEST, "appointment_patient_mismatch", "Invalid appointment",
                "The appointment belongs to a different patient than the clinical case");
    }

    @ExceptionHandler(AppointmentAlreadyRecordedException.class)
    ProblemDetail appointmentAlreadyRecorded(AppointmentAlreadyRecordedException ex) {
        return ApiProblems.create(HttpStatus.CONFLICT, "appointment_already_recorded",
                "Appointment already recorded", "The appointment already has a session record");
    }

    @ExceptionHandler(OwnershipCheckUnavailableException.class)
    ProblemDetail ownershipCheckUnavailable(OwnershipCheckUnavailableException ex) {
        return ApiProblems.create(HttpStatus.SERVICE_UNAVAILABLE, "ownership_check_unavailable",
                "Service unavailable", "Ownership could not be verified; try again later");
    }

    @ExceptionHandler(ActiveClinicalCaseExistsException.class)
    ProblemDetail conflict(ActiveClinicalCaseExistsException ex) {
        return ApiProblems.create(HttpStatus.CONFLICT, "active_clinical_case_exists",
                "Active clinical case exists", "Patient already has an active clinical case for this psychologist");
    }

    @ExceptionHandler(StaleResourceVersionException.class)
    ProblemDetail staleVersion(StaleResourceVersionException ex) {
        return ApiProblems.create(HttpStatus.PRECONDITION_FAILED, "stale_resource_version", "Stale resource version",
                "The resource changed since you loaded it. Reload it and try again.");
    }

    @ExceptionHandler(PreconditionRequiredException.class)
    ProblemDetail preconditionRequired(PreconditionRequiredException ex) {
        return ApiProblems.create(HttpStatus.PRECONDITION_REQUIRED, "precondition_required", "Precondition required",
                "Send If-Match with the version you last loaded");
    }

    @ExceptionHandler(InvalidPreconditionException.class)
    ProblemDetail invalidPrecondition(InvalidPreconditionException ex) {
        return ApiProblems.create(HttpStatus.BAD_REQUEST, "invalid_precondition", "Invalid precondition",
                "If-Match must be one strong entity tag, such as \"3\"");
    }

    @ExceptionHandler(SignedSessionRecordException.class)
    ProblemDetail signedSessionRecord(SignedSessionRecordException ex) {
        return ApiProblems.create(HttpStatus.CONFLICT, "session_record_signed", "Session record is signed",
                "Signed session records cannot be changed");
    }

    @ExceptionHandler(SessionRecordNotSignableException.class)
    ProblemDetail sessionRecordNotSignable(SessionRecordNotSignableException ex) {
        return ApiProblems.create(HttpStatus.CONFLICT, "session_record_not_signable",
                "Session record cannot be signed", "Only draft session notes with a summary can be signed");
    }

    @ExceptionHandler(InvalidNoteTransitionException.class)
    ProblemDetail invalidNoteTransition(InvalidNoteTransitionException ex) {
        return ApiProblems.create(HttpStatus.BAD_REQUEST, "invalid_note_transition", "Invalid note status",
                "Session notes can only be signed through the sign action");
    }

    @ExceptionHandler({ClinicalAccessDeniedException.class, AccessDeniedException.class})
    ProblemDetail denied(Exception ex) {
        return ApiProblems.create(HttpStatus.FORBIDDEN, "access_denied", "Access denied",
                "You do not have access to this clinical resource");
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
        return ApiProblems.create(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "unsupported_media_type",
                "Unsupported media type", "The request media type is not supported");
    }

    @ExceptionHandler({NoHandlerFoundException.class, NoResourceFoundException.class})
    ProblemDetail unknownEndpoint(Exception ex) {
        return ApiProblems.create(HttpStatus.NOT_FOUND, "endpoint_not_found", "Resource not found",
                "The requested endpoint does not exist");
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail unexpected(Exception ex, HttpServletRequest request) {
        log.error("clinical_unexpected_error path={}", request.getRequestURI(), ex);
        return ApiProblems.create(HttpStatus.INTERNAL_SERVER_ERROR, "internal_error", "Internal server error",
                "The request could not be completed");
    }
}
