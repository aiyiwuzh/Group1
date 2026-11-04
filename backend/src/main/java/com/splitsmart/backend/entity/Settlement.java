package com.splitsmart.backend.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Entity
@Table(name = "settlements")
public class Settlement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_id", nullable = false)
    private Group group;

    // The authenticated user who recorded the repayment is the payer.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payer_id", nullable = false)
    private User payer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payee_id", nullable = false)
    private User payee;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Settlement() {
    }

    public Settlement(Group group, User payer, User payee, BigDecimal amount) {
        this.group = group;
        this.payer = payer;
        this.payee = payee;
        this.amount = amount;
    }

    private static Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    @PrePersist
    void onCreate() {
        createdAt = now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = now();
    }

    public Long getId() { return id; }
    public Group getGroup() { return group; }
    public User getPayer() { return payer; }
    public User getPayee() { return payee; }
    public BigDecimal getAmount() { return amount; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setPayee(User payee) { this.payee = payee; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
}
