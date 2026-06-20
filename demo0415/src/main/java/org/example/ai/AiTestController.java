package org.example.ai;

import org.example.common.ApiResponse;
import org.example.config.SparkConfig;
import org.example.config.SparkHttpClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ai")
public class AiTestController {
    private final SparkConfig sparkConfig;

    public AiTestController(SparkConfig sparkConfig) {
        this.sparkConfig = sparkConfig;
    }

    @GetMapping("/test-chat")
    public ApiResponse<Map<String, Object>> testChat(@RequestParam(required = false) String q) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("input", q);
        try {
            SparkHttpClient client = new SparkHttpClient(sparkConfig);
            List<Map<String, String>> messages = new ArrayList<>();
            Map<String, String> sys = new LinkedHashMap<>();
            sys.put("role", "system");
            sys.put("content", "你是一个简单的测试助手。请只用一句话回答用户问题。");
            messages.add(sys);

            Map<String, String> user = new LinkedHashMap<>();
            user.put("role", "user");
            user.put("content", q == null ? "你好" : q);
            messages.add(user);

            Map<String, Object> req = client.buildRequest(q, "test-user", messages);
            String raw = client.chat(req);
            String content = client.extractContent(raw);
            data.put("raw", raw);
            data.put("answer", content);
            data.put("ok", true);
        } catch (Exception e) {
            data.put("ok", false);
            data.put("error", e.getMessage());
        }
        return ApiResponse.ok(data);
    }
}
