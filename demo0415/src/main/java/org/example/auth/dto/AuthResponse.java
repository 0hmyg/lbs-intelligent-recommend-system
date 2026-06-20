package org.example.auth.dto;

public class AuthResponse {
    private String token;
    private UserMeResponse user;

    public AuthResponse() {
    }

    public AuthResponse(String token, UserMeResponse user) {
        this.token = token;
        this.user = user;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public UserMeResponse getUser() {
        return user;
    }

    public void setUser(UserMeResponse user) {
        this.user = user;
    }
}

