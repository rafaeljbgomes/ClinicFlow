package com.clinicflow.notification.adapters.inbound.rest;

import com.clinicflow.notification.application.exceptions.NotificationNotFoundException;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ApiExceptionHandlerTest {
    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    @Test
    void mapsDomainSecurityAndValidationFailures() {
        assertThat(handler.notFound(new NotificationNotFoundException()).getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND.value());
        assertThat(handler.denied(new AccessDeniedException("denied")).getStatus())
                .isEqualTo(HttpStatus.FORBIDDEN.value());
        assertThat(handler.validation(new IllegalArgumentException("secret")).getDetail())
                .isEqualTo("Request validation failed");
    }

    @Test
    void mapsUnexpectedFailureToGenericInternalError() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/internal");

        Logger logger = (Logger) LoggerFactory.getLogger(ApiExceptionHandler.class);
        boolean additive = logger.isAdditive();
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.setAdditive(false);
        logger.addAppender(appender);

        try {
            var detail = handler.unexpected(new RuntimeException("database password"), request);

            assertThat(detail.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.value());
            assertThat(detail.getDetail()).isEqualTo("The request could not be completed");
            assertThat(appender.list).singleElement().satisfies(event ->
                    assertThat(event.getFormattedMessage()).isEqualTo("unhandled_exception path=/internal"));
        } finally {
            logger.detachAppender(appender);
            logger.setAdditive(additive);
            appender.stop();
        }
    }
}
