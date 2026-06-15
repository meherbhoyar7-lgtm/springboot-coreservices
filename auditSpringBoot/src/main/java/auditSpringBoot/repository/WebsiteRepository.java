package auditSpringBoot.repository;

import auditSpringBoot.models.WebsiteModel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface WebsiteRepository extends JpaRepository<WebsiteModel, Long> {

    /**
     * Custom query for specific validation logic.
     */
    @Query("SELECT w FROM WebsiteModel w WHERE w.name = :websiteName")
    WebsiteModel validateWebName(@Param("websiteName") String websiteName);

    /**
     * Standard method for automatic SQL generation.
     * Spring Data JPA automatically maps this to:
     * SELECT * FROM website_model WHERE name = ?
     */
    WebsiteModel findByName(String name);
}