package tr.edu.etu.endofday.user;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The password policy for new passwords: 8 to 72 characters, at most 72 bytes in UTF-8 (BCrypt ignores everything after
 * the 72nd byte), at least one letter and at least one digit. {@code null} is rejected.
 *
 * <p>Each broken rule is reported as its own violation with a Turkish message.
 */
@Documented
@Constraint(validatedBy = PasswordPolicyValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidPassword {

    String message() default "Parola geçersiz.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
