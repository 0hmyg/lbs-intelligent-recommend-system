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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/recommendations")
public class RecommendationController {
    private final SparkRecommendService recommendService;

    public RecommendationController(SparkRecommendService recommendService) {
        this.recommendService = recommendService;
    }

    @GetMapping("/guess-like")
    public ApiResponse<GuessLikeResponse> guessLike(Authentication authentication,
                                                    @RequestParam(defaultValue = "10") int limit,
                                                    @RequestParam(defaultValue = "100") double cosineWeight,
                                                    @RequestParam(defaultValue = "1.0") double distanceWeight,
                                                    @RequestParam(defaultValue = "1.0") double heatWeight,
                                                    @RequestParam(defaultValue = "8.0") double tagMatchWeight,
                                                    @RequestParam(defaultValue = "4.0") double categoryMatchWeight,
                                                    @RequestParam(defaultValue = "1.0") double imageBonus) {
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        List<Map<String, Object>> items = recommendService.recommendLikeItems(principal.getId(), limit, cosineWeight, distanceWeight, heatWeight, tagMatchWeight, categoryMatchWeight, imageBonus);
        Map<String, Object> formula = new LinkedHashMap<String, Object>();
        formula.put("cosineWeight", cosineWeight);
        formula.put("distanceWeight", distanceWeight);
        formula.put("heatWeight", heatWeight);
        formula.put("tagMatchWeight", tagMatchWeight);
        formula.put("categoryMatchWeight", categoryMatchWeight);
        formula.put("imageBonus", imageBonus);
        formula.put("formulaText", "总分 = 相似分(cosine × cosineWeight) + 标签命中分 + 分类命中分 + 距离分 + 热度分 + 图片加分");
        return ApiResponse.ok(new GuessLikeResponse("基于画像标签、帖子标签余弦相似度、距离和热度综合排序", items, formula));
    }

    @GetMapping("/smart")
    public ApiResponse<SmartRecommendationResponse> smart(Authentication authentication,
                                                          @RequestParam(required = false) String q,
                                                          @RequestParam(defaultValue = "10") int limit) {
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        SparkRecommendService.RecommendationResult result = new SparkRecommendService.RecommendationResult(
                java.util.Collections.<Post>emptyList(),
                "",
                "",
                java.util.Collections.<String, Object>emptyMap(),
                java.util.Collections.<String, Object>emptyMap(),
                null
        );
        List<PostResponse> posts = result.getPosts().stream().map(RecommendationController::toResp).collect(Collectors.toList());
        return ApiResponse.ok(new SmartRecommendationResponse(result.getReason(), posts, result.getPrompt()));
    }

    @GetMapping("/tabs")
    public ApiResponse<SmartRecommendationResponse> tabs(Authentication authentication,
                                                         @RequestParam(required = false) String q,
                                                         @RequestParam(defaultValue = "10") int limit) {
        return smart(authentication, q, limit);
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

    public static class SmartRecommendationResponse {
        private String reason;
        private List<PostResponse> posts;
        private Map<String, Object> debug;

        public SmartRecommendationResponse(String reason, List<PostResponse> posts, Map<String, Object> debug) {
            this.reason = reason;
            this.posts = posts;
            this.debug = debug;
        }

        public String getReason() { return reason; }
        public List<PostResponse> getPosts() { return posts; }
        public Map<String, Object> getDebug() { return debug; }
    }

    public static class GuessLikeResponse {
        private String reason;
        private List<Map<String, Object>> items;
        private Map<String, Object> formula;

        public GuessLikeResponse(String reason, List<Map<String, Object>> items, Map<String, Object> formula) {
            this.reason = reason;
            this.items = items;
            this.formula = formula;
        }

        public String getReason() { return reason; }
        public List<Map<String, Object>> getItems() { return items; }
        public Map<String, Object> getFormula() { return formula; }
    }
}
