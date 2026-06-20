package org.example.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.domain.Post;
import org.example.domain.PostTag;
import org.example.domain.Tag;
import org.example.domain.User;
import org.example.domain.UserAction;
import org.example.domain.UserProfile;
import org.example.repo.PostRepository;
import org.example.repo.PostTagRepository;
import org.example.repo.TagRepository;
import org.example.repo.UserActionRepository;
import org.example.repo.UserProfileRepository;
import org.example.repo.UserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class SparkRecommendService {
    private final SparkConfig sparkConfig;
    private final PostRepository postRepository;
    private final UserActionRepository userActionRepository;
    private final UserProfileRepository userProfileRepository;
    private final PostTagRepository postTagRepository;
    private final TagRepository tagRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public SparkRecommendService(SparkConfig sparkConfig,
                                 PostRepository postRepository,
                                 UserActionRepository userActionRepository,
                                 UserProfileRepository userProfileRepository,
                                 PostTagRepository postTagRepository,
                                 TagRepository tagRepository,
                                 UserRepository userRepository) {
        this.sparkConfig = sparkConfig;
        this.postRepository = postRepository;
        this.userActionRepository = userActionRepository;
        this.userProfileRepository = userProfileRepository;
        this.postTagRepository = postTagRepository;
        this.tagRepository = tagRepository;
        this.userRepository = userRepository;
    }

    public RecommendationResult recommend(Long userId, String query, int limit) {
        List<Post> candidates = postRepository.findApproved(PageRequest.of(0, 200)).getContent();
        List<UserAction> actions = userActionRepository.findByUserIdOrderByActionTimeDesc(userId);
        Map<String, Double> interest = buildInterest(actions);
        Map<String, Double> profileVector = loadProfileVector(userId);
        List<String> queryTokens = tokenize(query);
        Map<String, Object> prompt = buildPrompt(query, actions, candidates, interest, profileVector);
        Map<String, Object> ai = buildFallbackAiResponse(query, interest, queryTokens);
        RecommendationPack pack = rankPosts(candidates, userId, aiStringList(ai, "tags"), aiStringList(ai, "keywords"), queryTokens, profileVector, interest, limit, 100d, 1d, 1d, 8d, 4d, 1d);
        return new RecommendationResult(pack.posts, String.valueOf(ai.getOrDefault("answer", "")), String.valueOf(ai.getOrDefault("reason", "")), prompt, ai, null);
    }

    public List<Map<String, Object>> recommendLikeItems(Long userId, int limit,
                                                         double cosineWeight,
                                                         double distanceWeight,
                                                         double heatWeight,
                                                         double tagMatchWeight,
                                                         double categoryMatchWeight,
                                                         double imageBonus) {
        List<Post> candidates = postRepository.findApproved(PageRequest.of(0, 200)).getContent();
        RecommendationPack pack = rankPosts(candidates, userId, Collections.<String>emptyList(), Collections.<String>emptyList(), Collections.<String>emptyList(), loadProfileVector(userId), new HashMap<String, Double>(), limit,
                cosineWeight, distanceWeight, heatWeight, tagMatchWeight, categoryMatchWeight, imageBonus);
        List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();
        for (ScoredPost sp : pack.scoredPosts) {
            if (rows.size() >= limit) break;
            Map<String, Object> row = new LinkedHashMap<String, Object>();
            row.put("id", sp.post.getId());
            row.put("title", sp.post.getTitle());
            row.put("content", sp.post.getContent());
            row.put("category", sp.post.getCategory());
            row.put("locationName", sp.post.getLocationName());
            row.put("distanceMeters", round(sp.distanceMeters));
            row.put("cosine", round(sp.cosine));
            row.put("cosineScore", round(sp.cosineScore));
            row.put("distanceScore", round(sp.distanceScore));
            row.put("heatScore", round(sp.heatScore));
            row.put("tagMatchScore", round(sp.tagMatchScore));
            row.put("categoryMatchScore", round(sp.categoryMatchScore));
            row.put("imageBonusScore", round(sp.imageBonusScore));
            row.put("finalScore", round(sp.score));
            row.put("score", round(sp.score));
            rows.add(row);
        }
        return rows;
    }

    private Map<String, Object> buildPrompt(String query, List<UserAction> actions, List<Post> candidates, Map<String, Double> interest, Map<String, Double> profileVector) {
        Map<String, Object> prompt = new LinkedHashMap<String, Object>();
        prompt.put("query", query);
        prompt.put("history_actions", actions.size());
        prompt.put("top_categories", interest);
        prompt.put("profile_vector", profileVector);
        List<Map<String, Object>> sample = new ArrayList<Map<String, Object>>();
        for (int i = 0; i < Math.min(10, candidates.size()); i++) {
            Post p = candidates.get(i);
            Map<String, Object> item = new LinkedHashMap<String, Object>();
            item.put("id", p.getId());
            item.put("title", p.getTitle());
            item.put("category", p.getCategory());
            item.put("images", p.getImages() == null ? 0 : p.getImages().length);
            sample.add(item);
        }
        prompt.put("candidate_posts", sample);
        prompt.put("instruction", "纯画像推荐，不需要AI，仅参考这些数据用于日志或调试");
        return prompt;
    }

    private RecommendationPack rankPosts(List<Post> candidates,
                                         Long userId,
                                         List<String> tags,
                                         List<String> keywords,
                                         List<String> queryTokens,
                                         Map<String, Double> profileVector,
                                         Map<String, Double> interest,
                                         int limit,
                                         double cosineWeight,
                                         double distanceWeight,
                                         double heatWeight,
                                         double tagMatchWeight,
                                         double categoryMatchWeight,
                                         double imageBonus) {
        List<ScoredPost> scored = new ArrayList<ScoredPost>();
        for (Post post : candidates) {
            String hay = buildHaystack(post);
            Map<String, Double> postVector = loadPostTagVector(post.getId());
            double cosine = cosine(profileVector, postVector);
            double cosineScore = cosine * cosineWeight;

            double tagMatchScore = 0.0d;
            double categoryMatchScore = 0.0d;
            for (String tag : tags) {
                if (tag == null) continue;
                String t = tag.toLowerCase();
                if (hay.contains(t)) tagMatchScore += tagMatchWeight;
                if (post.getCategory() != null && post.getCategory().toLowerCase().contains(t)) categoryMatchScore += categoryMatchWeight;
            }
            for (String keyword : keywords) {
                if (keyword == null) continue;
                String k = keyword.toLowerCase();
                if (hay.contains(k)) tagMatchScore += tagMatchWeight * 0.5d;
            }
            for (String token : queryTokens) {
                if (!token.isEmpty() && hay.contains(token)) tagMatchScore += tagMatchWeight * 0.25d;
            }
            double interestScore = interest.getOrDefault(post.getCategory() == null ? "" : post.getCategory().toLowerCase(), 0.0d) * 3.0d;
            double distanceMeters = distanceMeters(userId, post);
            double distanceScore = distanceScore(distanceMeters, distanceWeight);
            double heatScore = heatBoost(post) * heatWeight;
            double imageBonusScore = (post.getImages() != null && post.getImages().length > 0) ? imageBonus : 0.0d;

            double finalScore = cosineScore + tagMatchScore + categoryMatchScore + interestScore + distanceScore + heatScore + imageBonusScore;
            scored.add(new ScoredPost(post, finalScore, cosine, cosineScore, distanceScore, heatScore, tagMatchScore, categoryMatchScore, imageBonusScore, distanceMeters));
        }

        scored.sort((a, b) -> Double.compare(b.score, a.score));
        List<Post> posts = new ArrayList<Post>();
        for (ScoredPost sp : scored) {
            if (posts.size() >= limit) break;
            posts.add(sp.post);
        }
        return new RecommendationPack(posts, scored);
    }

    private double distanceScore(double meters, double distanceWeight) {
        if (meters < 0) return 0.0d;
        double base;
        if (meters <= 500) base = 12.0d;
        else if (meters <= 2000) base = 10.0d;
        else if (meters <= 5000) base = 8.0d;
        else if (meters <= 10000) base = 5.0d;
        else base = 1.5d;
        return base * distanceWeight;
    }

    private double heatBoost(Post post) {
        double views = post.getViewCount() == null ? 0.0d : post.getViewCount();
        double likes = post.getLikeCount() == null ? 0.0d : post.getLikeCount();
        double comments = post.getCommentCount() == null ? 0.0d : post.getCommentCount();
        return views * 0.01d + likes * 0.08d + comments * 0.10d;
    }

    private double distanceMeters(Long userId, Post post) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null || user.getLocationGeom() == null || post.getLocationGeom() == null) return -1.0d;
        return user.getLocationGeom().distance(post.getLocationGeom());
    }

    private List<String> aiStringList(Map<String, Object> ai, String key) {
        Object v = ai.get(key);
        if (!(v instanceof List)) return Collections.emptyList();
        List<?> list = (List<?>) v;
        List<String> out = new ArrayList<String>();
        for (Object item : list) {
            if (item != null) out.add(String.valueOf(item));
        }
        return out;
    }

    private Map<String, Object> buildFallbackAiResponse(String query, Map<String, Double> interest, List<String> queryTokens) {
        Map<String, Object> res = new LinkedHashMap<String, Object>();
        String topCategory = interest.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse("post");
        List<String> tags = new ArrayList<String>();
        if (query != null && !query.trim().isEmpty()) tags.add(query.trim().toLowerCase());
        tags.add(topCategory);
        tags.addAll(queryTokens);
        List<String> uniq = new ArrayList<String>(new LinkedHashSet<String>(tags));
        res.put("answer", "根据你的输入和历史行为，我已经为你筛选了更相关的帖子。你可以先看看下面这些结果。");
        res.put("tags", uniq);
        res.put("keywords", uniq);
        res.put("intent", "recommend");
        res.put("reason", "优先匹配你的输入关键词和最近高频行为分类：" + topCategory);
        return res;
    }

    private Map<String, Double> buildInterest(List<UserAction> actions) {
        Map<String, Double> map = new HashMap<String, Double>();
        for (UserAction action : actions) {
            Post post = postRepository.findById(action.getPostId()).orElse(null);
            if (post == null) continue;
            double weight = 1.0d;
            if ("like".equalsIgnoreCase(action.getActionType())) weight = 3.0d;
            else if ("comment".equalsIgnoreCase(action.getActionType())) weight = 4.0d;
            else if ("share".equalsIgnoreCase(action.getActionType())) weight = 5.0d;
            String category = post.getCategory() == null ? "" : post.getCategory().toLowerCase();
            map.put(category, map.getOrDefault(category, 0.0d) + weight);
        }
        return map;
    }

    private List<String> tokenize(String query) {
        if (query == null || query.trim().isEmpty()) return Collections.emptyList();
        String[] parts = query.toLowerCase().split("[^\\p{IsHan}a-z0-9_]+");
        List<String> tokens = new ArrayList<String>();
        for (String p : parts) {
            if (p != null && p.trim().length() > 0) tokens.add(p.trim());
        }
        return tokens;
    }

    private Map<String, Double> loadProfileVector(Long userId) {
        Map<String, Double> vector = new HashMap<String, Double>();
        UserProfile profile = userProfileRepository.findByUserId(userId).orElse(null);
        if (profile != null && profile.getTagVector() != null) {
            vector.putAll(profile.getTagVector());
        }
        return vector;
    }

    private Map<String, Double> loadPostTagVector(Long postId) {
        Map<String, Double> vector = new HashMap<String, Double>();
        List<PostTag> postTags = postTagRepository.findByPostId(postId);
        for (PostTag postTag : postTags) {
            Tag tag = tagRepository.findById(postTag.getTagId()).orElse(null);
            if (tag == null || tag.getName() == null) continue;
            double weight = postTag.getWeight() == null ? 1.0d : postTag.getWeight().doubleValue();
            vector.put(tag.getName(), vector.getOrDefault(tag.getName(), 0.0d) + weight);
        }
        return vector;
    }

    private double cosine(Map<String, Double> a, Map<String, Double> b) {
        if (a.isEmpty() || b.isEmpty()) return 0.0d;
        Set<String> keys = new HashSet<String>();
        keys.addAll(a.keySet());
        keys.addAll(b.keySet());
        double dot = 0.0d;
        double na = 0.0d;
        double nb = 0.0d;
        for (String k : keys) {
            double x = a.getOrDefault(k, 0.0d);
            double y = b.getOrDefault(k, 0.0d);
            dot += x * y;
            na += x * x;
            nb += y * y;
        }
        if (na == 0.0d || nb == 0.0d) return 0.0d;
        return dot / (Math.sqrt(na) * Math.sqrt(nb));
    }

    private String buildHaystack(Post post) {
        return ((post.getTitle() == null ? "" : post.getTitle()) + " " +
                (post.getContent() == null ? "" : post.getContent()) + " " +
                (post.getCategory() == null ? "" : post.getCategory()) + " " +
                (post.getLocationName() == null ? "" : post.getLocationName())).toLowerCase();
    }

    private double round(double value) {
        return Math.round(value * 100.0d) / 100.0d;
    }

    public static class RecommendationResult {
        private final List<Post> posts;
        private final String answer;
        private final String reason;
        private final Map<String, Object> prompt;
        private final Map<String, Object> aiResponse;
        private final String raw;

        public RecommendationResult(List<Post> posts, String answer, String reason, Map<String, Object> prompt, Map<String, Object> aiResponse, String raw) {
            this.posts = posts;
            this.answer = answer;
            this.reason = reason;
            this.prompt = prompt;
            this.aiResponse = aiResponse;
            this.raw = raw;
        }

        public List<Post> getPosts() { return posts; }
        public String getAnswer() { return answer; }
        public String getReason() { return reason; }
        public Map<String, Object> getPrompt() { return prompt; }
        public Map<String, Object> getAiResponse() { return aiResponse; }
        public String getRaw() { return raw; }
    }

    private static class ScoredPost {
        private final Post post;
        private final double score;
        private final double cosine;
        private final double cosineScore;
        private final double distanceScore;
        private final double heatScore;
        private final double tagMatchScore;
        private final double categoryMatchScore;
        private final double imageBonusScore;
        private final double distanceMeters;

        private ScoredPost(Post post, double score, double cosine, double cosineScore, double distanceScore, double heatScore, double tagMatchScore, double categoryMatchScore, double imageBonusScore, double distanceMeters) {
            this.post = post;
            this.score = score;
            this.cosine = cosine;
            this.cosineScore = cosineScore;
            this.distanceScore = distanceScore;
            this.heatScore = heatScore;
            this.tagMatchScore = tagMatchScore;
            this.categoryMatchScore = categoryMatchScore;
            this.imageBonusScore = imageBonusScore;
            this.distanceMeters = distanceMeters;
        }
    }

    private static class RecommendationPack {
        private final List<Post> posts;
        private final List<ScoredPost> scoredPosts;

        private RecommendationPack(List<Post> posts, List<ScoredPost> scoredPosts) {
            this.posts = posts;
            this.scoredPosts = scoredPosts;
        }
    }
}
