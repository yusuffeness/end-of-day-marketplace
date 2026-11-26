package tr.edu.etu.endofday.auth.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tr.edu.etu.endofday.common.error.ApiException;
import tr.edu.etu.endofday.user.EmailAddresses;
import tr.edu.etu.endofday.user.Role;
import tr.edu.etu.endofday.user.User;
import tr.edu.etu.endofday.user.UserProfile;
import tr.edu.etu.endofday.user.repository.NewUser;
import tr.edu.etu.endofday.user.repository.UserRepository;
import tr.edu.etu.endofday.user.repository.UserRoleRepository;

/** Creates customer accounts. */
@Service
public class RegistrationService {

    static final String EMAIL_TAKEN_CODE = "EOD-009";
    static final String EMAIL_TAKEN_DETAIL = "Bu e-posta adresiyle kayıtlı bir hesap var.";

    private static final Logger log = LoggerFactory.getLogger(RegistrationService.class);

    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    public RegistrationService(
            UserRepository userRepository,
            UserRoleRepository userRoleRepository,
            PasswordEncoder passwordEncoder,
            Clock clock) {
        this.userRepository = userRepository;
        this.userRoleRepository = userRoleRepository;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    /**
     * Creates an active user with the role MUSTERI in one transaction. The values must already be validated and trimmed;
     * the email address is normalized here.
     *
     * @throws ApiException {@code EOD-009} when an account with the email address exists
     */
    @Transactional
    public UserProfile register(String firstName, String lastName, String email, String phone, String password) {
        String normalizedEmail = EmailAddresses.normalize(email);
        if (userRepository.existsByEmail(normalizedEmail)) {
            throw emailTaken();
        }
        // KULLANICI.kayit_zamani is a DATETIME without fractions, which MySQL would round rather than truncate.
        LocalDateTime registeredAt = LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
        NewUser newUser = new NewUser(
                firstName, lastName, normalizedEmail, phone, passwordEncoder.encode(password), registeredAt);
        long userId;
        try {
            userId = userRepository.insert(newUser);
        } catch (DuplicateKeyException ex) {
            // A concurrent registration with the same address won the race against the check above.
            throw emailTaken();
        }
        if (!userRoleRepository.addRole(userId, Role.MUSTERI)) {
            throw new IllegalStateException("Role " + Role.MUSTERI + " is missing from ROL");
        }
        log.info("Registered user {}", userId);
        User user = userRepository.findById(userId).orElseThrow();
        return new UserProfile(user, Set.of(Role.MUSTERI));
    }

    private static ApiException emailTaken() {
        return new ApiException(HttpStatus.CONFLICT, EMAIL_TAKEN_CODE, EMAIL_TAKEN_DETAIL);
    }
}
