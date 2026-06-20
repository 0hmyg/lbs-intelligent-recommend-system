package org.example.recommend;

import org.example.common.ApiResponse;
import org.example.config.SparkRecommendService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/recommendations")
public class RecommendTestController {
    private final SparkRecommendService recommendService;

    public RecommendTestController(SparkRecommendService recommendService) {
        this.recommendService = recommendService;
    }

    @GetMapping("/test")
    public ApiResponse<Map<String, Object>> test(@RequestParam(required = false) String q,
                                                 @RequestParam(defaultValue = "5") int limit) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("q", q);
        data.put("limit", limit);
        data.put("status", "ok");
        data.put("note", "这是本地测试版，不依赖星火真实接口");
        return ApiResponse.ok(data);
    }
}
