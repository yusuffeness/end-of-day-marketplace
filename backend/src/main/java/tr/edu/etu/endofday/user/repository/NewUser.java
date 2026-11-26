package tr.edu.etu.endofday.user.repository;

import java.time.LocalDateTime;

/**
 * The values of a KULLANICI row to insert. The email address must already be normalized.
 *
 * <p>{@link #toString()} leaves out the personal fields and the password hash.
 */
public record NewUser(
        String firstName,
        String lastName,
        String email,
        String phone,
        String passwordHash,
        LocalDateTime registeredAt) {

    @Override
    public String toString() {
        return "NewUser[registeredAt=" + registeredAt + "]";
    }
}
