package org.example.admin;

import org.example.common.ApiResponse;
import org.example.domain.Post;
import org.example.post.dto.PostResponse;
import org.example.repo.PostRepository;
import org.example.service.PostTaggingService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;

@RestController
@RequestMapping("/api/admin/post-moderation")
public class AdminPostModerationController {
    private final PostRepository postRepository;
    private final PostTaggingService postTaggingService;

    public AdminPostModerationController(PostRepository postRepository,
                                         PostTaggingService postTaggingService) {
        this.postRepository = postRepository;
        this.postTaggingService = postTaggingService;
    }

    @GetMapping("/blocked")
    public ApiResponse<Page<PostResponse>> blocked(@RequestParam(defaultValue = "0") int page,
                                                   @RequestParam(defaultValue = "20") int size,
                                                   @RequestParam(required = false) Short status) {
        Pageable pageable = PageRequest.of(page, size);
        short queryStatus = status == null ? (short) 2 : status;
        Page<Post> posts = postRepository.findByIsAuditedAndDeletedAtIsNullOrderByCreatedAtDesc(queryStatus, pageable);
        return ApiResponse.ok(posts.map(this::toResp));
    }

    private PostResponse toResp(Post p) {
        PostResponse r = new PostResponse();
        r.setId(p.getId());
        r.setUserId(p.getUserId());
        r.setTitle(p.getTitle());
        r.setContent(p.getContent());
        r.setCategory(p.getCategory());
        r.setImages(p.getImages());
        r.setLocationName(p.getLocationName());
        if (p.getLocationGeom() != null) {
            r.setLng(p.getLocationGeom().getX());
            r.setLat(p.getLocationGeom().getY());
        }
        r.setViewCount(p.getViewCount());
        r.setLikeCount(p.getLikeCount());
        r.setCommentCount(p.getCommentCount());
        r.setIsAudited(p.getIsAudited());
        r.setAuditReason(p.getAuditReason());
        r.setModerationFilteredText(p.getModerationFilteredText());
        r.setModerationHitWords(p.getModerationHitWords());
        r.setModerationCheckedAt(p.getModerationCheckedAt());
        r.setCreatedAt(p.getCreatedAt());
        return r;
    }

    public static class DecisionBody {
        private boolean allowed;
        private String reason;
        public boolean isAllowed() { return allowed; }
        public void setAllowed(boolean allowed) { this.allowed = allowed; }
        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
    }

    @PostMapping("/{postId}/decision")
    @Transactional
    public ApiResponse<Object> decision(@PathVariable Long postId, @RequestBody DecisionBody body) {
        Post post = postRepository.findById(postId).orElseThrow(() -> new IllegalArgumentException("帖子不存在"));
        if (Boolean.TRUE.equals(body.isAllowed())) {
            post.setIsAudited((short) 1);
            post.setAuditReason(body.getReason() == null || body.getReason().trim().isEmpty() ? "管理员放行" : body.getReason().trim());
        } else {
            post.setIsAudited((short) 4);
            post.setAuditReason(body.getReason() == null || body.getReason().trim().isEmpty() ? "管理员驳回" : body.getReason().trim());
        }
        post.setUpdatedAt(LocalDateTime.now());
        postRepository.save(post);
        if (Boolean.TRUE.equals(body.isAllowed())) {
            postTaggingService.generateAndSaveTags(post);
        }
        return ApiResponse.ok("处理成功", null);
    }


    @PostMapping("/{postId}/appeal-decision")
    @Transactional
    public ApiResponse<Object> appealDecision(@PathVariable Long postId, @RequestBody DecisionBody body) {
        Post post = postRepository.findById(postId).orElseThrow(() -> new IllegalArgumentException("帖子不存在"));
        if (post.getIsAudited() == null || post.getIsAudited() != 3) {
            throw new IllegalArgumentException("只有申述中的帖子才能审核");
        }
        if (Boolean.TRUE.equals(body.isAllowed())) {
            post.setIsAudited((short) 1);
            post.setAuditReason(body.getReason() == null || body.getReason().trim().isEmpty() ? "申述通过" : body.getReason().trim());
        } else {
            post.setIsAudited((short) 2);
            post.setAuditReason(body.getReason() == null || body.getReason().trim().isEmpty() ? "申述驳回" : body.getReason().trim());
        }
        post.setUpdatedAt(LocalDateTime.now());
        postRepository.save(post);
        if (Boolean.TRUE.equals(body.isAllowed())) {
            postTaggingService.generateAndSaveTags(post);
        }
        return ApiResponse.ok("处理成功", null);
    }

}

