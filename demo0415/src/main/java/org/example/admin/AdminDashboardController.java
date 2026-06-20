package org.example.admin;

import org.example.common.ApiResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/dashboard")
public class AdminDashboardController {

    private final JdbcTemplate jdbcTemplate;

    public AdminDashboardController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/overview")
    public ApiResponse<Map<String, Object>> overview() {
        Map<String, Object> out = new HashMap<String, Object>();

        Integer userCount = jdbcTemplate.queryForObject("select count(1) from users", Integer.class);
        Integer postCount = jdbcTemplate.queryForObject("select count(1) from posts where deleted_at is null", Integer.class);
        Integer commentCount = jdbcTemplate.queryForObject("select count(1) from comments where deleted_at is null", Integer.class);
        Integer actionCount = jdbcTemplate.queryForObject("select count(1) from user_actions", Integer.class);

        out.put("userCount", userCount == null ? 0 : userCount);
        out.put("postCount", postCount == null ? 0 : postCount);
        out.put("commentCount", commentCount == null ? 0 : commentCount);
        out.put("actionCount", actionCount == null ? 0 : actionCount);

        List<Map<String, Object>> trend7d = jdbcTemplate.queryForList(
                "select to_char(d,'MM-DD') as day, " +
                        "coalesce(u.c,0) as users, coalesce(p.c,0) as posts, coalesce(c.c,0) as comments " +
                        "from generate_series(current_date - interval '6 day', current_date, interval '1 day') d " +
                        "left join (select date(created_at) dd, count(1) c from users group by date(created_at)) u on date(d)=u.dd " +
                        "left join (select date(created_at) dd, count(1) c from posts where deleted_at is null group by date(created_at)) p on date(d)=p.dd " +
                        "left join (select date(created_at) dd, count(1) c from comments where deleted_at is null group by date(created_at)) c on date(d)=c.dd " +
                        "order by d"
        );
        out.put("trend7d", trend7d);

        List<Map<String, Object>> categoryDist = jdbcTemplate.queryForList(
                "select category as name, count(1) as value from posts where deleted_at is null group by category order by count(1) desc"
        );
        out.put("categoryDist", categoryDist);

        List<Map<String, Object>> auditDist = jdbcTemplate.queryForList(
                "select cast(is_audited as varchar) as name, count(1) as value from posts where deleted_at is null group by is_audited order by is_audited"
        );
        out.put("auditDist", auditDist);

        List<Map<String, Object>> hotAreas = jdbcTemplate.queryForList(
                "select coalesce(location_name,'未设置') as name, count(1) as value " +
                        "from posts where deleted_at is null group by location_name order by count(1) desc limit 10"
        );
        out.put("hotAreas", hotAreas);

        return ApiResponse.ok(out);
    }
}

