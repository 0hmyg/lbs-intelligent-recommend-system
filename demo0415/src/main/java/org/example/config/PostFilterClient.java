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
public class PostFilterClient {
    private final RestTemplate restTemplate;
    private final String filterUrl;

    public PostFilterClient(RestTemplateBuilder builder,
                            @Value("${app.filter.url:http://localhost:5000/api/filter}") String filterUrl) {
        this.restTemplate = builder.setConnectTimeout(Duration.ofSeconds(3)).setReadTimeout(Duration.ofSeconds(6)).build();
        this.filterUrl = filterUrl;
    }

    @SuppressWarnings("unchecked")
    public FilterResult filter(String text) {
        Map<String, Object> body = new HashMap<String, Object>();
        body.put("text", text == null ? "" : text);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        Map<String, Object> resp = restTemplate.postForObject(filterUrl, new HttpEntity<Map<String, Object>>(body, headers), Map.class);
        if (resp == null) return FilterResult.reject(text, new String[0]);
        Object isValid = resp.get("is_valid");
        String filteredText = resp.get("filtered_text") == null ? text : String.valueOf(resp.get("filtered_text"));
        Object hitWordsObj = resp.get("hit_words");
        String[] hitWords = hitWordsObj instanceof java.util.Collection ? ((java.util.Collection<?>) hitWordsObj).stream().map(String::valueOf).toArray(String[]::new) : new String[0];
        return new FilterResult(Boolean.TRUE.equals(isValid), filteredText, hitWords);
    }

    public static class FilterResult {
        private final boolean valid;
        private final String filteredText;
        private final String[] hitWords;

        public FilterResult(boolean valid, String filteredText, String[] hitWords) {
            this.valid = valid;
            this.filteredText = filteredText;
            this.hitWords = hitWords;
        }
        public static FilterResult reject(String text, String[] words) { return new FilterResult(false, text, words); }
        public boolean isValid() { return valid; }
        public String getFilteredText() { return filteredText; }
        public String[] getHitWords() { return hitWords; }
    }
}
