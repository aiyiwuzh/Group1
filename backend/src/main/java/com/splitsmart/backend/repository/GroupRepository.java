
package com.splitsmart.backend.repository;

import com.splitsmart.backend.entity.Group;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface GroupRepository extends JpaRepository<Group, Long> {

    List<Group> findByOwnerId(Long ownerId);
}
