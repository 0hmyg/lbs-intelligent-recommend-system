package org.example.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Component
public class PythonModerationClient {
    private final RestTemplate restTemplate;
    private final String baseUrl;

    public PythonModerationClient(RestTemplateBuilder builder,
                                  @Value("${app.python.base-url:http://localhost:5000}") String baseUrl) {
        this.restTemplate = builder
                .setConnectTimeout(Duration.ofSeconds(3))
                .setReadTimeout(Duration.ofSeconds(5))
                .build();
        this.baseUrl = baseUrl;
    }

    public boolean healthy() {
        try {
            Map<?, ?> resp = restTemplate.getForObject(baseUrl + "/health", Map.class);
            return resp != null;
        } catch (Exception e) {
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    public ModerationResult moderate(String text) {
        try {
            Map<String, Object> body = new HashMap<String, Object>();
            body.put("text", text == null ? "" : text);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<Map<String, Object>>(body, headers);

            Map<String, Object> data = restTemplate.postForObject(baseUrl + "/api/filter", entity, Map.class);
            if (data == null) {
                return ModerationResult.reject("python 服务未返回结果", null);
            }

            boolean valid = Boolean.TRUE.equals(data.get("is_valid"));
            String filteredText = data.get("filtered_text") == null ? null : String.valueOf(data.get("filtered_text"));
            Object hitWords = data.get("hit_words");
            if (valid) {
                return ModerationResult.pass(filteredText, hitWords);
            }
            return ModerationResult.reject(filteredText, hitWords);
        } catch (Exception e) {
            return ModerationResult.reject("python 服务调用失败", null);
        }
    }

    @SuppressWarnings("unchecked")
    public TaggingResult tagPost(Long postId, String title, String content, String category, Integer topK, boolean saveToDb) {
        try {
            Map<String, Object> body = new HashMap<String, Object>();
            body.put("post_id", postId);
            body.put("title", title == null ? "" : title);
            body.put("content", content == null ? "" : content);
            body.put("category", category == null ? "" : category);
            body.put("top_k", topK == null ? 5 : topK);
            body.put("save_to_db", saveToDb);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<Map<String, Object>>(body, headers);

            Map<String, Object> data = restTemplate.postForObject(baseUrl + "/api/tagging", entity, Map.class);
            if (data == null) {
                return TaggingResult.fail("python 标签服务未返回结果");
            }
            return TaggingResult.success(data);
        } catch (Exception e) {
            return TaggingResult.fail("python 标签服务调用失败");
        }
    }

    public static class ModerationResult {
        private final boolean passed;
        private final String filteredText;
        private final Object hitWords;

        private ModerationResult(boolean passed, String filteredText, Object hitWords) {
            this.passed = passed;
            this.filteredText = filteredText;
            this.hitWords = hitWords;
        }

        public static ModerationResult pass(String filteredText, Object hitWords) {
            return new ModerationResult(true, filteredText, hitWords);
        }

        public static ModerationResult reject(String filteredText, Object hitWords) {
            return new ModerationResult(false, filteredText, hitWords);
        }

        public boolean isPassed() {
            return passed;
        }

        public String getFilteredText() {
            return filteredText;
        }

        public Object getHitWords() {
            return hitWords;
        }
    }

    public static class TaggingResult {
        private final boolean success;
        private final Map<String, Object> data;
        private final String errorMessage;

        private TaggingResult(boolean success, Map<String, Object> data, String errorMessage) {
            this.success = success;
            this.data = data;
            this.errorMessage = errorMessage;
        }

        public static TaggingResult success(Map<String, Object> data) {
            return new TaggingResult(true, data, null);
        }

        public static TaggingResult fail(String errorMessage) {
            return new TaggingResult(false, null, errorMessage);
        }

        public boolean isSuccess() {
            return success;
        }

        public Map<String, Object> getData() {
            return data;
        }

        public String getErrorMessage() {
            return errorMessage;
        }
    }
}
