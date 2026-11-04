package com.splitsmart.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "expense_receipts")
public class ExpenseReceipt {
    @Id
    @Column(name = "expense_id")
    private Long expenseId;

    @OneToOne(optional = false)
    @MapsId
    @JoinColumn(name = "expense_id")
    private Expense expense;

    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    @Lob
    @Column(name = "image_data", nullable = false, columnDefinition = "LONGBLOB")
    private byte[] imageData;

    protected ExpenseReceipt() { }

    public ExpenseReceipt(Expense expense, String contentType, byte[] imageData) {
        this.expense = expense;
        this.contentType = contentType;
        this.imageData = imageData;
    }

    public String getContentType() { return contentType; }
    public byte[] getImageData() { return imageData; }
}
