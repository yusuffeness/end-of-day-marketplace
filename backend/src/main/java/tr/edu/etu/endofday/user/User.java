package tr.edu.etu.endofday.user;

import java.time.LocalDateTime;

/**
 * A row of KULLANICI without the password hash, which is read only where a password is checked.
 *
 * <p>{@link #toString()} leaves out the personal fields so that a user never ends up in a log by accident.
 */
public record User(
        long id,
        String firstName,
        String lastName,
        String email,
        String phone,
        boolean active,
        LocalDateTime registeredAt) {

    @Override
    public String toString() {
        return "User[id=" + id + ", active=" + active + "]";
    }
}
