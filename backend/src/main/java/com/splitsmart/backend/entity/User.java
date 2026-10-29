
package com.splitsmart.backend.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Column(name = "usc_id", nullable = false,
            unique = true, length = 10)
    private String uscId;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    protected User() {
    }

    public User(String name, String email,
                String uscId, String passwordHash) {
        this.name = name;
        this.email = email;
        this.uscId = uscId;
        this.passwordHash = passwordHash;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getEmail() { return email; }
    public String getUscId() { return uscId; }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }
}
