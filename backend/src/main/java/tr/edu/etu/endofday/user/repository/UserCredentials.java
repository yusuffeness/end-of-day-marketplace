package tr.edu.etu.endofday.user.repository;

/**
 * The sign-in data of a user: id, BCrypt password hash and active flag.
 *
 * <p>{@link #toString()} leaves out the password hash.
 */
public record UserCredentials(long id, String passwordHash, boolean active) {

    @Override
    public String toString() {
        return "UserCredentials[id=" + id + ", active=" + active + "]";
    }
}
