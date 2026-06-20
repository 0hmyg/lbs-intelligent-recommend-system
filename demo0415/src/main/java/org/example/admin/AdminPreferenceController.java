package org.example.admin;

import org.example.common.ApiResponse;
import org.example.service.UserPreferenceService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/user-preferences")
public class AdminPreferenceController {
    private final UserPreferenceService userPreferenceService;

    public AdminPreferenceController(UserPreferenceService userPreferenceService) {
        this.userPreferenceService = userPreferenceService;
    }

    @GetMapping
    public ApiResponse<Map<String, Object>> list() {
        Map<String, Object> out = new HashMap<String, Object>();
        List<Map<String, Object>> preferences = userPreferenceService.listAllPreferences();
        out.put("total", preferences.size());
        out.put("records", preferences);
        return ApiResponse.ok(out);
    }
}
