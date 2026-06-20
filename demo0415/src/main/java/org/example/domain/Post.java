package org.example.domain;

import org.hibernate.annotations.Type;
import org.locationtech.jts.geom.Point;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "posts")
public class Post {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @Column(nullable = false, length = 50)
    private String category;

    @Type(type = "com.vladmihalcea.hibernate.type.array.StringArrayType")
    @Column(columnDefinition = "text[]")
    private String[] images;

    @Column(name = "location_name", length = 200)
    private String locationName;

    @Column(name = "location_geom", columnDefinition = "geometry(Point,4326)")
    private Point locationGeom;

    @Column(name = "view_count")
    private Integer viewCount;

    @Column(name = "like_count")
    private Integer likeCount;

    @Column(name = "comment_count")
    private Integer commentCount;

    @Column(name = "is_audited")
    private Short isAudited;

    @Column(name = "audit_reason", length = 500)
    private String auditReason;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "moderation_filtered_text", columnDefinition = "text")
    private String moderationFilteredText;

    @Column(name = "moderation_hit_words", length = 500)
    private String moderationHitWords;

    @Column(name = "moderation_checked_at")
    private LocalDateTime moderationCheckedAt;


    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String[] getImages() { return images; }
    public void setImages(String[] images) { this.images = images; }
    public String getLocationName() { return locationName; }
    public void setLocationName(String locationName) { this.locationName = locationName; }
    public Point getLocationGeom() { return locationGeom; }
    public void setLocationGeom(Point locationGeom) { this.locationGeom = locationGeom; }
    public Integer getViewCount() { return viewCount; }
    public void setViewCount(Integer viewCount) { this.viewCount = viewCount; }
    public Integer getLikeCount() { return likeCount; }
    public void setLikeCount(Integer likeCount) { this.likeCount = likeCount; }
    public Integer getCommentCount() { return commentCount; }
    public void setCommentCount(Integer commentCount) { this.commentCount = commentCount; }
    public Short getIsAudited() { return isAudited; }
    public void setIsAudited(Short isAudited) { this.isAudited = isAudited; }
    public String getAuditReason() { return auditReason; }
    public void setAuditReason(String auditReason) { this.auditReason = auditReason; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public LocalDateTime getDeletedAt() { return deletedAt; }
    public void setDeletedAt(LocalDateTime deletedAt) { this.deletedAt = deletedAt; }
    public String getModerationFilteredText() { return moderationFilteredText; }
    public void setModerationFilteredText(String moderationFilteredText) { this.moderationFilteredText = moderationFilteredText; }
    public String getModerationHitWords() { return moderationHitWords; }
    public void setModerationHitWords(String moderationHitWords) { this.moderationHitWords = moderationHitWords; }
    public LocalDateTime getModerationCheckedAt() { return moderationCheckedAt; }
    public void setModerationCheckedAt(LocalDateTime moderationCheckedAt) { this.moderationCheckedAt = moderationCheckedAt; }
}
