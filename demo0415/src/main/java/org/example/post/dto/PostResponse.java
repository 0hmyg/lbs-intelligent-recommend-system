package org.example.post.dto;

import java.time.LocalDateTime;

public class PostResponse {
    private Long id;
    private Long userId;
    private String title;
    private String content;
    private String category;
    private String[] images;
    private String locationName;
    private Double lng;
    private Double lat;
    private Integer viewCount;
    private Integer likeCount;
    private Integer commentCount;
    private Short isAudited;
    private String auditReason;
    private String moderationFilteredText;
    private String moderationHitWords;
    private LocalDateTime moderationCheckedAt;
    private Double distanceMeters;
    private LocalDateTime createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String[] getImages() {
        return images;
    }

    public void setImages(String[] images) {
        this.images = images;
    }

    public String getLocationName() {
        return locationName;
    }

    public void setLocationName(String locationName) {
        this.locationName = locationName;
    }

    public Double getLng() {
        return lng;
    }

    public void setLng(Double lng) {
        this.lng = lng;
    }

    public Double getLat() {
        return lat;
    }

    public void setLat(Double lat) {
        this.lat = lat;
    }

    public Integer getViewCount() {
        return viewCount;
    }

    public void setViewCount(Integer viewCount) {
        this.viewCount = viewCount;
    }

    public Integer getLikeCount() {
        return likeCount;
    }

    public void setLikeCount(Integer likeCount) {
        this.likeCount = likeCount;
    }

    public Integer getCommentCount() {
        return commentCount;
    }

    public void setCommentCount(Integer commentCount) {
        this.commentCount = commentCount;
    }

    public Short getIsAudited() {
        return isAudited;
    }

    public void setIsAudited(Short isAudited) {
        this.isAudited = isAudited;
    }

    public String getAuditReason() {
        return auditReason;
    }

    public void setAuditReason(String auditReason) {
        this.auditReason = auditReason;
    }

    public String getModerationFilteredText() {
        return moderationFilteredText;
    }

    public void setModerationFilteredText(String moderationFilteredText) {
        this.moderationFilteredText = moderationFilteredText;
    }

    public String getModerationHitWords() {
        return moderationHitWords;
    }

    public void setModerationHitWords(String moderationHitWords) {
        this.moderationHitWords = moderationHitWords;
    }

    public LocalDateTime getModerationCheckedAt() {
        return moderationCheckedAt;
    }

    public void setModerationCheckedAt(LocalDateTime moderationCheckedAt) {
        this.moderationCheckedAt = moderationCheckedAt;
    }

    public Double getDistanceMeters() {
        return distanceMeters;
    }

    public void setDistanceMeters(Double distanceMeters) {
        this.distanceMeters = distanceMeters;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}

