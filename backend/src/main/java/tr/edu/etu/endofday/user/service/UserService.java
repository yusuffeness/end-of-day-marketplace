package tr.edu.etu.endofday.user.service;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tr.edu.etu.endofday.common.error.ApiException;
import tr.edu.etu.endofday.user.User;
import tr.edu.etu.endofday.user.UserProfile;
import tr.edu.etu.endofday.user.repository.UserRepository;
import tr.edu.etu.endofday.user.repository.UserRoleRepository;

/** The signed-in user's own account: profile and password. */
@Service
public class UserService {

    static final String WRONG_CURRENT_PASSWORD_CODE = "EOD-012";
    static final String WRONG_CURRENT_PASSWORD_DETAIL = "Mevcut parola hatalı.";

    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(
            UserRepository userRepository, UserRoleRepository userRoleRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.userRoleRepository = userRoleRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Reads the user with the roles currently assigned.
     *
     * <p>The signed-in user is reloaded on every request, so a missing user here means the row disappeared during the
     * request; that is answered with {@code EOD-404}.
     */
    @Transactional(readOnly = true)
    public UserProfile getProfile(long userId) {
        User user = userRepository.findById(userId).orElseThrow(ApiException::notFound);
        return new UserProfile(user, userRoleRepository.findRoles(userId));
    }

    /** Replaces the first name, last name and phone number. The values must already be validated and trimmed. */
    @Transactional
    public UserProfile updateProfile(long userId, String firstName, String lastName, String phone) {
        if (!userRepository.updateProfile(userId, firstName, lastName, phone)) {
            throw ApiException.notFound();
        }
        return getProfile(userId);
    }

    /**
     * Replaces the password after checking the current one. The new password must already satisfy the password policy.
     *
     * @throws ApiException {@code EOD-012} when the current password is wrong
     */
    @Transactional
    public void changePassword(long userId, String currentPassword, String newPassword) {
        String currentHash = userRepository.findPasswordHash(userId).orElseThrow(ApiException::notFound);
        if (!passwordEncoder.matches(currentPassword, currentHash)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, WRONG_CURRENT_PASSWORD_CODE, WRONG_CURRENT_PASSWORD_DETAIL);
        }
        userRepository.updatePasswordHash(userId, passwordEncoder.encode(newPassword));
    }
}
