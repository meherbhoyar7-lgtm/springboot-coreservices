package auditSpringBoot.controller;

import auditSpringBoot.service.WebsiteService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
public class WebsiteController {

    @Autowired
    WebsiteService websiteService;

    @GetMapping("/getAllUsersOfWebsite")
    public Object getAllUsersForParticularWebsite(@RequestParam String websiteName){
        return websiteService.getAllUsersForWebsite(websiteName);
    }

    @GetMapping("/checker")
    public Object checker() {
        return java.util.Map.of("status", "UP", "service", "Spring Boot Backend");
    }
}