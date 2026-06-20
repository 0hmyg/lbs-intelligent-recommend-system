package org.example.repo;

import org.example.domain.UserAction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

public interface UserActionRepository extends JpaRepository<UserAction, Long>, JpaSpecificationExecutor<UserAction> {
    List<UserAction> findByUserIdOrderByActionTimeDesc(Long userId);
}
