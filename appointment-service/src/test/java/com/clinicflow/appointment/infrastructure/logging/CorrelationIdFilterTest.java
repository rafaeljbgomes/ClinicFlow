package com.clinicflow.appointment.infrastructure.logging;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.mock;

class CorrelationIdFilterTest {
    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    static Stream<String> unacceptableCorrelationIds() {
        return Stream.of("a".repeat(129), "bad id", "bad;id");
    }

    @Test
    void exposesProvidedCorrelationIdToDownstreamLogging() throws Exception {
        // Arrange
        MockHttpServletRequest request = requestWithCorrelationId("correlation-123");
        AtomicReference<String> downstreamCorrelationId = new AtomicReference<>();
        FilterChain chain = (req, res) -> downstreamCorrelationId.set(MDC.get("correlation.id"));

        // Act
        filter.doFilter(request, new MockHttpServletResponse(), chain);

        // Assert
        assertThat(downstreamCorrelationId).hasValue("correlation-123");
    }

    @Test
    void echoesProvidedCorrelationIdInResponseHeader() throws Exception {
        // Arrange
        MockHttpServletRequest request = requestWithCorrelationId("correlation-123");
        MockHttpServletResponse response = new MockHttpServletResponse();

        // Act
        filter.doFilter(request, response, mock(FilterChain.class));

        // Assert
        assertThat(response.getHeader(CorrelationIdFilter.HEADER)).isEqualTo("correlation-123");
    }

    @Test
    void clearsCorrelationIdFromMdcAfterTheRequest() throws Exception {
        // Arrange
        MockHttpServletRequest request = requestWithCorrelationId("correlation-123");

        // Act
        filter.doFilter(request, new MockHttpServletResponse(), mock(FilterChain.class));

        // Assert
        assertThat(MDC.get("correlation.id")).isNull();
    }

    @ParameterizedTest(name = "header [{0}]")
    @NullSource
    @ValueSource(strings = " ")
    void generatesCorrelationIdWhenHeaderIsMissingOrBlank(String correlationId) throws Exception {
        // Arrange
        MockHttpServletRequest request = requestWithCorrelationId(correlationId);
        MockHttpServletResponse response = new MockHttpServletResponse();

        // Act
        filter.doFilter(request, response, mock(FilterChain.class));

        // Assert
        assertThat(response.getHeader(CorrelationIdFilter.HEADER)).isNotBlank();
    }

    @ParameterizedTest(name = "replaces [{0}]")
    @MethodSource("unacceptableCorrelationIds")
    void replacesOversizedOrUnsafeCorrelationIdWithGeneratedUuid(String unacceptable) throws Exception {
        // Arrange
        MockHttpServletRequest request = requestWithCorrelationId(unacceptable);
        MockHttpServletResponse response = new MockHttpServletResponse();

        // Act
        filter.doFilter(request, response, mock(FilterChain.class));

        // Assert
        String generated = response.getHeader(CorrelationIdFilter.HEADER);
        assertThat(catchThrowable(() -> UUID.fromString(generated))).isNull();
    }

    @Test
    void keepsCorrelationIdAtTheMaximumLength() throws Exception {
        // Arrange
        String maximum = "a".repeat(128);
        MockHttpServletRequest request = requestWithCorrelationId(maximum);
        MockHttpServletResponse response = new MockHttpServletResponse();

        // Act
        filter.doFilter(request, response, mock(FilterChain.class));

        // Assert
        assertThat(response.getHeader(CorrelationIdFilter.HEADER)).isEqualTo(maximum);
    }

    private MockHttpServletRequest requestWithCorrelationId(String correlationId) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        if (correlationId != null) {
            request.addHeader(CorrelationIdFilter.HEADER, correlationId);
        }
        return request;
    }
}
