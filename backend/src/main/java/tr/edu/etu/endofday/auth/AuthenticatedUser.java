package tr.edu.etu.endofday.auth;

import java.util.List;
import java.util.Set;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import tr.edu.etu.endofday.user.Role;

/**
 * The principal of a signed-in request: the user id and the roles read from the database for this request.
 *
 * <p>Controllers receive it with {@code @AuthenticationPrincipal}. It carries no personal data and no credentials.
 */
public record AuthenticatedUser(long id, Set<Role> roles) {

    public AuthenticatedUser {
        roles = Set.copyOf(roles);
    }

    /** An authenticated token with this user as principal and one {@code ROLE_*} authority per role. */
    public Authentication toAuthentication() {
        List<SimpleGrantedAuthority> authorities = roles.stream()
                .map(Role::authority)
                .sorted()
                .map(SimpleGrantedAuthority::new)
                .toList();
        return UsernamePasswordAuthenticationToken.authenticated(this, null, authorities);
    }
}
