package org.example.repo;

import org.example.domain.Comment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CommentRepository extends JpaRepository<Comment, Long> {
    @Query("select c from Comment c where c.postId = :postId and c.deletedAt is null order by c.createdAt asc")
    Page<Comment> findByPostId(@Param("postId") Long postId, Pageable pageable);
}

