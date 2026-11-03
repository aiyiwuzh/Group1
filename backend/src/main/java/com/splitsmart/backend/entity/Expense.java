
package com.splitsmart.backend.entity;

import jakarta.persistence.*;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashSet;
import java.util.Set;

@Entity
@Table(name = "expenses")
public class Expense {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_id", nullable = false)
    private Group group;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private ExpenseCategory category;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payer_id", nullable = false)
    private User payer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "creator_id", nullable = false)
    private User creator;

    @Column(length = 500)
    private String note;

    // Set automatically; users cannot enter or edit the date.
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // Set by Balances & Settlements when a settlement covers this expense.
    @Column(nullable = false)
    private boolean settled;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "expense_participants",
            joinColumns = @JoinColumn(name = "expense_id"),
            inverseJoinColumns = @JoinColumn(name = "user_id")
    )
    private Set<User> participants = new LinkedHashSet<>();

    protected Expense() {
    }

    public Expense(Group group, User creator) {
        this.group = group;
        this.creator = creator;
    }

    // MySQL DATETIME(6) keeps microseconds, so match it to avoid the
    // returned time changing after a reload.
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
    public String getTitle() { return title; }
    public BigDecimal getAmount() { return amount; }
    public ExpenseCategory getCategory() { return category; }
    public User getPayer() { return payer; }
    public User getCreator() { return creator; }
    public String getNote() { return note; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public boolean isSettled() { return settled; }
    public Set<User> getParticipants() { return participants; }

    public void setTitle(String title) {
        this.title = title;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public void setCategory(ExpenseCategory category) {
        this.category = category;
    }

    public void setPayer(User payer) {
        this.payer = payer;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public void setSettled(boolean settled) {
        this.settled = settled;
    }

    public void setParticipants(Set<User> participants) {
        this.participants.clear();
        this.participants.addAll(participants);
    }
}
