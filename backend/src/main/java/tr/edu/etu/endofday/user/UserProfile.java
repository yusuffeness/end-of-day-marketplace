package tr.edu.etu.endofday.user;

import java.util.Set;

/** A user together with the roles assigned in KULLANICI_ROL. */
public record UserProfile(User user, Set<Role> roles) {

    public UserProfile {
        roles = Set.copyOf(roles);
    }
}
