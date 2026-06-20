package org.example.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class SparkHttpClient {
    private final SparkConfig config;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public SparkHttpClient(SparkConfig config) {
        this.config = config;
    }

    public String chat(Map<String, Object> payload) throws Exception {
        String body = objectMapper.writeValueAsString(payload);
        System.out.println("[SparkHttpClient] request=" + body);

        URL target = new URL(config.getApiUrl());
        HttpURLConnection con = (HttpURLConnection) target.openConnection();
        con.setRequestMethod("POST");
        con.setRequestProperty("Content-Type", "application/json");
        con.setRequestProperty("Authorization", "Bearer " + config.getApiPassword().trim());
        con.setDoOutput(true);

        try (OutputStream os = con.getOutputStream()) {
            os.write(body.getBytes(StandardCharsets.UTF_8));
            os.flush();
        }

        int code = con.getResponseCode();
        System.out.println("[SparkHttpClient] responseCode=" + code);

        BufferedReader reader = new BufferedReader(new InputStreamReader(
                code >= 200 && code < 300 ? con.getInputStream() : con.getErrorStream(),
                StandardCharsets.UTF_8
        ));
        StringBuilder response = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            response.append(line);
        }
        reader.close();

        String raw = response.toString();
        System.out.println("[SparkHttpClient] response=" + raw);
        return raw;
    }

    public Map<String, Object> buildRequest(String query, String userId, List<Map<String, String>> messages) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("user", userId);
        root.put("model", config.getModel());
        root.put("messages", messages);
        root.put("stream", false);
        root.put("max_tokens", 4096);
        root.put("thinking", new LinkedHashMap<String, Object>() {{
            put("type", "enabled");
        }});
        return root;
    }

    public String extractContent(String raw) {
        try {
            JsonNode node = objectMapper.readTree(raw);
            JsonNode choices = node.path("choices");
            if (choices.isArray() && choices.size() > 0) {
                JsonNode first = choices.get(0);
                JsonNode msg = first.path("message");
                if (msg.hasNonNull("content")) {
                    return msg.path("content").asText("");
                }
            }
            return raw;
        } catch (Exception e) {
            return raw;
        }
    }
}
