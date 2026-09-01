package com.jarvis.commerce.auth;

public record TokenResponse(String accessToken, String refreshToken, String tokenType, long expiresIn) {
}
