package auditSpringBoot.service;

import auditSpringBoot.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.core.ParameterizedTypeReference;

@Service
public class DashboardService {

    @Autowired
    private UserRepository userRepository;

    @Value("${nodejs.service.url:http://localhost:3001}")
    private String nodeServiceUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * Returns comprehensive overview stats for the admin dashboard:
     * - totalUsers from PostgreSQL
     * - audit & activity log stats from Node.js Log Service
     * - websiteStats (per-website user count) from PostgreSQL
     */
    public HashMap<String, Object> getOverviewStats(String token) {
        HashMap<String, Object> stats = new HashMap<>();

        // User stats from PostgreSQL
        stats.put("totalUsers", userRepository.countUsers());

        // Per-website user breakdown from PostgreSQL
        List<Object[]> websiteData = userRepository.getWebsiteStats();
        List<Map<String, Object>> websiteStats = new ArrayList<>();
        for (Object[] row : websiteData) {
            Map<String, Object> entry = new HashMap<>();
            String name = (String) row[0];
            entry.put("websiteName", name != null ? name : "Unassigned");
            entry.put("userCount", row[1]);
            websiteStats.add(entry);
        }
        stats.put("websiteStats", websiteStats);

        // Audit log stats from Node.js
        try {
            HttpHeaders headers = new HttpHeaders();
            if (token != null) headers.set("Authorization", token);
            HttpEntity<String> entity = new HttpEntity<>(headers);
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                nodeServiceUrl + "/api/audit/logs/stats", HttpMethod.GET, entity,
                new ParameterizedTypeReference<Map<String, Object>>() {}
            );
            Map<String, Object> auditStats = response.getBody();
            if (auditStats != null) {
                stats.put("totalAuditLogs", auditStats.getOrDefault("totalAuditLogs", 0));
                stats.put("failedLogins", auditStats.getOrDefault("failedCount", 0));
                stats.put("successfulLogins", auditStats.getOrDefault("successCount", 0));
            }
        } catch (Exception e) {
            System.err.println("Failed to fetch audit stats from Node.js: " + e.getMessage());
            stats.put("totalAuditLogs", 0);
            stats.put("failedLogins", 0);
            stats.put("successfulLogins", 0);
        }

        // Activity log stats from Node.js
        try {
            HttpHeaders headers = new HttpHeaders();
            if (token != null) headers.set("Authorization", token);
            HttpEntity<String> entity = new HttpEntity<>(headers);
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                nodeServiceUrl + "/api/activity/logs/stats", HttpMethod.GET, entity,
                new ParameterizedTypeReference<Map<String, Object>>() {}
            );
            Map<String, Object> activityStats = response.getBody();
            if (activityStats != null) {
                stats.put("totalActivityLogs", activityStats.getOrDefault("totalActivityLogs", 0));
                stats.put("loginAttempts", activityStats.getOrDefault("loginCount", 0));
                stats.put("registrations", activityStats.getOrDefault("registrationCount", 0));
            }
        } catch (Exception e) {
            System.err.println("Failed to fetch activity stats from Node.js: " + e.getMessage());
            stats.put("totalActivityLogs", 0);
            stats.put("loginAttempts", 0);
            stats.put("registrations", 0);
        }

        return stats;
    }

    /**
     * Returns detailed per-website breakdown with user counts.
     * Audit log data is fetched from Node.js for each website.
     */
    public List<Map<String, Object>> getWebsiteBreakdown(String token) {
        List<Object[]> websiteData = userRepository.getWebsiteStats();
        List<Map<String, Object>> breakdown = new ArrayList<>();

        for (Object[] row : websiteData) {
            Map<String, Object> entry = new HashMap<>();
            String websiteName = (String) row[0];
            if (websiteName == null) websiteName = "Unassigned";
            entry.put("websiteName", websiteName);
            entry.put("userCount", row[1]);

            // Fetch audit log counts per website from Node.js
            try {
                HttpHeaders headers = new HttpHeaders();
                if (token != null) headers.set("Authorization", token);
                HttpEntity<String> entity = new HttpEntity<>(headers);
                ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    nodeServiceUrl + "/api/audit/logs/website?websiteName=" + websiteName, HttpMethod.GET, entity,
                    new ParameterizedTypeReference<Map<String, Object>>() {}
                );
                Map<String, Object> auditData = response.getBody();
                if (auditData != null) {
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> logs = (List<Map<String, Object>>) auditData.get("logs");
                    if (logs != null) {
                        long successCount = logs.stream()
                            .filter(l -> "SUCCESS".equals(l.get("status")))
                            .count();
                        long failedCount = logs.stream()
                            .filter(l -> "FAILED".equals(l.get("status")))
                            .count();
                        entry.put("successfulActions", successCount);
                        entry.put("failedActions", failedCount);
                        entry.put("totalActions", logs.size());
                    } else {
                        entry.put("successfulActions", 0L);
                        entry.put("failedActions", 0L);
                        entry.put("totalActions", 0);
                    }
                }
            } catch (Exception e) {
                System.err.println("Failed to fetch audit data for " + websiteName + ": " + e.getMessage());
                entry.put("successfulActions", 0L);
                entry.put("failedActions", 0L);
                entry.put("totalActions", 0);
            }

            breakdown.add(entry);
        }

        return breakdown;
    }

    /**
     * Returns recent combined activity from Node.js Log Service
     */
    public HashMap<String, Object> getRecentActivity(String token) {
        HashMap<String, Object> recent = new HashMap<>();

        // Recent audit logs from Node.js
        try {
            HttpHeaders headers = new HttpHeaders();
            if (token != null) headers.set("Authorization", token);
            HttpEntity<String> entity = new HttpEntity<>(headers);
            ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(
                nodeServiceUrl + "/api/audit/logs/recent", HttpMethod.GET, entity,
                new ParameterizedTypeReference<List<Map<String, Object>>>() {}
            );
            List<Map<String, Object>> recentAudits = response.getBody();
            recent.put("recentAuditLogs", recentAudits != null ? recentAudits : new ArrayList<>());
        } catch (Exception e) {
            System.err.println("Failed to fetch recent audits from Node.js: " + e.getMessage());
            recent.put("recentAuditLogs", new ArrayList<>());
        }

        // Recent activity logs from Node.js
        try {
            HttpHeaders headers = new HttpHeaders();
            if (token != null) headers.set("Authorization", token);
            HttpEntity<String> entity = new HttpEntity<>(headers);
            ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(
                nodeServiceUrl + "/api/activity/logs/recent", HttpMethod.GET, entity,
                new ParameterizedTypeReference<List<Map<String, Object>>>() {}
            );
            List<Map<String, Object>> recentActivity = response.getBody();
            recent.put("recentActivityLogs", recentActivity != null ? recentActivity : new ArrayList<>());
        } catch (Exception e) {
            System.err.println("Failed to fetch recent activity from Node.js: " + e.getMessage());
            recent.put("recentActivityLogs", new ArrayList<>());
        }

        return recent;
    }
}
