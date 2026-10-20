package com.clinicflow.clinical.adapters.outbound.http;

import com.clinicflow.clinical.application.exceptions.OwnershipCheckUnavailableException;
import com.clinicflow.clinical.domain.AppointmentId;
import com.clinicflow.clinical.domain.PatientId;
import com.clinicflow.clinical.domain.PsychologistId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.springframework.test.web.client.ExpectedCount.never;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class HttpAppointmentDirectoryTest {
    private static final String BASE_URL = "http://appointment-service:8083";
    private static final PsychologistId PSYCHOLOGIST_ID =
            new PsychologistId(UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"));
    private static final PatientId PATIENT_ID =
            new PatientId(UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"));
    private static final AppointmentId APPOINTMENT_ID =
            new AppointmentId(UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd"));
    private static final String APPOINTMENT_URL = BASE_URL + "/appointments/" + APPOINTMENT_ID.value();

    private MockRestServiceServer appointmentService;
    private HttpAppointmentDirectory directory;

    @BeforeEach
    void bindClient() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        appointmentService = MockRestServiceServer.bindTo(builder).build();
        directory = new HttpAppointmentDirectory(builder.build());
    }

    @AfterEach
    void clearCallerContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void returnsThePatientOfAnOwnedAppointment() {
        // Arrange
        authenticateAs(PSYCHOLOGIST_ID);
        appointmentService.expect(requestTo(APPOINTMENT_URL)).andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(appointmentJson(PSYCHOLOGIST_ID), MediaType.APPLICATION_JSON));

        // Act
        Optional<PatientId> patient = directory.findPatientOfOwnedAppointment(APPOINTMENT_ID, PSYCHOLOGIST_ID);

        // Assert
        assertThat(patient).contains(PATIENT_ID);
    }

    @Test
    void forwardsTheCallerBearerToken() {
        // Arrange
        authenticateAs(PSYCHOLOGIST_ID);
        appointmentService.expect(requestTo(APPOINTMENT_URL))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer caller-token"))
                .andRespond(withSuccess(appointmentJson(PSYCHOLOGIST_ID), MediaType.APPLICATION_JSON));

        // Act
        directory.findPatientOfOwnedAppointment(APPOINTMENT_ID, PSYCHOLOGIST_ID);

        // Assert
        appointmentService.verify();
    }

    @ParameterizedTest(name = "status {0} means not owned")
    @ValueSource(ints = {403, 404})
    void returnsEmptyForAppointmentThatAppointmentServiceRefuses(int status) {
        // Arrange
        authenticateAs(PSYCHOLOGIST_ID);
        appointmentService.expect(requestTo(APPOINTMENT_URL)).andRespond(withStatus(HttpStatus.valueOf(status)));

        // Act
        Optional<PatientId> patient = directory.findPatientOfOwnedAppointment(APPOINTMENT_ID, PSYCHOLOGIST_ID);

        // Assert
        assertThat(patient).isEmpty();
    }

    @Test
    void returnsEmptyWhenTheAnswerNamesAnotherOwner() {
        // Arrange
        authenticateAs(PSYCHOLOGIST_ID);
        var otherOwner = new PsychologistId(UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc"));
        appointmentService.expect(requestTo(APPOINTMENT_URL))
                .andRespond(withSuccess(appointmentJson(otherOwner), MediaType.APPLICATION_JSON));

        // Act
        Optional<PatientId> patient = directory.findPatientOfOwnedAppointment(APPOINTMENT_ID, PSYCHOLOGIST_ID);

        // Assert
        assertThat(patient).isEmpty();
    }

    @ParameterizedTest(name = "status {0} fails closed")
    @ValueSource(ints = {401, 500, 503})
    void failsClosedWhenAppointmentServiceCannotDecide(int status) {
        // Arrange
        authenticateAs(PSYCHOLOGIST_ID);
        appointmentService.expect(requestTo(APPOINTMENT_URL)).andRespond(withStatus(HttpStatus.valueOf(status)));

        // Act
        Throwable thrown = catchThrowable(() ->
                directory.findPatientOfOwnedAppointment(APPOINTMENT_ID, PSYCHOLOGIST_ID));

        // Assert
        assertThat(thrown).isInstanceOf(OwnershipCheckUnavailableException.class);
    }

    @Test
    void failsClosedWhenTheAnswerOmitsOwnership() {
        // Arrange
        authenticateAs(PSYCHOLOGIST_ID);
        appointmentService.expect(requestTo(APPOINTMENT_URL))
                .andRespond(withSuccess("{\"id\":\"" + APPOINTMENT_ID.value() + "\"}", MediaType.APPLICATION_JSON));

        // Act
        Throwable thrown = catchThrowable(() ->
                directory.findPatientOfOwnedAppointment(APPOINTMENT_ID, PSYCHOLOGIST_ID));

        // Assert
        assertThat(thrown).isInstanceOf(OwnershipCheckUnavailableException.class);
    }

    @Test
    void failsClosedWhenAppointmentServiceIsUnreachable() {
        // Arrange
        authenticateAs(PSYCHOLOGIST_ID);
        appointmentService.expect(requestTo(APPOINTMENT_URL))
                .andRespond(withException(new IOException("connect timed out")));

        // Act
        Throwable thrown = catchThrowable(() ->
                directory.findPatientOfOwnedAppointment(APPOINTMENT_ID, PSYCHOLOGIST_ID));

        // Assert
        assertThat(thrown).isInstanceOf(OwnershipCheckUnavailableException.class);
    }

    @Test
    void neverForwardsATokenThatBelongsToAnotherPsychologist() {
        // Arrange
        authenticateAs(new PsychologistId(UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc")));
        appointmentService.expect(never(), requestTo(APPOINTMENT_URL));

        // Act
        catchThrowable(() -> directory.findPatientOfOwnedAppointment(APPOINTMENT_ID, PSYCHOLOGIST_ID));

        // Assert
        appointmentService.verify();
    }

    private static String appointmentJson(PsychologistId owner) {
        return """
                {"id":"%s","psychologistId":"%s","patientId":"%s","scheduledAt":"2099-06-12T10:00:00Z",
                 "status":"SCHEDULED","type":"ONLINE","cancellationReason":null}
                """.formatted(APPOINTMENT_ID.value(), owner.value(), PATIENT_ID.value());
    }

    private static void authenticateAs(PsychologistId psychologistId) {
        Instant now = Instant.now();
        Jwt jwt = Jwt.withTokenValue("caller-token")
                .header("alg", "none")
                .issuedAt(now).expiresAt(now.plusSeconds(300))
                .claim("userId", psychologistId.value().toString())
                .claim("role", "PSYCHOLOGIST")
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }
}
