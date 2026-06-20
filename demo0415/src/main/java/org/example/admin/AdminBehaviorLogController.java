package org.example.admin;

import org.example.common.ApiResponse;
import org.example.domain.UserAction;
import org.example.repo.UserActionRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.persistence.criteria.Predicate;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin/behavior-logs")
public class AdminBehaviorLogController {

    private final UserActionRepository userActionRepository;

    public AdminBehaviorLogController(UserActionRepository userActionRepository) {
        this.userActionRepository = userActionRepository;
    }

    @GetMapping
    public ApiResponse<Map<String, Object>> list(@RequestParam(defaultValue = "0") int page,
                                                 @RequestParam(defaultValue = "20") int size,
                                                 @RequestParam(required = false) String startDate,
                                                 @RequestParam(required = false) String endDate,
                                                 @RequestParam(required = false) String actionType,
                                                 @RequestParam(required = false) Long userId,
                                                 @RequestParam(required = false) Long postId) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.max(size, 1));
        Page<UserAction> result = userActionRepository.findAll(buildSpec(startDate, endDate, actionType, userId, postId), pageable);

        Map<String, Object> out = new HashMap<String, Object>();
        out.put("total", result.getTotalElements());
        out.put("records", result.getContent().stream().map(this::toRow).collect(Collectors.toList()));
        return ApiResponse.ok(out);
    }

    @GetMapping("/overview")
    public ApiResponse<Map<String, Object>> overview(@RequestParam(required = false) String startDate,
                                                     @RequestParam(required = false) String endDate,
                                                     @RequestParam(required = false) String actionType,
                                                     @RequestParam(required = false) Long userId,
                                                     @RequestParam(required = false) Long postId) {
        List<UserAction> actions = userActionRepository.findAll(buildSpec(startDate, endDate, actionType, userId, postId));

        Map<String, Object> out = new LinkedHashMap<String, Object>();
        out.put("totalCount", actions.size());
        out.put("activeUsers", actions.stream().map(UserAction::getUserId).distinct().count());

        List<Map<String, Object>> actionTypeDist = actions.stream()
                .collect(Collectors.groupingBy(a -> a.getActionType() == null ? "unknown" : a.getActionType(), Collectors.counting()))
                .entrySet().stream()
                .map(e -> {
                    Map<String, Object> row = new HashMap<String, Object>();
                    row.put("name", actionTypeName(e.getKey()));
                    row.put("value", e.getValue());
                    return row;
                })
                .collect(Collectors.toList());
        out.put("actionTypeDist", actionTypeDist);

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MM-dd");
        List<Map<String, Object>> dailyTrend = actions.stream()
                .filter(a -> a.getActionTime() != null)
                .collect(Collectors.groupingBy(a -> a.getActionTime().toLocalDate(), Collectors.counting()))
                .entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> {
                    Map<String, Object> row = new HashMap<String, Object>();
                    row.put("day", e.getKey().format(fmt));
                    row.put("value", e.getValue());
                    return row;
                })
                .collect(Collectors.toList());
        out.put("dailyTrend", dailyTrend);

        List<Map<String, Object>> topUsers = actions.stream()
                .collect(Collectors.groupingBy(UserAction::getUserId, Collectors.counting()))
                .entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                .limit(10)
                .map(e -> {
                    Map<String, Object> row = new HashMap<String, Object>();
                    row.put("name", e.getKey());
                    row.put("value", e.getValue());
                    return row;
                })
                .collect(Collectors.toList());
        out.put("topUsers", topUsers);

        List<Map<String, Object>> topPosts = actions.stream()
                .collect(Collectors.groupingBy(UserAction::getPostId, Collectors.counting()))
                .entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                .limit(10)
                .map(e -> {
                    Map<String, Object> row = new HashMap<String, Object>();
                    row.put("name", e.getKey());
                    row.put("value", e.getValue());
                    return row;
                })
                .collect(Collectors.toList());
        out.put("topPosts", topPosts);

        return ApiResponse.ok(out);
    }

    private Specification<UserAction> buildSpec(String startDate, String endDate, String actionType, Long userId, Long postId) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new java.util.ArrayList<Predicate>();
            if (startDate != null && !startDate.trim().isEmpty()) {
                LocalDateTime start = LocalDate.parse(startDate.trim()).atStartOfDay();
                predicates.add(cb.greaterThanOrEqualTo(root.get("actionTime"), start));
            }
            if (endDate != null && !endDate.trim().isEmpty()) {
                LocalDateTime end = LocalDate.parse(endDate.trim()).atTime(LocalTime.MAX);
                predicates.add(cb.lessThanOrEqualTo(root.get("actionTime"), end));
            }
            if (actionType != null && !actionType.trim().isEmpty()) {
                predicates.add(cb.equal(root.get("actionType"), actionType.trim()));
            }
            if (userId != null) {
                predicates.add(cb.equal(root.get("userId"), userId));
            }
            if (postId != null) {
                predicates.add(cb.equal(root.get("postId"), postId));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private Map<String, Object> toRow(UserAction action) {
        Map<String, Object> row = new LinkedHashMap<String, Object>();
        row.put("id", action.getId());
        row.put("user_id", action.getUserId());
        row.put("post_id", action.getPostId());
        row.put("action_type", actionTypeName(action.getActionType()));
        row.put("action_time", action.getActionTime());
        row.put("duration", action.getDuration());
        row.put("metadata", action.getMetadata());
        return row;
    }

    private String actionTypeName(String type) {
        if (type == null) {
            return "未知";
        }
        switch (type) {
            case "view":
                return "浏览";
            case "like":
                return "点赞";
            case "comment":
                return "评论";
            case "share":
                return "分享";
            default:
                return type;
        }
    }
}
