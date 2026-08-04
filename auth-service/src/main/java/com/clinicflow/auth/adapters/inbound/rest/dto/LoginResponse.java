package com.clinicflow.auth.adapters.inbound.rest.dto;

public record LoginResponse(String accessToken, String tokenType, long expiresInSeconds, UserResponse user) {
}
