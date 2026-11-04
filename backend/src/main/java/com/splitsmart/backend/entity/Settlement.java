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

    // The member who actually sent the repayment; may differ from its creator.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payer_id", nullable = false)
    private User payer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payee_id", nullable = false)
    private User payee;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "creator_id", nullable = false)
    private User creator;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(length = 500)
    private String note;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Settlement() {
    }

    public Settlement(Group group, User payer, User payee, BigDecimal amount) {
        this(group, payer, payee, amount, payer, null);
    }

    public Settlement(Group group, User payer, User payee, BigDecimal amount,
                      User creator, String note) {
        this.group = group;
        this.payer = payer;
        this.payee = payee;
        this.amount = amount;
        this.creator = creator;
        this.note = note;
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
    public User getCreator() { return creator; }
    public BigDecimal getAmount() { return amount; }
    public String getNote() { return note; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setPayee(User payee) { this.payee = payee; }
    public void setPayer(User payer) { this.payer = payer; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public void setNote(String note) { this.note = note; }
}
