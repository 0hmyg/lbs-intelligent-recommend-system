package org.example.admin;

import org.example.common.ApiResponse;
import org.example.domain.Post;
import org.example.post.dto.PostResponse;
import org.example.repo.PostRepository;
import org.example.service.PostTaggingService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;
import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/admin")
public class AdminAuditController {
    private final PostRepository postRepository;
    private final PostTaggingService postTaggingService;

    @Value("${app.upload-dir:uploads}")
    private String uploadDir;

    public AdminAuditController(PostRepository postRepository,
                                PostTaggingService postTaggingService) {
        this.postRepository = postRepository;
        this.postTaggingService = postTaggingService;
    }

    @GetMapping("/audits")
    public ApiResponse<Page<PostResponse>> audits(@RequestParam(defaultValue = "0") int page,
                                                  @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return ApiResponse.ok(postRepository.findPending(pageable).map(post -> {
            PostResponse resp = toResp(post);
            resp.setAuditReason((resp.getAuditReason() == null || resp.getAuditReason().isEmpty()) ? auditStatusText(resp.getIsAudited()) : resp.getAuditReason());
            return resp;
        }));
    }

    @GetMapping("/audits/{postId}")
    public ApiResponse<PostResponse> detail(@PathVariable Long postId) {
        Post post = postRepository.findById(postId).orElseThrow(() -> new IllegalArgumentException("帖子不存在"));
        return ApiResponse.ok(toResp(post));
    }

    @PostMapping("/audits/{postId}/approve")
    @Transactional
    public ApiResponse<Object> approve(@PathVariable Long postId) {
        Post post = postRepository.findById(postId).orElseThrow(() -> new IllegalArgumentException("帖子不存在"));
        post.setIsAudited((short) 1);
        post.setAuditReason(null);
        post.setUpdatedAt(LocalDateTime.now());
        postRepository.save(post);
        postTaggingService.generateAndSaveTags(post);
        return ApiResponse.ok("已通过", null);
    }

    public static class RejectBody {
        @NotBlank
        @Size(max = 500)
        private String reason;

        public String getReason() {
            return reason;
        }

        public void setReason(String reason) {
            this.reason = reason;
        }
    }

    @PostMapping("/audits/{postId}/reject")
    @Transactional
    public ApiResponse<Object> reject(@PathVariable Long postId, @RequestBody RejectBody body) {
        if (body == null || body.getReason() == null || body.getReason().trim().isEmpty()) {
            throw new IllegalArgumentException("请输入驳回原因");
        }
        Post post = postRepository.findById(postId).orElseThrow(() -> new IllegalArgumentException("帖子不存在"));
        post.setIsAudited((short) 2);
        post.setAuditReason(body.getReason().trim());
        post.setUpdatedAt(LocalDateTime.now());
        postRepository.save(post);
        return ApiResponse.ok("已驳回", null);
    }

    private static PostResponse toResp(Post p) {
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
        r.setCreatedAt(p.getCreatedAt());
        return r;
    }

    private static String auditStatusText(Short status) {
        if (status == null) {
            return "待审核";
        }
        if (status == 1) {
            return "通过审核";
        }
        if (status == 2) {
            return "未通过审核";
        }
        return "待审核";
    }
}

