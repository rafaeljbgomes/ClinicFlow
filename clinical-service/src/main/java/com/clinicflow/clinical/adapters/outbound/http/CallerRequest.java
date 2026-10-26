package com.clinicflow.clinical.adapters.outbound.http;

import com.clinicflow.clinical.application.exceptions.OwnershipCheckUnavailableException;
import com.clinicflow.clinical.domain.PsychologistId;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.function.Consumer;

/**
 * Lets the owning service decide ownership as the caller: forwards the caller's own bearer token and the
 * current correlation id.
 */
final class CallerRequest {
    private static final String CORRELATION_HEADER = "X-Correlation-Id";
    private static final String CORRELATION_MDC_KEY = "correlation.id";

    private CallerRequest() {
    }

    // The forwarded token must belong to the psychologist being checked; anything else fails closed.
    static Consumer<HttpHeaders> headersFor(PsychologistId psychologistId) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken jwtAuthentication)
                || !psychologistId.value().toString().equals(jwtAuthentication.getToken().getSubject())) {
            throw new OwnershipCheckUnavailableException("No caller token matches the psychologist being checked");
        }
        String token = jwtAuthentication.getToken().getTokenValue();
        String correlationId = MDC.get(CORRELATION_MDC_KEY);
        return headers -> {
            headers.setBearerAuth(token);
            if (correlationId != null) {
                headers.set(CORRELATION_HEADER, correlationId);
            }
        };
    }
}
