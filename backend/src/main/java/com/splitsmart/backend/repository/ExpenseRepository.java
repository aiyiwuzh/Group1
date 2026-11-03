
package com.splitsmart.backend.repository;

import com.splitsmart.backend.entity.Expense;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ExpenseRepository extends JpaRepository<Expense, Long> {

    // Newest first; loads payer, creator and participants in one query.
    @EntityGraph(attributePaths = {"payer", "creator", "participants"})
    List<Expense> findByGroupIdOrderByCreatedAtDescIdDesc(Long groupId);
}
