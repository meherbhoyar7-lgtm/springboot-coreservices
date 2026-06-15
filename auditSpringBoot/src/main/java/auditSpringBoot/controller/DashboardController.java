package auditSpringBoot.controller;

import auditSpringBoot.service.DashboardService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/dashboard")
public class DashboardController {

    @Autowired
    DashboardService dashboardService;

    // Overview stats: total users, audit counts, website stats
    @GetMapping("/stats")
    public HashMap<String, Object> getOverviewStats(@RequestHeader(value = "Authorization", required = false) String token) {
        return dashboardService.getOverviewStats(token);
    }

    // Per-website breakdown with user counts and action stats
    @GetMapping("/website-breakdown")
    public List<Map<String, Object>> getWebsiteBreakdown(@RequestHeader(value = "Authorization", required = false) String token) {
        return dashboardService.getWebsiteBreakdown(token);
    }

    // Recent audit and activity log entries
    @GetMapping("/recent-activity")
    public HashMap<String, Object> getRecentActivity(@RequestHeader(value = "Authorization", required = false) String token) {
        return dashboardService.getRecentActivity(token);
    }
}
