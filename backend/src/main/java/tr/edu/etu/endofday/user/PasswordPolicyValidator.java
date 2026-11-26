package tr.edu.etu.endofday.user;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Checks the rules of {@link ValidPassword}. */
public class PasswordPolicyValidator implements ConstraintValidator<ValidPassword, String> {

    public static final int MIN_LENGTH = 8;
    public static final int MAX_LENGTH = 72;
    public static final int MAX_BYTES = 72;

    static final String REQUIRED = ProfileRules.PASSWORD_REQUIRED;
    static final String LENGTH = "Parola 8 ile 72 karakter arasında olmalıdır.";
    static final String BYTES = "Parola en fazla 72 bayt olabilir; Türkçe karakterler 2 bayt sayılır.";
    static final String LETTER_AND_DIGIT = "Parola en az bir harf ve bir rakam içermelidir.";

    @Override
    public boolean isValid(String password, ConstraintValidatorContext context) {
        List<String> violations = violations(password);
        if (violations.isEmpty()) {
            return true;
        }
        context.disableDefaultConstraintViolation();
        violations.forEach(
                message -> context.buildConstraintViolationWithTemplate(message).addConstraintViolation());
        return false;
    }

    static List<String> violations(String password) {
        if (password == null) {
            return List.of(REQUIRED);
        }
        List<String> violations = new ArrayList<>();
        int length = password.codePointCount(0, password.length());
        if (length < MIN_LENGTH || length > MAX_LENGTH) {
            violations.add(LENGTH);
        } else if (password.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            violations.add(BYTES);
        }
        boolean hasLetter = password.codePoints().anyMatch(Character::isLetter);
        boolean hasDigit = password.codePoints().anyMatch(Character::isDigit);
        if (!hasLetter || !hasDigit) {
            violations.add(LETTER_AND_DIGIT);
        }
        return violations;
    }
}
