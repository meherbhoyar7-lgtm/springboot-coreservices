package auditSpringBoot.service;

import auditSpringBoot.models.User;
import auditSpringBoot.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class WebsiteService {

    @Autowired
    UserRepository userRepository;

    public Object getAllUsersForWebsite(String websiteName) {
        List<User> users = userRepository.findByWebsiteName(websiteName);

        if (users == null || users.isEmpty()) {
            HashMap<String, Object> resp = new HashMap<>();
            resp.put("websiteName", websiteName);
            resp.put("allUsersEmailList", List.of());
            resp.put("allActiveUsersEmailList", List.of());
            resp.put("deletedUsersEmailList", List.of());
            return resp;
        }

        // All registered users for this website
        List<String> allEmails = users.stream()
                .map(User::getEmail)
                .collect(Collectors.toList());

        // Active users — users where isActive = true
        List<String> activeEmails = users.stream()
                .filter(u -> u.getIsActive() != null && u.getIsActive())
                .map(User::getEmail)
                .collect(Collectors.toList());

        // Deactivated users — users where isActive = false
        List<String> deletedEmails = users.stream()
                .filter(u -> u.getIsActive() != null && !u.getIsActive())
                .map(User::getEmail)
                .collect(Collectors.toList());

        HashMap<String, Object> resp = new HashMap<>();
        resp.put("websiteName", websiteName);
        resp.put("allUsersEmailList", allEmails);
        resp.put("allActiveUsersEmailList", activeEmails);
        resp.put("deletedUsersEmailList", deletedEmails);
        return resp;
    }
}