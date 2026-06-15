package auditSpringBoot.controller;

import auditSpringBoot.models.User;
import auditSpringBoot.models.UserSignInModel;
import auditSpringBoot.service.UserService;
import auditSpringBoot.utils.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
public class UserController {

    @Autowired
    private UserService userService;

    @Autowired
    private JwtUtil jwtUtil;

    // ===================== PUBLIC ENDPOINTS =====================

    @PostMapping("/registerUser")
    public Object registerUser(@RequestBody User user) {
        try {
            User savedUser = userService.registerUser(user);
            return savedUser;
        } catch (RuntimeException e) {
            return org.springframework.http.ResponseEntity
                    .status(400)
                    .body(java.util.Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/signInUser")
    public Object signinUser(@RequestBody UserSignInModel user) {
        return userService.signinUser(user);
    }

    @PostMapping("/adminSignIn")
    public Object adminSignIn(@RequestBody UserSignInModel user) {
        return userService.adminSignIn(user);
    }

    // ===================== PROTECTED — ADMIN USER MANAGEMENT =====================

    @GetMapping("/api/admin/users")
    public List<User> getAllUsers(@RequestParam(required = false, defaultValue = "false") boolean includeInactive) {
        if (includeInactive) {
            return userService.getAllUsersIncludingInactive();
        }
        return userService.getAllUsers();
    }

    @GetMapping("/api/admin/users/website")
    public List<User> getUsersByWebsite(@RequestParam String websiteName) {
        return userService.getUsersByWebsite(websiteName);
    }

    // Delete a user permanently
    @DeleteMapping("/api/admin/users/{id}")
    public Map<String, Object> deleteUser(@PathVariable Long id) {
        return userService.deleteUser(id);
    }

    // Soft delete — deactivate a user
    @PutMapping("/api/admin/users/{id}/deactivate")
    public Map<String, Object> deactivateUser(@PathVariable Long id) {
        return userService.deactivateUser(id);
    }

    // Reactivate a user
    @PutMapping("/api/admin/users/{id}/activate")
    public Map<String, Object> activateUser(@PathVariable Long id) {
        return userService.activateUser(id);
    }

    // Change user role (USER <-> ADMIN)
    @PutMapping("/api/admin/users/{id}/role")
    public Map<String, Object> changeUserRole(@PathVariable Long id, @RequestBody Map<String, String> body) {
        String newRole = body.get("role");
        return userService.changeUserRole(id, newRole);
    }

    // ===================== PROTECTED — ADMIN PROFILE =====================

    @PutMapping("/api/admin/profile")
    public Map<String, Object> updateProfile(@RequestBody Map<String, String> body,
                                              @RequestHeader("Authorization") String authHeader) {
        String token = authHeader.replace("Bearer ", "");
        String email = jwtUtil.getEmailFromToken(token);
        if (email == null) {
            return Map.of("error", "Invalid token");
        }

        // Handle password change
        if (body.containsKey("oldPassword") && body.containsKey("newPassword")) {
            return userService.changePassword(email, body.get("oldPassword"), body.get("newPassword"));
        }

        // Handle profile update (name)
        return userService.updateProfile(email, body.get("name"));
    }

    // Get token info (remaining time)
    @GetMapping("/api/admin/token-info")
    public Map<String, Object> getTokenInfo(@RequestHeader("Authorization") String authHeader) {
        String token = authHeader.replace("Bearer ", "");
        String email = jwtUtil.getEmailFromToken(token);
        long remaining = jwtUtil.getRemainingSeconds(token);
        return Map.of(
            "email", email != null ? email : "",
            "remainingSeconds", remaining,
            "expiresAt", jwtUtil.getExpirationTime(token)
        );
    }

    // ===================== PROTECTED — USER PROFILE (for regular users) =====================

    @PutMapping("/api/user/profile")
    public Map<String, Object> updateUserProfile(@RequestBody Map<String, String> body,
                                                  @RequestHeader("Authorization") String authHeader) {
        String token = authHeader.replace("Bearer ", "");
        String email = jwtUtil.getEmailFromToken(token);
        if (email == null) {
            return Map.of("error", "Invalid token");
        }

        // Handle password change
        if (body.containsKey("oldPassword") && body.containsKey("newPassword")) {
            return userService.changePassword(email, body.get("oldPassword"), body.get("newPassword"));
        }

        // Handle profile update (name)
        return userService.updateProfile(email, body.get("name"));
    }

    // Get user token info (remaining time)
    @GetMapping("/api/user/token-info")
    public Map<String, Object> getUserTokenInfo(@RequestHeader("Authorization") String authHeader) {
        String token = authHeader.replace("Bearer ", "");
        String email = jwtUtil.getEmailFromToken(token);
        long remaining = jwtUtil.getRemainingSeconds(token);
        return Map.of(
            "email", email != null ? email : "",
            "remainingSeconds", remaining,
            "expiresAt", jwtUtil.getExpirationTime(token)
        );
    }
}