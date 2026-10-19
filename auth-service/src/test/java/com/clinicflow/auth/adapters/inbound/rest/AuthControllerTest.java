package com.clinicflow.auth.adapters.inbound.rest;

import com.clinicflow.auth.application.LoginResult;
import com.clinicflow.auth.application.LoginService;
import com.clinicflow.auth.application.RegisterUserService;
import com.clinicflow.auth.application.results.UserResult;
import com.clinicflow.auth.domain.Role;
import com.clinicflow.auth.infrastructure.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, AuthRestMapperImpl.class})
class AuthControllerTest {
    private static final UUID USER_ID =
            UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

    @Autowired
    private MockMvc mvc;
    @MockitoBean
    private RegisterUserService registerUserService;
    @MockitoBean
    private LoginService loginService;
    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void registrationIsPublicAndReturnsCreatedUser() throws Exception {
        // Arrange
        when(registerUserService.register(any())).thenReturn(userResult());
        var request = post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "email": "user@example.com",
                          "password": "TestingPass123!",
                          "role": "PSYCHOLOGIST",
                          "fullName": "Clinic User"
                        }
                        """);

        // Act
        ResultActions response = mvc.perform(request);

        // Assert
        response.andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(USER_ID.toString()))
                .andExpect(jsonPath("$.email").value("user@example.com"))
                .andExpect(jsonPath("$.role").value("PSYCHOLOGIST"));
    }

    @Test
    void loginIsPublicAndReturnsBearerToken() throws Exception {
        // Arrange
        when(loginService.login(any())).thenReturn(new LoginResult(
                "signed-jwt",
                "Bearer",
                1800,
                userResult()
        ));
        var request = post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "email": "user@example.com",
                          "password": "TestingPass123!"
                        }
                        """);

        // Act
        ResultActions response = mvc.perform(request);

        // Assert
        response.andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("signed-jwt"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresInSeconds").value(1800));
    }

    @Test
    void invalidRequestReturnsGenericBadRequest() throws Exception {
        // Arrange
        var request = post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "email": "invalid",
                          "password": "TestingPass123!",
                          "role": "PSYCHOLOGIST",
                          "fullName": "Clinic User"
                        }
                        """);

        // Act
        ResultActions response = mvc.perform(request);

        // Assert
        response.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.detail").value("Request validation failed"));
    }

    private UserResult userResult() {
        return new UserResult(USER_ID, "user@example.com", Role.PSYCHOLOGIST, "Clinic User", true);
    }
}
