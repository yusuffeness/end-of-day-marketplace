package tr.edu.etu.endofday.admin.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tr.edu.etu.endofday.user.Role;
import tr.edu.etu.endofday.user.User;
import tr.edu.etu.endofday.user.UserProfile;

/** The administrator's view of KULLANICI: account search and the active flag. */
@Repository
public class AdminUserRepository {

    /** Every filter is optional; {@code :pattern} is a LIKE pattern built from the search text. */
    private static final String WHERE = """
            WHERE (:pattern IS NULL OR k.ad LIKE :pattern OR k.soyad LIKE :pattern OR k.eposta LIKE :pattern)
              AND (:role IS NULL OR EXISTS (SELECT 1
                                            FROM KULLANICI_ROL kr
                                            JOIN ROL r ON r.rol_id = kr.rol_id
                                            WHERE kr.kullanici_id = k.kullanici_id AND r.rol_adi = :role))
              AND (:active IS NULL OR k.aktif_mi = :active)
            """;

    private static final String SEARCH = """
            SELECT k.kullanici_id, k.ad, k.soyad, k.eposta, k.telefon, k.aktif_mi, k.kayit_zamani,
                   (SELECT GROUP_CONCAT(r.rol_adi ORDER BY r.rol_adi SEPARATOR ',')
                    FROM KULLANICI_ROL kr
                    JOIN ROL r ON r.rol_id = kr.rol_id
                    WHERE kr.kullanici_id = k.kullanici_id) AS roller
            FROM KULLANICI k
            """ + WHERE + """
            ORDER BY k.kullanici_id
            LIMIT :limit OFFSET :offset
            """;

    private static final String COUNT = "SELECT COUNT(*) FROM KULLANICI k " + WHERE;

    private final JdbcClient jdbcClient;

    public AdminUserRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public List<UserProfile> search(String text, Role role, Boolean active, int limit, long offset) {
        return withFilter(jdbcClient.sql(SEARCH), text, role, active)
                .param("limit", limit)
                .param("offset", offset)
                .query(AdminUserRepository::map)
                .list();
    }

    public long count(String text, Role role, Boolean active) {
        return withFilter(jdbcClient.sql(COUNT), text, role, active)
                .query(Long.class)
                .single();
    }

    public boolean exists(long userId) {
        return jdbcClient
                .sql("SELECT EXISTS (SELECT 1 FROM KULLANICI WHERE kullanici_id = :id)")
                .param("id", userId)
                .query(Boolean.class)
                .single();
    }

    /** @return {@code true} when the user exists */
    public boolean setActive(long userId, boolean active) {
        int updated = jdbcClient
                .sql("UPDATE KULLANICI SET aktif_mi = :active WHERE kullanici_id = :id")
                .param("active", active)
                .param("id", userId)
                .update();
        return updated == 1;
    }

    private static JdbcClient.StatementSpec withFilter(
            JdbcClient.StatementSpec spec, String text, Role role, Boolean active) {
        return spec.param("pattern", text == null ? null : "%" + escapeLike(text) + "%")
                .param("role", role == null ? null : role.name())
                .param("active", active);
    }

    /** Makes % and _ in the search text match themselves. */
    private static String escapeLike(String text) {
        return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private static UserProfile map(ResultSet rs, int rowNum) throws SQLException {
        User user = new User(
                rs.getLong("kullanici_id"),
                rs.getString("ad"),
                rs.getString("soyad"),
                rs.getString("eposta"),
                rs.getString("telefon"),
                rs.getBoolean("aktif_mi"),
                rs.getObject("kayit_zamani", LocalDateTime.class));
        Set<Role> roles = EnumSet.noneOf(Role.class);
        String names = rs.getString("roller");
        if (names != null) {
            Arrays.stream(names.split(",")).map(Role::valueOf).forEach(roles::add);
        }
        return new UserProfile(user, roles);
    }
}
