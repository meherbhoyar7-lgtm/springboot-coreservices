package auditSpringBoot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.CrossOrigin;

@SpringBootApplication
@CrossOrigin(origins = "*")
public class AuditSpringBootApplication {

	public static void main(String[] args) {
		SpringApplication.run(AuditSpringBootApplication.class, args);
	}

}
