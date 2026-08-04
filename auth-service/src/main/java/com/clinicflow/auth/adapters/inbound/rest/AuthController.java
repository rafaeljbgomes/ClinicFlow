package com.clinicflow.auth.adapters.inbound.rest;

import com.clinicflow.auth.adapters.inbound.rest.dto.LoginRequest;
import com.clinicflow.auth.adapters.inbound.rest.dto.LoginResponse;
import com.clinicflow.auth.adapters.inbound.rest.dto.RegisterUserRequest;
import com.clinicflow.auth.adapters.inbound.rest.dto.UserResponse;
import com.clinicflow.auth.application.LoginService;
import com.clinicflow.auth.application.RegisterUserService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class AuthController {
    private static final Logger log = LoggerFactory.getLogger(AuthController.class);
    private final RegisterUserService registerUserService;
    private final LoginService loginService;
    private final AuthRestMapper mapper;

    public AuthController(RegisterUserService registerUserService, LoginService loginService, AuthRestMapper mapper) {
        this.registerUserService = registerUserService;
        this.loginService = loginService;
        this.mapper = mapper;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse register(@Valid @RequestBody RegisterUserRequest request) {
        UserResponse user = mapper.toResponse(registerUserService.register(mapper.toCommand(request)));
        log.info("user_registered userId={} role={}", user.id(), user.role());
        return user;
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        LoginResponse result = mapper.toResponse(loginService.login(mapper.toCommand(request)));
        log.info("user_login_success userId={} role={}", result.user().id(), result.user().role());
        return result;
    }
}
