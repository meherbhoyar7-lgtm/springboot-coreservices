package auditSpringBoot.models;


import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UserSignInModel {
    String email;
    String password;
}
