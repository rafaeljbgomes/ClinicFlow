package com.clinicflow.appointment.adapters.inbound.rest;

import com.clinicflow.appointment.application.exceptions.AppointmentAccessDeniedException;
import com.clinicflow.appointment.application.exceptions.AppointmentNotFoundException;
import com.clinicflow.appointment.application.exceptions.OwnershipCheckUnavailableException;
import com.clinicflow.appointment.application.exceptions.PatientNotFoundException;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;

import java.util.function.Function;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ApiExceptionHandlerTest {
    private final ApiExceptionHandler handler = new ApiExceptionHandler();
    private final Logger logger = (Logger) LoggerFactory.getLogger(ApiExceptionHandler.class);
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private boolean additive;

    @BeforeEach
    void captureHandlerLogs() {
        additive = logger.isAdditive();
        appender.start();
        logger.setAdditive(false);
        logger.addAppender(appender);
    }

    @AfterEach
    void restoreHandlerLogs() {
        logger.detachAppender(appender);
        logger.setAdditive(additive);
        appender.stop();
    }

    static Stream<Arguments> knownFailures() {
        return Stream.of(
                Arguments.of("appointment not found",
                        (Function<ApiExceptionHandler, ProblemDetail>) handler ->
                                handler.notFound(new AppointmentNotFoundException()),
                        HttpStatus.NOT_FOUND),
                Arguments.of("unknown or foreign patient",
                        (Function<ApiExceptionHandler, ProblemDetail>) handler ->
                                handler.patientNotFound(new PatientNotFoundException()),
                        HttpStatus.NOT_FOUND),
                Arguments.of("unavailable ownership check",
                        (Function<ApiExceptionHandler, ProblemDetail>) handler ->
                                handler.ownershipCheckUnavailable(new OwnershipCheckUnavailableException("down")),
                        HttpStatus.SERVICE_UNAVAILABLE),
                Arguments.of("appointment ownership denial",
                        (Function<ApiExceptionHandler, ProblemDetail>) handler ->
                                handler.denied(new AppointmentAccessDeniedException()),
                        HttpStatus.FORBIDDEN),
                Arguments.of("security access denial",
                        (Function<ApiExceptionHandler, ProblemDetail>) handler ->
                                handler.denied(new AccessDeniedException("denied")),
                        HttpStatus.FORBIDDEN),
                Arguments.of("illegal state",
                        (Function<ApiExceptionHandler, ProblemDetail>) handler ->
                                handler.badRequest(new IllegalStateException("secret")),
                        HttpStatus.BAD_REQUEST));
    }

    @ParameterizedTest(name = "maps {0} to {2}")
    @MethodSource("knownFailures")
    void mapsKnownFailureToItsHttpStatus(String failure, Function<ApiExceptionHandler, ProblemDetail> handle,
                                         HttpStatus expectedStatus) {
        // Act
        ProblemDetail problem = handle.apply(handler);

        // Assert
        assertThat(problem.getStatus()).isEqualTo(expectedStatus.value());
    }

    @Test
    void badRequestHidesTheExceptionMessage() {
        // Arrange
        IllegalArgumentException failure = new IllegalArgumentException("secret");

        // Act
        ProblemDetail problem = handler.badRequest(failure);

        // Assert
        assertThat(problem.getDetail()).isEqualTo("Request validation failed");
    }

    @Test
    void unavailableOwnershipCheckHidesTheCause() {
        // Arrange
        var failure = new OwnershipCheckUnavailableException("connect timed out to patient-service:8082");

        // Act
        ProblemDetail problem = handler.ownershipCheckUnavailable(failure);

        // Assert
        assertThat(problem.getDetail()).isEqualTo("Patient ownership could not be verified; try again later");
    }

    @Test
    void unexpectedFailureReturnsGenericInternalError() {
        // Arrange
        HttpServletRequest request = requestTo("/internal");

        // Act
        ProblemDetail problem = handler.unexpected(new RuntimeException("database password"), request);

        // Assert
        assertThat(problem.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.value());
        assertThat(problem.getDetail()).isEqualTo("The request could not be completed");
    }

    @Test
    void unexpectedFailureLogsOnlyTheRequestPath() {
        // Arrange
        HttpServletRequest request = requestTo("/internal");

        // Act
        handler.unexpected(new RuntimeException("database password"), request);

        // Assert
        assertThat(appender.list).singleElement().satisfies(event ->
                assertThat(event.getFormattedMessage()).isEqualTo("unhandled_exception path=/internal"));
    }

    private HttpServletRequest requestTo(String path) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn(path);
        return request;
    }
}
