package tr.edu.etu.endofday.auth.service;

import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import tr.edu.etu.endofday.user.EmailAddresses;
import tr.edu.etu.endofday.user.repository.UserCredentials;
import tr.edu.etu.endofday.user.repository.UserRepository;

/**
 * Loads the credentials for a sign-in by email address. Used only by the authentication manager, which is why it is not
 * a bean of its own.
 *
 * <p>The returned user name is the user id, so the email address does not travel further through the authentication.
 * Roles are not loaded here: they are read on every request by the session's security context repository.
 */
public class AccountUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public AccountUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String email) {
        UserCredentials credentials = userRepository
                .findCredentialsByEmail(EmailAddresses.normalize(email))
                .orElseThrow(() -> new UsernameNotFoundException("No account with this email address"));
        return User.withUsername(Long.toString(credentials.id()))
                .password(credentials.passwordHash())
                .disabled(!credentials.active())
                .authorities(new String[0])
                .build();
    }
}
