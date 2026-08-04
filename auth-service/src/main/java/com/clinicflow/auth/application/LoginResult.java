package com.clinicflow.auth.application;

import com.clinicflow.auth.application.results.UserResult;

public record LoginResult(String accessToken, String tokenType, long expiresInSeconds, UserResult user) {
}
