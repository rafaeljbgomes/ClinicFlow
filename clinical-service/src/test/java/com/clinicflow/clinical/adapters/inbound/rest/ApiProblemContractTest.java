package com.clinicflow.clinical.adapters.inbound.rest;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Stream;
import com.clinicflow.clinical.application.exceptions.AppointmentAlreadyRecordedException;
import com.clinicflow.clinical.application.exceptions.AppointmentNotFoundException;
import com.clinicflow.clinical.application.exceptions.AppointmentPatientMismatchException;
import com.clinicflow.clinical.application.exceptions.OwnershipCheckUnavailableException;
import com.clinicflow.clinical.application.exceptions.PatientNotFoundException;
import com.clinicflow.clinical.domain.InvalidNoteTransitionException;
import com.clinicflow.clinical.domain.SessionRecordNotSignableException;
import com.clinicflow.clinical.domain.SignedSessionRecordException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
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

    static Stream<Arguments> frameworkFailures() {
        return Stream.of(
                Arguments.of("malformed body",
                        (Function<ApiExceptionHandler, ProblemDetail>) handler -> handler.malformed(
                                new HttpMessageNotReadableException("bad json", new MockHttpInputMessage(new byte[0]))),
                        HttpStatus.BAD_REQUEST, "malformed_request"),
                Arguments.of("invalid parameter",
                        (Function<ApiExceptionHandler, ProblemDetail>) handler -> handler.invalidParameter(
                                new MethodArgumentTypeMismatchException("not-a-uuid", UUID.class, "id",
                                        mock(MethodParameter.class), new IllegalArgumentException())),
                        HttpStatus.BAD_REQUEST, "invalid_parameter"),
                Arguments.of("missing parameter",
                        (Function<ApiExceptionHandler, ProblemDetail>) handler -> handler.missingParameter(
                                new MissingServletRequestParameterException("id", "UUID")),
                        HttpStatus.BAD_REQUEST, "missing_parameter"),
                Arguments.of("unsupported method",
                        (Function<ApiExceptionHandler, ProblemDetail>) handler -> handler.methodNotAllowed(
                                new HttpRequestMethodNotSupportedException("TRACE")),
                        HttpStatus.METHOD_NOT_ALLOWED, "method_not_allowed"),
                Arguments.of("unsupported media type",
                        (Function<ApiExceptionHandler, ProblemDetail>) handler -> handler.unsupportedMediaType(
                                new HttpMediaTypeNotSupportedException("text/plain")),
                        HttpStatus.UNSUPPORTED_MEDIA_TYPE, "unsupported_media_type"));
    }

    static Stream<Arguments> signedNoteRuleFailures() {
        return Stream.of(
                Arguments.of("signed session record",
                        (Function<ApiExceptionHandler, ProblemDetail>) handler -> handler.signedSessionRecord(
                                new SignedSessionRecordException()),
                        HttpStatus.CONFLICT, "session_record_signed"),
                Arguments.of("session record not signable",
                        (Function<ApiExceptionHandler, ProblemDetail>) handler -> handler.sessionRecordNotSignable(
                                new SessionRecordNotSignableException()),
                        HttpStatus.CONFLICT, "session_record_not_signable"),
                Arguments.of("invalid note transition",
                        (Function<ApiExceptionHandler, ProblemDetail>) handler -> handler.invalidNoteTransition(
                                new InvalidNoteTransitionException()),
                        HttpStatus.BAD_REQUEST, "invalid_note_transition"));
    }

    static Stream<Arguments> ownershipFailures() {
        return Stream.of(
                Arguments.of("unknown or foreign patient",
                        (Function<ApiExceptionHandler, ProblemDetail>) handler -> handler.patientNotFound(
                                new PatientNotFoundException()),
                        HttpStatus.NOT_FOUND, "patient_not_found"),
                Arguments.of("unknown or foreign appointment",
                        (Function<ApiExceptionHandler, ProblemDetail>) handler -> handler.appointmentNotFound(
                                new AppointmentNotFoundException()),
                        HttpStatus.NOT_FOUND, "appointment_not_found"),
                Arguments.of("appointment of another patient",
                        (Function<ApiExceptionHandler, ProblemDetail>) handler -> handler.appointmentPatientMismatch(
                                new AppointmentPatientMismatchException()),
                        HttpStatus.BAD_REQUEST, "appointment_patient_mismatch"),
                Arguments.of("appointment already recorded",
                        (Function<ApiExceptionHandler, ProblemDetail>) handler -> handler.appointmentAlreadyRecorded(
                                new AppointmentAlreadyRecordedException()),
                        HttpStatus.CONFLICT, "appointment_already_recorded"),
                Arguments.of("unavailable ownership check",
                        (Function<ApiExceptionHandler, ProblemDetail>) handler -> handler.ownershipCheckUnavailable(
                                new OwnershipCheckUnavailableException("connect timed out")),
                        HttpStatus.SERVICE_UNAVAILABLE, "ownership_check_unavailable"));
    }

    @ParameterizedTest(name = "maps {0} to {3}")
    @MethodSource("ownershipFailures")
    void mapsOwnershipFailureToStableProblemCode(String failure,
                                                 Function<ApiExceptionHandler, ProblemDetail> handle,
                                                 HttpStatus expectedStatus, String expectedCode) {
        // Act
        ProblemDetail problem = handle.apply(handler);

        // Assert
        assertProblem(problem, expectedStatus, expectedCode);
    }

    @ParameterizedTest(name = "maps {0} to {3}")
    @MethodSource("frameworkFailures")
    void mapsFrameworkRequestFailureToStableProblemCode(String failure,
                                                         Function<ApiExceptionHandler, ProblemDetail> handle,
                                                         HttpStatus expectedStatus, String expectedCode) {
        // Act
        ProblemDetail problem = handle.apply(handler);

        // Assert
        assertProblem(problem, expectedStatus, expectedCode);
    }

    @ParameterizedTest(name = "maps {0} to {3}")
    @MethodSource("signedNoteRuleFailures")
    void mapsSignedNoteRuleToStableProblemCode(String failure,
                                               Function<ApiExceptionHandler, ProblemDetail> handle,
                                               HttpStatus expectedStatus, String expectedCode) {
        // Act
        ProblemDetail problem = handle.apply(handler);

        // Assert
        assertProblem(problem, expectedStatus, expectedCode);
    }

    @Test
    void validationFailureKeepsTheProblemContract() {
        // Arrange
        MethodArgumentNotValidException failure = invalidEmailFailure();

        // Act
        ProblemDetail problem = handler.validation(failure);

        // Assert
        assertProblem(problem, HttpStatus.BAD_REQUEST, "validation_failed");
    }

    @Test
    void validationFailureExposesGroupedFieldErrors() {
        // Arrange
        MethodArgumentNotValidException failure = invalidEmailFailure();

        // Act
        ProblemDetail problem = handler.validation(failure);

        // Assert
        assertThat(problem.getProperties()).containsEntry(
                "fieldErrors", Map.of("email", List.of("Must be a valid email")));
    }

    private static MethodArgumentNotValidException invalidEmailFailure() {
        MapBindingResult bindingResult = new MapBindingResult(new HashMap<>(), "request");
        bindingResult.rejectValue("email", "invalid", "Must be a valid email");
        return new MethodArgumentNotValidException(mock(MethodParameter.class), bindingResult);
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
