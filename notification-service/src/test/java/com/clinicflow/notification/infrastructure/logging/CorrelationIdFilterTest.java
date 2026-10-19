package com.clinicflow.notification.infrastructure.logging;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class CorrelationIdFilterTest {
    private final CorrelationIdFilter filter = new CorrelationIdFilter();

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

    @Test
    void generatesCorrelationIdWhenHeaderIsMissing() throws Exception {
        // Arrange
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        // Act
        filter.doFilter(request, response, mock(FilterChain.class));

        // Assert
        assertThat(response.getHeader(CorrelationIdFilter.HEADER)).isNotBlank();
    }

    @Test
    void generatesCorrelationIdWhenHeaderIsBlank() throws Exception {
        // Arrange
        MockHttpServletRequest request = requestWithCorrelationId(" ");
        MockHttpServletResponse response = new MockHttpServletResponse();

        // Act
        filter.doFilter(request, response, mock(FilterChain.class));

        // Assert
        assertThat(response.getHeader(CorrelationIdFilter.HEADER)).isNotBlank();
    }

    private MockHttpServletRequest requestWithCorrelationId(String correlationId) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CorrelationIdFilter.HEADER, correlationId);
        return request;
    }
}
