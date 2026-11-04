
package com.example.splitsmart.model;

// Response of GET /api/auth/me
public class CurrentUser {

    private Long userId;
    private String name;
    private String email;
    private String uscId;

    public Long getUserId() {
        return userId;
    }

    public String getName() { return name; }
    public String getEmail() { return email; }
    public String getUscId() { return uscId; }
}
