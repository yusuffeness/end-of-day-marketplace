package tr.edu.etu.endofday.user.repository;

import java.util.Set;
import tr.edu.etu.endofday.user.Role;

/** What decides whether a signed-in user may make a request: the active flag and the current roles. */
public record UserAccess(long id, boolean active, Set<Role> roles) {

    public UserAccess {
        roles = Set.copyOf(roles);
    }
}
