package org.example.content;

import org.example.auth.UserPrincipal;
import org.example.common.ApiResponse;
import org.example.domain.Post;
import org.example.domain.User;
import org.example.domain.UserAction;
import org.example.geo.GeoUtils;
import org.example.repo.PostRepository;
import org.example.repo.UserActionRepository;
import org.example.repo.UserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
public class ContentApiController {

    private static final List<String> DEFAULT_STOPWORDS = Arrays.asList("的", "了", "是", "在", "我", "有", "和", "就", "都", "也", "与", "及", "或");
    private static final Map<String, List<String>> DEFAULT_SENSITIVE = new LinkedHashMap<String, List<String>>();

    static {
        DEFAULT_SENSITIVE.put("block", Arrays.asList("毒品", "色情", "赌博", "暴力", "诈骗"));
        DEFAULT_SENSITIVE.put("review", Arrays.asList("违法", "违规", "敏感", "高危"));
    }

    private final JdbcTemplate jdbcTemplate;
    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final UserActionRepository userActionRepository;

    public ContentApiController(JdbcTemplate jdbcTemplate,
                                PostRepository postRepository,
                                UserRepository userRepository,
                                UserActionRepository userActionRepository) {
        this.jdbcTemplate = jdbcTemplate;
        this.postRepository = postRepository;
        this.userRepository = userRepository;
        this.userActionRepository = userActionRepository;
    }

    @GetMapping(value = "", produces = "application/json;charset=UTF-8")
    public ApiResponse<Map<String, Object>> root() {
        Map<String, Object> info = new LinkedHashMap<String, Object>();
        info.put("service", "Python 服务基本信息");
        info.put("service_url", "http://localhost:5000");
        info.put("docs_url", "http://localhost:5000/docs");
        info.put("format", "JSON");
        info.put("charset", "UTF-8");
        return ApiResponse.ok(info);
    }

    @GetMapping("/health")
    public ApiResponse<Map<String, Object>> health() {
        Map<String, Object> out = new LinkedHashMap<String, Object>();
        out.put("status", "healthy");
        out.put("database", "connected");
        Map<String, String> modules = new LinkedHashMap<String, String>();
        modules.put("tag_extractor", "ready");
        modules.put("recommender", "ready");
        modules.put("sensitive_filter", "ready");
        out.put("modules", modules);
        return ApiResponse.ok(out);
    }

    @PostMapping("/extract_tags")
    public ApiResponse<List<Map<String, Object>>> extractTags(@RequestBody Map<String, Object> body) {
        String text = body == null ? null : asText(body.get("text"));
        int topK = body == null ? 5 : asInt(body.get("top_k"), 5);
        List<Map<String, Object>> tags = keywordTags(text, topK);
        return ApiResponse.ok(tags);
    }

    @PostMapping("/filter")
    public ApiResponse<Map<String, Object>> filter(@RequestBody Map<String, Object> body, Authentication authentication) {
        String text = body == null ? null : asText(body.get("text"));
        FilterResult result = filterText(text, canManageModeration(authentication));
        Map<String, Object> out = new LinkedHashMap<String, Object>();
        out.put("is_valid", result.valid);
        out.put("filtered_text", result.filteredText);
        out.put("hit_words", result.hitWords);
        return ApiResponse.ok(out);
    }

    @PostMapping("/recommend")
    public ApiResponse<List<Map<String, Object>>> recommend(@RequestBody Map<String, Object> body) {
        Long userId = asLong(body == null ? null : body.get("user_id"));
        Double lat = asDouble(body == null ? null : body.get("location_lat"));
        Double lng = asDouble(body == null ? null : body.get("location_lng"));
        int limit = body == null ? 20 : asInt(body.get("limit"), 20);
        return ApiResponse.ok(recommendByHabit(userId, lat, lng, limit));
    }

    @PostMapping("/update_profile")
    public ApiResponse<Map<String, Object>> updateProfile(@RequestBody Map<String, Object> body) {
        Long userId = asLong(body == null ? null : body.get("user_id"));
        Long postId = asLong(body == null ? null : body.get("post_id"));
        String actionType = asText(body == null ? null : body.get("action_type"));
        if (userId == null || postId == null || actionType == null || actionType.trim().isEmpty()) {
            throw new IllegalArgumentException("user_id、action_type、post_id 不能为空");
        }

        UserAction action = new UserAction();
        action.setUserId(userId);
        action.setPostId(postId);
        action.setActionType(actionType.trim().toLowerCase(Locale.ROOT));
        action.setActionTime(LocalDateTime.now());
        userActionRepository.save(action);

        Map<String, Object> out = new LinkedHashMap<String, Object>();
        out.put("status", "success");
        out.put("message", "用户画像已更新");
        return ApiResponse.ok(out);
    }

    @GetMapping("/sensitive_words")
    public ApiResponse<Map<String, Object>> sensitiveWords(Authentication authentication) {
        ensureAdmin(authentication);
        Map<String, Object> out = new LinkedHashMap<String, Object>();
        List<Map<String, Object>> block = querySensitiveWords("block");
        List<Map<String, Object>> review = querySensitiveWords("review");
        out.put("block", block);
        out.put("review", review);
        out.put("total", block.size() + review.size());
        Map<String, Object> wrapper = new LinkedHashMap<String, Object>();
        wrapper.put("status", "success");
        wrapper.put("data", out);
        return ApiResponse.ok(wrapper);
    }

    @PostMapping("/sensitive_words")
    public ApiResponse<Map<String, Object>> addSensitiveWord(@RequestParam String word,
                                                              @RequestParam(defaultValue = "review") String level,
                                                              Authentication authentication) {
        ensureAdmin(authentication);
        if (word == null || word.trim().isEmpty()) {
            throw new IllegalArgumentException("word 不能为空");
        }
        String normalizedLevel = normalizeLevel(level);
        try {
            jdbcTemplate.update("insert into sensitive_words(word, level, created_at) values (?, ?, now())", word.trim(), normalizedLevel);
        } catch (Exception ignore) {
        }
        return ApiResponse.ok(Collections.singletonMap("message", "已添加敏感词: " + word.trim()));
    }

    @DeleteMapping("/sensitive_words")
    public ApiResponse<Map<String, Object>> deleteSensitiveWord(@RequestParam String word, Authentication authentication) {
        ensureAdmin(authentication);
        if (word == null || word.trim().isEmpty()) {
            throw new IllegalArgumentException("word 不能为空");
        }
        try {
            jdbcTemplate.update("delete from sensitive_words where word = ?", word.trim());
        } catch (Exception ignore) {
        }
        return ApiResponse.ok(Collections.singletonMap("message", "已移除敏感词: " + word.trim()));
    }

    @PostMapping("/sensitive_words/reload")
    public ApiResponse<Map<String, Object>> reloadSensitiveWords(Authentication authentication) {
        ensureAdmin(authentication);
        return ApiResponse.ok(Collections.singletonMap("message", "敏感词已重新加载"));
    }

    @GetMapping("/stopwords")
    public ApiResponse<Map<String, Object>> stopwords(Authentication authentication) {
        ensureAdmin(authentication);
        List<String> words = new ArrayList<String>(DEFAULT_STOPWORDS);
        try {
            words.addAll(jdbcTemplate.queryForList("select word from stopwords", String.class));
        } catch (Exception ignore) {
        }
        LinkedHashSet<String> unique = new LinkedHashSet<String>(words);
        Map<String, Object> out = new LinkedHashMap<String, Object>();
        out.put("status", "success");
        out.put("data", new ArrayList<String>(unique));
        out.put("count", unique.size());
        return ApiResponse.ok(out);
    }

    @PostMapping("/stopwords")
    public ApiResponse<Map<String, Object>> addStopword(@RequestParam String word, Authentication authentication) {
        ensureAdmin(authentication);
        if (word == null || word.trim().isEmpty()) {
            throw new IllegalArgumentException("word 不能为空");
        }
        try {
            jdbcTemplate.update("insert into stopwords(word, created_at) values (?, now())", word.trim());
        } catch (Exception ignore) {
        }
        return ApiResponse.ok(Collections.singletonMap("message", "已添加停用词: " + word.trim()));
    }

    @GetMapping("/tags")
    public ApiResponse<Map<String, Object>> tags(Authentication authentication) {
        ensureAdmin(authentication);
        List<Map<String, Object>> rows;
        try {
            rows = jdbcTemplate.queryForList("select id, name, type, weight, created_at from tags order by id asc");
        } catch (Exception ignore) {
            rows = new ArrayList<Map<String, Object>>();
        }
        Map<String, Object> out = new LinkedHashMap<String, Object>();
        out.put("status", "success");
        out.put("data", rows);
        out.put("count", rows.size());
        return ApiResponse.ok(out);
    }

    @PostMapping("/tags")
    public ApiResponse<Map<String, Object>> addTag(@RequestParam String name,
                                                   @RequestParam(defaultValue = "content") String type,
                                                   @RequestParam(defaultValue = "1.0") double weight,
                                                   Authentication authentication) {
        ensureAdmin(authentication);
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("name 不能为空");
        }
        try {
            jdbcTemplate.update("insert into tags(name, type, weight, created_at) values (?, ?, ?, now())", name.trim(), type.trim(), weight);
        } catch (Exception ignore) {
        }
        return ApiResponse.ok(Collections.singletonMap("message", "已添加标签: " + name.trim()));
    }

    @PostMapping("/reload")
    public ApiResponse<Map<String, Object>> reloadAll(Authentication authentication) {
        ensureAdmin(authentication);
        Map<String, Object> out = new LinkedHashMap<String, Object>();
        out.put("status", "success");
        out.put("message", "所有数据已重载");
        return ApiResponse.ok(out);
    }

    private List<Map<String, Object>> recommendByHabit(Long userId, Double lat, Double lng, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 100));
        List<Post> posts = postRepository.findApproved(PageRequest.of(0, safeLimit * 4)).getContent();
        List<UserAction> actions = userId == null ? Collections.<UserAction>emptyList() : userActionRepository.findByUserIdOrderByActionTimeDesc(userId);

        Map<String, Integer> actionWeights = new HashMap<String, Integer>();
        actionWeights.put("view", 1);
        actionWeights.put("like", 3);
        actionWeights.put("comment", 4);
        actionWeights.put("share", 5);

        Set<Long> seenPostIds = new LinkedHashSet<Long>();
        Set<String> preferredKeywords = new LinkedHashSet<String>();
        for (UserAction action : actions) {
            if (action.getPostId() != null) {
                seenPostIds.add(action.getPostId());
                postRepository.findById(action.getPostId()).ifPresent(post -> preferredKeywords.addAll(keywordSet(post)));
            }
        }

        List<Map<String, Object>> scored = new ArrayList<Map<String, Object>>();
        for (Post post : posts) {
            if (post.getDeletedAt() != null || post.getIsAudited() == null || post.getIsAudited() != 1) {
                continue;
            }
            double score = 0.35;
            StringBuilder reason = new StringBuilder("基于您的兴趣推荐");

            if (userId != null && post.getUserId() != null && userId.equals(post.getUserId())) {
                score -= 0.4;
                reason.append("，降低自发布内容权重");
            }

            if (!preferredKeywords.isEmpty()) {
                Set<String> postKeywords = keywordSet(post);
                long hit = postKeywords.stream().filter(preferredKeywords::contains).count();
                if (hit > 0) {
                    score += Math.min(0.5, 0.12 * hit);
                    reason.append("，匹配您的常看标签");
                }
            }

            if (lat != null && lng != null && post.getLocationGeom() != null) {
                double distance = haversineMeters(lng, lat, post.getLocationGeom().getX(), post.getLocationGeom().getY());
                double distanceBonus = Math.max(0.0, 0.25 - (distance / 20000.0));
                score += distanceBonus;
                reason.append("，兼顾附近内容");
            }

            score += Math.min(0.2, (post.getLikeCount() == null ? 0 : post.getLikeCount()) * 0.01);
            score += Math.min(0.1, (post.getCommentCount() == null ? 0 : post.getCommentCount()) * 0.01);
            score += Math.min(0.1, (post.getViewCount() == null ? 0 : post.getViewCount()) * 0.002);
            if (seenPostIds.contains(post.getId())) {
                score -= 0.15;
            }

            Map<String, Object> row = new LinkedHashMap<String, Object>();
            row.put("post_id", post.getId());
            row.put("score", Math.max(0.0, Math.min(0.9999, score)));
            row.put("reason", reason.toString());
            scored.add(row);
        }

        scored.sort((a, b) -> Double.compare((Double) b.get("score"), (Double) a.get("score")));
        if (scored.size() > safeLimit) {
            return new ArrayList<Map<String, Object>>(scored.subList(0, safeLimit));
        }
        return scored;
    }

    private List<Map<String, Object>> keywordTags(String text, int topK) {
        if (text == null || text.trim().isEmpty()) {
            return Collections.emptyList();
        }
        List<String> words = tokenize(text);
        Map<String, Double> weightMap = new LinkedHashMap<String, Double>();
        for (String word : words) {
            if (word.length() < 2) {
                continue;
            }
            double add = word.length() >= 4 ? 1.0 : word.length() == 3 ? 0.85 : 0.6;
            weightMap.put(word, weightMap.getOrDefault(word, 0.0) + add);
        }
        return weightMap.entrySet().stream()
                .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                .limit(Math.max(1, topK))
                .map(e -> {
                    Map<String, Object> row = new LinkedHashMap<String, Object>();
                    row.put("tag", e.getKey());
                    row.put("weight", Math.round(e.getValue() * 100.0) / 100.0);
                    return row;
                })
                .collect(Collectors.toList());
    }

    private FilterResult filterText(String text, boolean allowAdminView) {
        if (text == null) {
            return new FilterResult(true, "", Collections.<String>emptyList());
        }
        List<String> hitWords = new ArrayList<String>();
        String filtered = text;
        for (String word : getSensitiveWords("block")) {
            if (filtered.contains(word)) {
                hitWords.add(word);
                filtered = filtered.replace(word, repeatStar(word.length()));
            }
        }
        for (String word : getSensitiveWords("review")) {
            if (filtered.contains(word)) {
                hitWords.add(word);
                if (!allowAdminView) {
                    filtered = filtered.replace(word, repeatStar(word.length()));
                }
            }
        }
        return new FilterResult(hitWords.isEmpty(), filtered, hitWords);
    }

    private List<String> getSensitiveWords(String level) {
        List<String> words = new ArrayList<String>();
        try {
            words.addAll(jdbcTemplate.queryForList("select word from sensitive_words where level = ? order by created_at desc", String.class, level));
        } catch (Exception ignore) {
            words.addAll(DEFAULT_SENSITIVE.getOrDefault(level, Collections.<String>emptyList()));
        }
        return words;
    }

    private List<Map<String, Object>> querySensitiveWords(String level) {
        try {
            return jdbcTemplate.queryForList("select word, created_at from sensitive_words where level = ? order by created_at desc", level);
        } catch (Exception ignore) {
            List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();
            for (String word : DEFAULT_SENSITIVE.getOrDefault(level, Collections.<String>emptyList())) {
                Map<String, Object> row = new LinkedHashMap<String, Object>();
                row.put("word", word);
                row.put("created_at", LocalDateTime.now().toString().replace('T', ' '));
                rows.add(row);
            }
            return rows;
        }
    }

    private Set<String> keywordSet(Post post) {
        Set<String> result = new LinkedHashSet<String>();
        result.addAll(tokenize(post.getTitle()));
        result.addAll(tokenize(post.getContent()));
        result.addAll(tokenize(post.getCategory()));
        result.addAll(tokenize(post.getLocationName()));
        result.removeIf(s -> s == null || s.length() < 2 || DEFAULT_STOPWORDS.contains(s));
        return result;
    }

    private List<String> tokenize(String text) {
        if (text == null) {
            return Collections.emptyList();
        }
        String normalized = text.toLowerCase(Locale.ROOT);
        for (String stop : DEFAULT_STOPWORDS) {
            normalized = normalized.replace(stop, " ");
        }
        String[] parts = normalized.split("[^\\p{IsHan}a-z0-9]+|");
        List<String> result = new ArrayList<String>();
        for (String part : parts) {
            if (part == null) {
                continue;
            }
            String trimmed = part.trim();
            if (!trimmed.isEmpty() && !DEFAULT_STOPWORDS.contains(trimmed)) {
                result.add(trimmed);
            }
        }
        return result;
    }

    private boolean canManageModeration(Authentication authentication) {
        if (authentication == null || authentication.getPrincipal() == null) {
            return false;
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof UserPrincipal) {
            String role = ((UserPrincipal) principal).getRole();
            return role != null && "admin".equalsIgnoreCase(role);
        }
        return false;
    }

    private void ensureAdmin(Authentication authentication) {
        if (!canManageModeration(authentication)) {
            throw new IllegalArgumentException("无权限");
        }
    }

    private String normalizeLevel(String level) {
        return "block".equalsIgnoreCase(level) ? "block" : "review";
    }

    private static double haversineMeters(double lng1, double lat1, double lng2, double lat2) {
        double r = 6371000.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return 2 * r * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    private String repeatStar(int count) {
        int safe = Math.max(2, count);
        StringBuilder sb = new StringBuilder(safe);
        for (int i = 0; i < safe; i++) {
            sb.append('*');
        }
        return sb.toString();
    }

    private static String asText(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static int asInt(Object value, int defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private static Long asLong(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (Exception e) {
            return null;
        }
    }

    private static Double asDouble(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (Exception e) {
            return null;
        }
    }

    private static class FilterResult {
        private final boolean valid;
        private final String filteredText;
        private final List<String> hitWords;

        private FilterResult(boolean valid, String filteredText, List<String> hitWords) {
            this.valid = valid;
            this.filteredText = filteredText;
            this.hitWords = hitWords;
        }
    }
}
