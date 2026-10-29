
package com.example.splitsmart.model;

public class LoginResponse {

    private String token;
    private String tokenType;
    private long expiresIn;
    private Long userId;
    private String name;
    private String email;

    public String getToken() {
        return token;
    }

    public Long getUserId() {
        return userId;
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }

    public long getExpiresIn() {
        return expiresIn;
    }
}
