
package com.splitsmart.backend.repository;

import com.splitsmart.backend.entity.Expense;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.Instant;
import java.util.List;

public interface ExpenseRepository extends JpaRepository<Expense, Long> {

    // Newest first; loads payer, creator and participants in one query.
    @EntityGraph(attributePaths = {"payer", "creator", "participants"})
    List<Expense> findByGroupIdOrderByCreatedAtDescIdDesc(Long groupId);

    // Balance calculations need the payer and each participant's persisted
    // share for every expense in a group.
    @EntityGraph(attributePaths = {"payer", "shares"})
    List<Expense> findAllByGroupId(Long groupId);

    // Monthly summaries: expenses created in [start, end), with each
    // participant's share so the current user's part can be totalled.
    @EntityGraph(attributePaths = {"shares"})
    List<Expense> findByGroupIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
            Long groupId, Instant start, Instant end);
}
