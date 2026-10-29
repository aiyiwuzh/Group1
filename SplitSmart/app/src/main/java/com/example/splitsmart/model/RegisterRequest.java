
package com.example.splitsmart.model;

public class RegisterRequest {

    private String name;
    private String email;
    private String uscId;
    private String password;

    public RegisterRequest(
            String name,
            String email,
            String uscId,
            String password) {

        this.name = name;
        this.email = email;
        this.uscId = uscId;
        this.password = password;
    }
}
