package org.example.admin;

import org.example.common.ApiResponse;
import org.example.service.UserProfileService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/user-profiles")
public class AdminUserProfileController {
    private final UserProfileService userProfileService;

    public AdminUserProfileController(UserProfileService userProfileService) {
        this.userProfileService = userProfileService;
    }

    @GetMapping
    public ApiResponse<Map<String, Object>> list() {
        List<Map<String, Object>> records = userProfileService.listAllProfiles();
        Map<String, Object> out = new HashMap<String, Object>();
        out.put("total", records.size());
        out.put("records", records);
        return ApiResponse.ok(out);
    }

    @GetMapping("/{userId}")
    public ApiResponse<Object> detail(@PathVariable Long userId) {
        return ApiResponse.ok(userProfileService.getProfile(userId).orElse(null));
    }

    @PostMapping
    public ApiResponse<Object> createOrUpdate(@Valid @RequestBody UpsertRequest request) {
        return ApiResponse.ok("保存成功", userProfileService.upsertProfile(request.getUserId(), request.getTagVector()));
    }

    @PutMapping("/{userId}")
    public ApiResponse<Object> update(@PathVariable Long userId, @Valid @RequestBody UpsertRequest request) {
        return ApiResponse.ok("保存成功", userProfileService.upsertProfile(userId, request.getTagVector()));
    }

    @DeleteMapping("/{userId}")
    public ApiResponse<Object> delete(@PathVariable Long userId) {
        userProfileService.deleteProfile(userId);
        return ApiResponse.ok("删除成功", null);
    }

    @PostMapping("/{userId}/manual-update")
    public ApiResponse<Object> manualUpdate(@PathVariable Long userId, @Valid @RequestBody ManualUpdateRequest request) {
        return ApiResponse.ok("更新成功", userProfileService.manualUpdate(userId, request.getViewLimit(), request.getLikeLimit(), request.getCommentLimit(), request.getShareLimit(), request.getOldProfileWeight()));
    }

    @PostMapping("/batch-update")
    public ApiResponse<Object> batchUpdate(@Valid @RequestBody BatchUpdateRequest request) {
        return ApiResponse.ok("更新成功", userProfileService.batchUpdate(
                request.getMinActions(),
                request.getSinceHours(),
                request.getViewLimit(),
                request.getLikeLimit(),
                request.getCommentLimit(),
                request.getShareLimit(),
                request.getOldProfileWeight()));
    }

    @Scheduled(cron = "0 0 3 * * ?")
    public void scheduledBatchUpdate() {
        userProfileService.batchUpdate(5, 24, 50, 20, 10, 5, 0.3d);
    }

    public static class UpsertRequest {
        @NotNull
        private Long userId;

        @NotNull
        private Map<String, Double> tagVector;

        public Long getUserId() {
            return userId;
        }

        public void setUserId(Long userId) {
            this.userId = userId;
        }

        public Map<String, Double> getTagVector() {
            return tagVector;
        }

        public void setTagVector(Map<String, Double> tagVector) {
            this.tagVector = tagVector;
        }
    }

    public static class ManualUpdateRequest {
        private Integer viewLimit = 50;
        private Integer likeLimit = 20;
        private Integer commentLimit = 10;
        private Integer shareLimit = 5;
        private Double oldProfileWeight = 0.3d;

        public Integer getViewLimit() { return viewLimit; }
        public void setViewLimit(Integer viewLimit) { this.viewLimit = viewLimit; }
        public Integer getLikeLimit() { return likeLimit; }
        public void setLikeLimit(Integer likeLimit) { this.likeLimit = likeLimit; }
        public Integer getCommentLimit() { return commentLimit; }
        public void setCommentLimit(Integer commentLimit) { this.commentLimit = commentLimit; }
        public Integer getShareLimit() { return shareLimit; }
        public void setShareLimit(Integer shareLimit) { this.shareLimit = shareLimit; }
        public Double getOldProfileWeight() { return oldProfileWeight; }
        public void setOldProfileWeight(Double oldProfileWeight) { this.oldProfileWeight = oldProfileWeight; }
    }

    public static class BatchUpdateRequest {
        private Integer minActions = 5;
        private Integer sinceHours = 24;
        private Integer viewLimit = 50;
        private Integer likeLimit = 20;
        private Integer commentLimit = 10;
        private Integer shareLimit = 5;
        private Double oldProfileWeight = 0.3d;

        public Integer getMinActions() { return minActions; }
        public void setMinActions(Integer minActions) { this.minActions = minActions; }
        public Integer getSinceHours() { return sinceHours; }
        public void setSinceHours(Integer sinceHours) { this.sinceHours = sinceHours; }
        public Integer getViewLimit() { return viewLimit; }
        public void setViewLimit(Integer viewLimit) { this.viewLimit = viewLimit; }
        public Integer getLikeLimit() { return likeLimit; }
        public void setLikeLimit(Integer likeLimit) { this.likeLimit = likeLimit; }
        public Integer getCommentLimit() { return commentLimit; }
        public void setCommentLimit(Integer commentLimit) { this.commentLimit = commentLimit; }
        public Integer getShareLimit() { return shareLimit; }
        public void setShareLimit(Integer shareLimit) { this.shareLimit = shareLimit; }
        public Double getOldProfileWeight() { return oldProfileWeight; }
        public void setOldProfileWeight(Double oldProfileWeight) { this.oldProfileWeight = oldProfileWeight; }
    }
}
