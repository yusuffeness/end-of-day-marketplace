package tr.edu.etu.endofday.auth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.SecurityContextRepository;
import tr.edu.etu.endofday.auth.service.AccountUserDetailsService;
import tr.edu.etu.endofday.auth.session.UserIdSecurityContextRepository;
import tr.edu.etu.endofday.user.repository.UserRepository;

/**
 * Authentication against KULLANICI and the session's security context repository.
 *
 * <p>Publishing an {@link AuthenticationManager} also stops Spring Boot from creating its default in-memory user with a
 * generated password.
 */
@Configuration(proxyBeanMethods = false)
public class AuthConfig {

    @Bean
    public AuthenticationManager authenticationManager(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider =
                new DaoAuthenticationProvider(new AccountUserDetailsService(userRepository));
        provider.setPasswordEncoder(passwordEncoder);
        // By default the account status is checked before the password, which would tell anyone who knows an inactive
        // account's email address that it exists. Checking it afterwards reveals it only to the owner of the password.
        provider.setPreAuthenticationChecks(user -> {});
        provider.setPostAuthenticationChecks(user -> {
            if (!user.isEnabled()) {
                throw new DisabledException("Account is inactive");
            }
        });
        return new ProviderManager(provider);
    }

    @Bean
    public SecurityContextRepository securityContextRepository(UserRepository userRepository) {
        return new UserIdSecurityContextRepository(userRepository);
    }
}
