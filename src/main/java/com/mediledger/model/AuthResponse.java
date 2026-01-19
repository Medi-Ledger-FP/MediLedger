package com.mediledger.model;

/**
 * Response DTO for authentication containing JWT token
 */
public class AuthResponse {
    private String token;
    private String username;
    private UserRole role;
    private String message;

    public AuthResponse() {
    }

    public AuthResponse(String token, String username, UserRole role, String message) {
        this.token = token;
        this.username = username;
        this.role = role;
        this.message = message;
    }

    public static AuthResponseBuilder builder() {
        return new AuthResponseBuilder();
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public UserRole getRole() {
        return role;
    }

    public void setRole(UserRole role) {
        this.role = role;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public static class AuthResponseBuilder {
        private String token;
        private String username;
        private UserRole role;
        private String message;

        public AuthResponseBuilder token(String token) {
            this.token = token;
            return this;
        }

        public AuthResponseBuilder username(String username) {
            this.username = username;
            return this;
        }

        public AuthResponseBuilder role(UserRole role) {
            this.role = role;
            return this;
        }

        public AuthResponseBuilder message(String message) {
            this.message = message;
            return this;
        }

        public AuthResponse build() {
            return new AuthResponse(token, username, role, message);
        }
    }
}
