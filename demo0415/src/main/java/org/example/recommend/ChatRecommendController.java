package org.example.recommend;

import org.example.auth.UserPrincipal;
import org.example.common.ApiResponse;
import org.example.config.SparkRecommendService;
import org.example.domain.Post;
import org.example.post.dto.PostResponse;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/recommendations")
public class ChatRecommendController {
    private final SparkRecommendService recommendService;

    public ChatRecommendController(SparkRecommendService recommendService) {
        this.recommendService = recommendService;
    }

    @GetMapping("/chat")
    public ApiResponse<ChatResponse> chat(Authentication authentication,
                                         @RequestParam(required = false) String q,
                                         @RequestParam(defaultValue = "8") int limit) {
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        SparkRecommendService.RecommendationResult result = recommendService.recommend(principal.getId(), q, limit);
        List<PostResponse> posts = result.getPosts().stream().map(ChatRecommendController::toResp).collect(Collectors.toList());
        return ApiResponse.ok(new ChatResponse(result.getAnswer(), result.getReason(), result.getPrompt(), result.getAiResponse(), result.getRaw(), posts));
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

    public static class ChatResponse {
        private String answer;
        private String reason;
        private Object prompt;
        private Object aiResponse;
        private String raw;
        private List<PostResponse> posts;

        public ChatResponse(String answer, String reason, Object prompt, Object aiResponse, String raw, List<PostResponse> posts) {
            this.answer = answer;
            this.reason = reason;
            this.prompt = prompt;
            this.aiResponse = aiResponse;
            this.raw = raw;
            this.posts = posts;
        }

        public String getAnswer() { return answer; }
        public String getReason() { return reason; }
        public Object getPrompt() { return prompt; }
        public Object getAiResponse() { return aiResponse; }
        public String getRaw() { return raw; }
        public List<PostResponse> getPosts() { return posts; }
    }
}
