package org.example.geo;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.common.ApiResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@RestController
@RequestMapping("/api/geo")
public class GeoController {
    private final String amapKey;
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public GeoController(@Value("${app.amap.key:}") String amapKey) {
        this.amapKey = amapKey;
    }

    @GetMapping("/suggest")
    public ApiResponse<Object> suggest(@RequestParam String keyword,
                                       @RequestParam(required = false) String city) throws Exception {
        requireKey();
        if (!StringUtils.hasText(keyword)) {
            throw new IllegalArgumentException("keyword 不能为空");
        }
        String url = "https://restapi.amap.com/v3/assistant/inputtips?keywords={keywords}&key={key}&datatype=all";
        if (StringUtils.hasText(city)) {
            url += "&city={city}";
            ResponseEntity<String> resp = restTemplate.getForEntity(url, String.class, keyword, amapKey, city);
            return ApiResponse.ok(objectMapper.readValue(resp.getBody(), Map.class));
        }
        ResponseEntity<String> resp = restTemplate.getForEntity(url, String.class, keyword, amapKey);
        return ApiResponse.ok(objectMapper.readValue(resp.getBody(), Map.class));
    }

    @GetMapping("/regeo")
    public ApiResponse<Object> regeo(@RequestParam double lng,
                                     @RequestParam double lat,
                                     @RequestParam(defaultValue = "base") String extensions) throws Exception {
        requireKey();
        String loc = String.format(java.util.Locale.US, "%.6f,%.6f", lng, lat);
        String url = "https://restapi.amap.com/v3/geocode/regeo?location={loc}&key={key}&extensions={ext}&output=JSON";
        ResponseEntity<String> resp = restTemplate.getForEntity(url, String.class, loc, amapKey, extensions);
        return ApiResponse.ok(objectMapper.readValue(resp.getBody(), Map.class));
    }

    private void requireKey() {
        if (!StringUtils.hasText(amapKey)) {
            throw new IllegalArgumentException("AMAP_KEY 未配置（请在环境变量或 application.yml 配置 app.amap.key）");
        }
    }
}
