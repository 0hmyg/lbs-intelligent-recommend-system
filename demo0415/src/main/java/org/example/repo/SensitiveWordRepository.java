package org.example.repo;

import org.example.domain.SensitiveWord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SensitiveWordRepository extends JpaRepository<SensitiveWord, Long> {
    Optional<SensitiveWord> findByWordIgnoreCase(String word);
}
