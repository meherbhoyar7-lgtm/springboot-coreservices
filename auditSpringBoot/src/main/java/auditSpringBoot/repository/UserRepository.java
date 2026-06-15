package auditSpringBoot.repository;

import auditSpringBoot.models.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    // Login validation
    @Query("SELECT u FROM User u WHERE u.email = :email AND u.password = :password")
    User validateCredentials(@Param("email") String email, @Param("password") String password);

    // Website stats for admin dashboard (active users only)
    @Query("SELECT u.websiteName, COUNT(u) FROM User u WHERE u.isActive = true AND u.role = 'USER' GROUP BY u.websiteName")
    List<Object[]> getWebsiteStats();

    // Find user by email
    User findByEmail(String email);

    // Find users by role
    List<User> findByRole(String role);

    // Find active users by role
    List<User> findByRoleAndIsActive(String role, boolean isActive);

    // Find users by website
    List<User> findByWebsiteName(String websiteName);

    // Find active users by website
    List<User> findByWebsiteNameAndIsActive(String websiteName, boolean isActive);

    // Count users per website
    @Query("SELECT COUNT(u) FROM User u WHERE u.websiteName = :websiteName")
    Long countByWebsiteName(@Param("websiteName") String websiteName);

    // Count total active users
    @Query("SELECT COUNT(u) FROM User u WHERE u.role = 'USER' AND u.isActive = true")
    Long countUsers();

    // Count all users (including inactive)
    @Query("SELECT COUNT(u) FROM User u WHERE u.role = 'USER'")
    Long countAllUsers();
}