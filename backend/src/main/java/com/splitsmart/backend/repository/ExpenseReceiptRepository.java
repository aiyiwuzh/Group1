package com.splitsmart.backend.repository;

import com.splitsmart.backend.entity.ExpenseReceipt;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExpenseReceiptRepository extends JpaRepository<ExpenseReceipt, Long> { }
