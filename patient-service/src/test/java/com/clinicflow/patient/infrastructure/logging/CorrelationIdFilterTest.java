package com.clinicflow.patient.infrastructure.logging;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

class CorrelationIdFilterTest {
    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    @Test
    void propagatesProvidedCorrelationIdAndCleansMdc() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CorrelationIdFilter.HEADER, "correlation-123");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        doAnswer(invocation -> {
            assertThat(MDC.get("correlation.id")).isEqualTo("correlation-123");
            return null;
        }).when(chain).doFilter(request, response);

        filter.doFilter(request, response, chain);

        assertThat(response.getHeader(CorrelationIdFilter.HEADER)).isEqualTo("correlation-123");
        assertThat(MDC.get("correlation.id")).isNull();
    }

    @Test
    void generatesCorrelationIdWhenHeaderIsMissingOrBlank() throws Exception {
        MockHttpServletRequest missing = new MockHttpServletRequest();
        MockHttpServletResponse missingResponse = new MockHttpServletResponse();
        filter.doFilter(missing, missingResponse, mock(FilterChain.class));

        MockHttpServletRequest blank = new MockHttpServletRequest();
        blank.addHeader(CorrelationIdFilter.HEADER, " ");
        MockHttpServletResponse blankResponse = new MockHttpServletResponse();
        filter.doFilter(blank, blankResponse, mock(FilterChain.class));

        assertThat(missingResponse.getHeader(CorrelationIdFilter.HEADER)).isNotBlank();
        assertThat(blankResponse.getHeader(CorrelationIdFilter.HEADER)).isNotBlank();
    }

    @Test
    void replacesOversizedOrUnsafeCorrelationIdsButKeepsTheMaximumLength() throws Exception {
        String maximum = "a".repeat(128);
        for (String unacceptable : new String[] {"a".repeat(129), "bad id", "bad;id"}) {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.addHeader(CorrelationIdFilter.HEADER, unacceptable);
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(request, response, mock(FilterChain.class));

            assertThat(UUID.fromString(response.getHeader(CorrelationIdFilter.HEADER))).isNotNull();
        }

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CorrelationIdFilter.HEADER, maximum);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, mock(FilterChain.class));

        assertThat(response.getHeader(CorrelationIdFilter.HEADER)).isEqualTo(maximum);
    }
}
