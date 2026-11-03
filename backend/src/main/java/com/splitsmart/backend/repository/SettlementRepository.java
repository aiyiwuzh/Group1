package com.splitsmart.backend.repository;

import com.splitsmart.backend.entity.Settlement;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SettlementRepository extends JpaRepository<Settlement, Long> {

    @EntityGraph(attributePaths = {"payer", "payee"})
    List<Settlement> findByGroupIdOrderByCreatedAtDescIdDesc(Long groupId);

    @EntityGraph(attributePaths = {"payer", "payee"})
    List<Settlement> findAllByGroupId(Long groupId);
}
