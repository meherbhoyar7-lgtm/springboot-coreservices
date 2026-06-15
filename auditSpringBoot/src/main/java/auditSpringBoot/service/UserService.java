package auditSpringBoot.service;

import auditSpringBoot.models.User;
import auditSpringBoot.models.UserSignInModel;
import auditSpringBoot.repository.UserRepository;
import auditSpringBoot.utils.JwtUtil;
import org.mindrot.jbcrypt.BCrypt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class UserService {
    @Autowired UserRepository userRepository;
    @Autowired JwtUtil jwtUtil;

    @Value("${nodejs.service.url:http://localhost:3001}")
    private String nodeServiceUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    // ===================== STARTUP: Migrate plaintext passwords to BCrypt =====================

    @EventListener(ApplicationReadyEvent.class)
    public void migratePasswords() {
        List<User> allUsers = userRepository.findAll();
        int migrated = 0;
        for (User user : allUsers) {
            // BCrypt hashes always start with "$2a$" or "$2b$"
            if (user.getPassword() != null && !user.getPassword().startsWith("$2a$") && !user.getPassword().startsWith("$2b$")) {
                user.setPassword(BCrypt.hashpw(user.getPassword(), BCrypt.gensalt()));
                userRepository.save(user);
                migrated++;
            }
            // Also ensure isActive is set for existing users
            if (user.getIsActive() == null) {
                user.setIsActive(true);
                userRepository.save(user);
            }
        }
        if (migrated > 0) {
            System.out.println("✅ Migrated " + migrated + " plaintext passwords to BCrypt.");
        }
    }

    // ===================== REGISTRATION =====================

    public User registerUser(User user) {
        // Validation
        if (user.getEmail() == null || user.getPassword() == null) {
            throw new RuntimeException("Email and Password are required");
        }

        // Check if user already exists
        if (userRepository.findByEmail(user.getEmail()) != null) {
            throw new RuntimeException("User already exists with this email");
        }

        // Set default role
        if (user.getRole() == null || user.getRole().isEmpty()) {
            user.setRole("USER");
        }

        // Hash password with BCrypt
        user.setPassword(BCrypt.hashpw(user.getPassword(), BCrypt.gensalt()));

        // Ensure isActive
        user.setIsActive(true);

        // Save to database
        User savedUser = userRepository.save(user);

        // Create audit log via Node.js Log Service
        sendAuditLog(
            user.getEmail(),
            "USER_REGISTRATION",
            user.getWebsiteName() != null ? user.getWebsiteName() : "SYSTEM",
            "New user registered: " + user.getName(),
            "127.0.0.1",
            "SUCCESS",
            null
        );

        // Create activity log via Node.js Log Service
        sendActivityLog(
            user.getEmail(),
            "REGISTRATION",
            user.getWebsiteName() != null ? user.getWebsiteName() : "SYSTEM",
            "User " + user.getName() + " registered for " + user.getWebsiteName(),
            "user_table",
            null,
            null,
            "LOW"
        );

        return savedUser;
    }

    // ===================== USER SIGN IN =====================

    public Object signinUser(UserSignInModel user) {
        User dbUser = userRepository.findByEmail(user.getEmail());

        if (dbUser == null) {
            logLoginAttempt(user.getEmail(), "UNKNOWN", false, "User not found");
            Map<String, Object> err = new HashMap<>();
            err.put("error", "User not found");
            return err;
        }

        // Check if user is active
        if (dbUser.getIsActive() != null && !dbUser.getIsActive()) {
            logLoginAttempt(user.getEmail(), dbUser.getWebsiteName(), false, "Account deactivated");
            Map<String, Object> err = new HashMap<>();
            err.put("error", "Account has been deactivated. Contact admin.");
            return err;
        }

        // BCrypt password comparison
        if (!BCrypt.checkpw(user.getPassword(), dbUser.getPassword())) {
            logLoginAttempt(user.getEmail(), dbUser.getWebsiteName(), false, "Wrong password");
            Map<String, Object> err = new HashMap<>();
            err.put("error", "Wrong password");
            return err;
        }

        // Successful login — generate token
        String token = jwtUtil.generateToken(dbUser.getEmail(), dbUser.getRole());
        logLoginAttempt(user.getEmail(), dbUser.getWebsiteName(), true, null);

        Map<String, Object> resp = new HashMap<>();
        resp.put("token", token);
        resp.put("status", "authenticated");
        resp.put("role", dbUser.getRole());
        resp.put("name", dbUser.getName());
        resp.put("email", dbUser.getEmail());
        resp.put("websiteName", dbUser.getWebsiteName());
        resp.put("expiresIn", jwtUtil.getRemainingSeconds(token));
        return resp;
    }

    // ===================== ADMIN SIGN IN =====================

    public Object adminSignIn(UserSignInModel user) {
        User dbUser = userRepository.findByEmail(user.getEmail());

        if (dbUser == null) {
            logLoginAttempt(user.getEmail(), "ADMIN_PANEL", false, "Admin not found");
            Map<String, Object> err = new HashMap<>();
            err.put("error", "Admin not found");
            return err;
        }

        // BCrypt password comparison
        if (!BCrypt.checkpw(user.getPassword(), dbUser.getPassword())) {
            logLoginAttempt(user.getEmail(), "ADMIN_PANEL", false, "Wrong password");
            Map<String, Object> err = new HashMap<>();
            err.put("error", "Wrong password");
            return err;
        }

        if (!"ADMIN".equals(dbUser.getRole())) {
            logLoginAttempt(user.getEmail(), "ADMIN_PANEL", false, "Not an admin account");
            Map<String, Object> err = new HashMap<>();
            err.put("error", "Access denied. Not an admin account.");
            return err;
        }

        // Successful admin login
        String token = jwtUtil.generateToken(dbUser.getEmail(), dbUser.getRole());
        logLoginAttempt(user.getEmail(), "ADMIN_PANEL", true, null);

        Map<String, Object> resp = new HashMap<>();
        resp.put("token", token);
        resp.put("status", "authenticated");
        resp.put("role", "ADMIN");
        resp.put("name", dbUser.getName());
        resp.put("email", dbUser.getEmail());
        resp.put("expiresIn", jwtUtil.getRemainingSeconds(token));
        return resp;
    }

    // ===================== ADMIN USER MANAGEMENT =====================

    public List<User> getAllUsers() {
        return userRepository.findByRoleAndIsActive("USER", true);
    }

    public List<User> getAllUsersIncludingInactive() {
        return userRepository.findByRole("USER");
    }

    public List<User> getUsersByWebsite(String websiteName) {
        return userRepository.findByWebsiteName(websiteName);
    }

    public Map<String, Object> deactivateUser(Long userId) {
        Optional<User> optUser = userRepository.findById(userId);
        if (optUser.isEmpty()) {
            return Map.of("error", "User not found");
        }
        User user = optUser.get();
        user.setIsActive(false);
        userRepository.save(user);

        sendAuditLog(user.getEmail(), "USER_DEACTIVATED", "ADMIN_PANEL",
            "User deactivated by admin", "127.0.0.1", "SUCCESS", null);

        return Map.of("message", "User deactivated successfully", "email", user.getEmail());
    }

    public Map<String, Object> activateUser(Long userId) {
        Optional<User> optUser = userRepository.findById(userId);
        if (optUser.isEmpty()) {
            return Map.of("error", "User not found");
        }
        User user = optUser.get();
        user.setIsActive(true);
        userRepository.save(user);

        sendAuditLog(user.getEmail(), "USER_ACTIVATED", "ADMIN_PANEL",
            "User reactivated by admin", "127.0.0.1", "SUCCESS", null);

        return Map.of("message", "User activated successfully", "email", user.getEmail());
    }

    public Map<String, Object> deleteUser(Long userId) {
        Optional<User> optUser = userRepository.findById(userId);
        if (optUser.isEmpty()) {
            return Map.of("error", "User not found");
        }
        User user = optUser.get();
        String email = user.getEmail();
        userRepository.deleteById(userId);

        sendAuditLog(email, "USER_DELETED", "ADMIN_PANEL",
            "User permanently deleted by admin", "127.0.0.1", "SUCCESS", null);

        return Map.of("message", "User deleted permanently", "email", email);
    }

    public Map<String, Object> changeUserRole(Long userId, String newRole) {
        if (!"USER".equals(newRole) && !"ADMIN".equals(newRole)) {
            return Map.of("error", "Invalid role. Must be USER or ADMIN.");
        }
        Optional<User> optUser = userRepository.findById(userId);
        if (optUser.isEmpty()) {
            return Map.of("error", "User not found");
        }
        User user = optUser.get();
        String oldRole = user.getRole();
        user.setRole(newRole);
        userRepository.save(user);

        sendAuditLog(user.getEmail(), "ROLE_CHANGED", "ADMIN_PANEL",
            "Role changed from " + oldRole + " to " + newRole, "127.0.0.1", "SUCCESS", null);

        return Map.of("message", "Role updated to " + newRole, "email", user.getEmail());
    }

    // ===================== ADMIN PROFILE =====================

    public Map<String, Object> changePassword(String email, String oldPassword, String newPassword) {
        User user = userRepository.findByEmail(email);
        if (user == null) {
            return Map.of("error", "User not found");
        }
        if (!BCrypt.checkpw(oldPassword, user.getPassword())) {
            return Map.of("error", "Current password is incorrect");
        }
        user.setPassword(BCrypt.hashpw(newPassword, BCrypt.gensalt()));
        userRepository.save(user);

        sendAuditLog(email, "PASSWORD_CHANGED", "ADMIN_PANEL",
            "Admin changed their password", "127.0.0.1", "SUCCESS", null);

        return Map.of("message", "Password changed successfully");
    }

    public Map<String, Object> updateProfile(String email, String name) {
        User user = userRepository.findByEmail(email);
        if (user == null) {
            return Map.of("error", "User not found");
        }
        if (name != null && !name.isEmpty()) {
            user.setName(name);
        }
        userRepository.save(user);
        return Map.of("message", "Profile updated", "name", user.getName());
    }

    // ===================== INTERNAL HELPERS =====================

    private void logLoginAttempt(String email, String websiteName, boolean success, String errorMsg) {
        sendAuditLog(
            email,
            "LOGIN_ATTEMPT",
            websiteName != null ? websiteName : "SYSTEM",
            success ? "Successful login" : "Failed login attempt",
            "127.0.0.1",
            success ? "SUCCESS" : "FAILED",
            errorMsg
        );

        sendActivityLog(
            email,
            "LOGIN",
            websiteName != null ? websiteName : "SYSTEM",
            success ? "User logged in successfully" : "Login failed: " + errorMsg,
            "auth_system",
            null,
            null,
            success ? "LOW" : "HIGH"
        );
    }

    // ===================== HTTP calls to Node.js Log Service =====================

    private void sendAuditLog(String userEmail, String action, String websiteName,
                              String details, String ipAddress, String status, String errorMessage) {
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("userEmail", userEmail);
            body.put("action", action);
            body.put("websiteName", websiteName);
            body.put("details", details);
            body.put("ipAddress", ipAddress);
            body.put("status", status);
            body.put("errorMessage", errorMessage);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

            restTemplate.postForObject(nodeServiceUrl + "/api/audit/logs", request, String.class);
        } catch (Exception e) {
            System.err.println("Failed to send audit log to Node.js service: " + e.getMessage());
        }
    }

    private void sendActivityLog(String userEmail, String activityType, String websiteName,
                                 String description, String resourceName, Long durationInSeconds,
                                 String sessionId, String severity) {
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("userEmail", userEmail);
            body.put("activityType", activityType);
            body.put("websiteName", websiteName);
            body.put("description", description);
            body.put("resourceName", resourceName);
            body.put("durationInSeconds", durationInSeconds);
            body.put("sessionId", sessionId);
            body.put("severity", severity);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

            restTemplate.postForObject(nodeServiceUrl + "/api/activity/logs", request, String.class);
        } catch (Exception e) {
            System.err.println("Failed to send activity log to Node.js service: " + e.getMessage());
        }
    }
}