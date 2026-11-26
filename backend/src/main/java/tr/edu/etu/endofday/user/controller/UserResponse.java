package tr.edu.etu.endofday.user.controller;

import java.util.List;
import tr.edu.etu.endofday.user.Role;
import tr.edu.etu.endofday.user.User;
import tr.edu.etu.endofday.user.UserProfile;

/** The account as returned by GET /me, login and registration. It never contains the password hash. */
public record UserResponse(long id, String firstName, String lastName, String email, String phone, List<String> roles) {

    public static UserResponse from(UserProfile profile) {
        User user = profile.user();
        List<String> roles = profile.roles().stream().map(Role::name).sorted().toList();
        return new UserResponse(user.id(), user.firstName(), user.lastName(), user.email(), user.phone(), roles);
    }

    @Override
    public String toString() {
        return "UserResponse[id=" + id + ", roles=" + roles + "]";
    }
}
