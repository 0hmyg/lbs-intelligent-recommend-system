package org.example.repo;

import org.example.domain.LikeRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LikeRepository extends JpaRepository<LikeRecord, Long> {
    Optional<LikeRecord> findByUserIdAndTargetTypeAndTargetId(Long userId, String targetType, Long targetId);

    long countByTargetTypeAndTargetId(String targetType, Long targetId);
}

