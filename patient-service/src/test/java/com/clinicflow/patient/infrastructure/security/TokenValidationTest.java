package com.clinicflow.patient.infrastructure.security;

import com.clinicflow.patient.adapters.inbound.rest.PatientController;
import com.clinicflow.patient.adapters.inbound.rest.PatientRestMapperImpl;
import com.clinicflow.patient.application.PatientService;
import com.clinicflow.patient.application.queries.ListPatientsQuery;
import com.clinicflow.patient.domain.PsychologistId;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Runs the decoder Spring Boot builds from application.yml against a stand-in realm JWKS (ADR-027 token contract).
@WebMvcTest(PatientController.class)
@Import({SecurityConfig.class, PatientRestMapperImpl.class})
class TokenValidationTest {
    private static final UUID CALLER_ID = UUID.fromString("00000000-0000-4000-8000-000000000011");
    private static final KeycloakTokens KEYCLOAK = new KeycloakTokens();

    @Autowired
    private MockMvc mvc;
    @MockitoBean
    private PatientService patientService;

    @DynamicPropertySource
    static void realmKeys(DynamicPropertyRegistry registry) {
        registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri", KEYCLOAK::jwksUri);
    }

    @AfterAll
    static void stopRealm() {
        KEYCLOAK.close();
    }

    @Test
    void acceptsARealmAccessToken() throws Exception {
        // Arrange
        String token = KEYCLOAK.sign(KEYCLOAK.claims(CALLER_ID, "PSYCHOLOGIST"));

        // Act
        ResultActions response = listPatients(token);

        // Assert
        response.andExpect(status().isOk());
    }

    @Test
    void identifiesTheCallerBySubject() throws Exception {
        // Arrange
        String token = KEYCLOAK.sign(KEYCLOAK.claims(CALLER_ID, "PSYCHOLOGIST"));

        // Act
        listPatients(token);

        // Assert
        verify(patientService).list(new ListPatientsQuery(new PsychologistId(CALLER_ID)));
    }

    @Test
    void ignoresRolesOutsideTheClinicFlowSet() throws Exception {
        // Arrange
        String token = KEYCLOAK.sign(KEYCLOAK.claims(CALLER_ID, "offline_access", "PSYCHOLOGIST"));

        // Act
        ResultActions response = listPatients(token);

        // Assert
        response.andExpect(status().isOk());
    }

    @Test
    void rejectsATokenFromAnotherIssuer() throws Exception {
        // Arrange
        String token = KEYCLOAK.sign(KEYCLOAK.claims(CALLER_ID, "PSYCHOLOGIST").issuer("clinicflow-auth"));

        // Act
        ResultActions response = listPatients(token);

        // Assert
        response.andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsATokenForAnotherAudience() throws Exception {
        // Arrange
        String token = KEYCLOAK.sign(KEYCLOAK.claims(CALLER_ID, "PSYCHOLOGIST").audience("clinicflow-bff"));

        // Act
        ResultActions response = listPatients(token);

        // Assert
        response.andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsAnExpiredToken() throws Exception {
        // Arrange
        Instant issued = Instant.now().minusSeconds(600);
        String token = KEYCLOAK.sign(KEYCLOAK.claims(CALLER_ID, "PSYCHOLOGIST")
                .issueTime(Date.from(issued))
                .expirationTime(Date.from(issued.plusSeconds(300))));

        // Act
        ResultActions response = listPatients(token);

        // Assert
        response.andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsATokenSignedByAnotherKey() throws Exception {
        // Arrange
        String token = KEYCLOAK.signWithForeignKey(KEYCLOAK.claims(CALLER_ID, "PSYCHOLOGIST"));

        // Act
        ResultActions response = listPatients(token);

        // Assert
        response.andExpect(status().isUnauthorized());
    }

    @Test
    void forbidsATokenWithoutRoles() throws Exception {
        // Arrange
        String token = KEYCLOAK.sign(KEYCLOAK.claims(CALLER_ID));

        // Act
        ResultActions response = listPatients(token);

        // Assert
        response.andExpect(status().isForbidden());
    }

    @Test
    void forbidsRolesThatOnlyAppearInRealmAccess() throws Exception {
        // Arrange
        String token = KEYCLOAK.sign(KEYCLOAK.claims(CALLER_ID)
                .claim("realm_access", Map.of("roles", List.of("PSYCHOLOGIST"))));

        // Act
        ResultActions response = listPatients(token);

        // Assert
        response.andExpect(status().isForbidden());
    }

    private ResultActions listPatients(String token) throws Exception {
        return mvc.perform(get("/patients").header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }
}
