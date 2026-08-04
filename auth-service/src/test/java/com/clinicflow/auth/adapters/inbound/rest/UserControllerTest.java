package com.clinicflow.auth.adapters.inbound.rest;

import com.clinicflow.auth.application.GetUserService;
import com.clinicflow.auth.application.results.UserResult;
import com.clinicflow.auth.domain.Role;
import com.clinicflow.auth.infrastructure.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserController.class)
@Import({SecurityConfig.class, AuthRestMapperImpl.class})
class UserControllerTest {
    private static final UUID USER_ID =
            UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

    @Autowired
    private MockMvc mvc;
    @MockitoBean
    private GetUserService getUserService;
    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void rejectsUnauthenticatedRequest() throws Exception {
        mvc.perform(get("/users/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void returnsAuthenticatedUserFromUserIdClaim() throws Exception {
        when(getUserService.get(any())).thenReturn(userResult());

        mvc.perform(get("/users/me")
                        .with(jwt().jwt(token -> token.claim("userId", USER_ID.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(USER_ID.toString()));
    }

    @Test
    void restrictsUserLookupToAdminRole() throws Exception {
        when(getUserService.get(any())).thenReturn(userResult());

        mvc.perform(get("/users/{id}", USER_ID)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_PSYCHOLOGIST"))))
                .andExpect(status().isForbidden());

        mvc.perform(get("/users/{id}", USER_ID)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("user@example.com"));
    }

    private UserResult userResult() {
        return new UserResult(USER_ID, "user@example.com", Role.PSYCHOLOGIST, "Clinic User", true);
    }
}
