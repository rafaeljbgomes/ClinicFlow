package com.clinicflow.notification.adapters.inbound.rest;

import com.clinicflow.notification.application.exceptions.NotificationNotFoundException;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;

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

    @Test
    void mapsMissingNotificationToNotFound() {
        // Arrange
        NotificationNotFoundException failure = new NotificationNotFoundException();

        // Act
        ProblemDetail problem = handler.notFound(failure);

        // Assert
        assertThat(problem.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
    }

    @Test
    void mapsAccessDenialToForbidden() {
        // Arrange
        AccessDeniedException failure = new AccessDeniedException("denied");

        // Act
        ProblemDetail problem = handler.denied(failure);

        // Assert
        assertThat(problem.getStatus()).isEqualTo(HttpStatus.FORBIDDEN.value());
    }

    @Test
    void domainValidationFailureHidesTheExceptionMessage() {
        // Arrange
        IllegalArgumentException failure = new IllegalArgumentException("secret");

        // Act
        ProblemDetail problem = handler.validation(failure);

        // Assert
        assertThat(problem.getDetail()).isEqualTo("Request validation failed");
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
