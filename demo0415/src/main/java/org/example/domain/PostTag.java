package org.example.domain;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "post_tags", indexes = {
        @Index(name = "idx_post_tags_tag_id", columnList = "tag_id")
}, uniqueConstraints = {
        @UniqueConstraint(name = "idx_post_tags_post_tag", columnNames = {"post_id", "tag_id"})
})
public class PostTag {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "post_id", nullable = false)
    private Long postId;

    @Column(name = "tag_id", nullable = false)
    private Long tagId;

    @Column(nullable = false)
    private Float weight;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getPostId() {
        return postId;
    }

    public void setPostId(Long postId) {
        this.postId = postId;
    }

    public Long getTagId() {
        return tagId;
    }

    public void setTagId(Long tagId) {
        this.tagId = tagId;
    }

    public Float getWeight() {
        return weight;
    }

    public void setWeight(Float weight) {
        this.weight = weight;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
