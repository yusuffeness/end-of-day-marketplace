package tr.edu.etu.endofday.user.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;
import tr.edu.etu.endofday.user.Role;
import tr.edu.etu.endofday.user.User;

/** Reads and writes KULLANICI. */
@Repository
public class UserRepository {

    private static final String SELECT_USER =
            "SELECT kullanici_id, ad, soyad, eposta, telefon, aktif_mi, kayit_zamani FROM KULLANICI";

    private final JdbcClient jdbcClient;

    public UserRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public Optional<User> findById(long id) {
        return jdbcClient
                .sql(SELECT_USER + " WHERE kullanici_id = :id")
                .param("id", id)
                .query(UserRepository::mapUser)
                .optional();
    }

    /** Looks the user up by a normalized email address. */
    public Optional<User> findByEmail(String email) {
        return jdbcClient
                .sql(SELECT_USER + " WHERE eposta = :email")
                .param("email", email)
                .query(UserRepository::mapUser)
                .optional();
    }

    public boolean existsByEmail(String email) {
        return jdbcClient
                .sql("SELECT EXISTS (SELECT 1 FROM KULLANICI WHERE eposta = :email)")
                .param("email", email)
                .query(Boolean.class)
                .single();
    }

    public Optional<UserCredentials> findCredentialsByEmail(String email) {
        return jdbcClient
                .sql("SELECT kullanici_id, parola_ozeti, aktif_mi FROM KULLANICI WHERE eposta = :email")
                .param("email", email)
                .query((rs, rowNum) -> new UserCredentials(
                        rs.getLong("kullanici_id"), rs.getString("parola_ozeti"), rs.getBoolean("aktif_mi")))
                .optional();
    }

    public Optional<String> findPasswordHash(long id) {
        return jdbcClient
                .sql("SELECT parola_ozeti FROM KULLANICI WHERE kullanici_id = :id")
                .param("id", id)
                .query(String.class)
                .optional();
    }

    /**
     * Reads the active flag and the roles of a user in one query.
     *
     * @return empty when the user does not exist
     */
    public Optional<UserAccess> findAccess(long id) {
        List<AccessRow> rows = jdbcClient
                .sql("""
                        SELECT k.aktif_mi, r.rol_adi
                        FROM KULLANICI k
                        LEFT JOIN KULLANICI_ROL kr ON kr.kullanici_id = k.kullanici_id
                        LEFT JOIN ROL r ON r.rol_id = kr.rol_id
                        WHERE k.kullanici_id = :id
                        """)
                .param("id", id)
                .query((rs, rowNum) -> new AccessRow(rs.getBoolean("aktif_mi"), rs.getString("rol_adi")))
                .list();
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        Set<Role> roles = EnumSet.noneOf(Role.class);
        rows.stream()
                .map(AccessRow::roleName)
                .filter(Objects::nonNull)
                .map(Role::valueOf)
                .forEach(roles::add);
        return Optional.of(new UserAccess(id, rows.getFirst().active(), roles));
    }

    /** Inserts an active user and returns the generated kullanici_id. */
    public long insert(NewUser user) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcClient
                .sql("""
                        INSERT INTO KULLANICI (ad, soyad, eposta, telefon, parola_ozeti, aktif_mi, kayit_zamani)
                        VALUES (:firstName, :lastName, :email, :phone, :passwordHash, TRUE, :registeredAt)
                        """)
                .param("firstName", user.firstName())
                .param("lastName", user.lastName())
                .param("email", user.email())
                .param("phone", user.phone())
                .param("passwordHash", user.passwordHash())
                .param("registeredAt", user.registeredAt())
                .update(keyHolder, "kullanici_id");
        return Objects.requireNonNull(keyHolder.getKey(), "generated kullanici_id")
                .longValue();
    }

    /** @return {@code true} when the user exists */
    public boolean updateProfile(long id, String firstName, String lastName, String phone) {
        int updated = jdbcClient
                .sql(
                        "UPDATE KULLANICI SET ad = :firstName, soyad = :lastName, telefon = :phone WHERE kullanici_id = :id")
                .param("firstName", firstName)
                .param("lastName", lastName)
                .param("phone", phone)
                .param("id", id)
                .update();
        return updated == 1;
    }

    /** @return {@code true} when the user exists */
    public boolean updatePasswordHash(long id, String passwordHash) {
        int updated = jdbcClient
                .sql("UPDATE KULLANICI SET parola_ozeti = :passwordHash WHERE kullanici_id = :id")
                .param("passwordHash", passwordHash)
                .param("id", id)
                .update();
        return updated == 1;
    }

    private static User mapUser(ResultSet rs, int rowNum) throws SQLException {
        return new User(
                rs.getLong("kullanici_id"),
                rs.getString("ad"),
                rs.getString("soyad"),
                rs.getString("eposta"),
                rs.getString("telefon"),
                rs.getBoolean("aktif_mi"),
                rs.getObject("kayit_zamani", LocalDateTime.class));
    }

    private record AccessRow(boolean active, String roleName) {}
}
