
package com.splitsmart.backend.repository;

import com.splitsmart.backend.entity.Membership;
import com.splitsmart.backend.entity.MembershipId;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface MembershipRepository
        extends JpaRepository<Membership, MembershipId> {

    List<Membership> findByGroupId(Long groupId);

    List<Membership> findByUserId(Long userId);

    boolean existsByGroupIdAndUserId(Long groupId, Long userId);
}
