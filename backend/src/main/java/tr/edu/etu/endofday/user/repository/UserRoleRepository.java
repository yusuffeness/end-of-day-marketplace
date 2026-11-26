package tr.edu.etu.endofday.user.repository;

import java.util.EnumSet;
import java.util.Set;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tr.edu.etu.endofday.user.Role;

/** Reads and writes the role assignments in KULLANICI_ROL. */
@Repository
public class UserRoleRepository {

    private final JdbcClient jdbcClient;

    public UserRoleRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public Set<Role> findRoles(long userId) {
        Set<Role> roles = EnumSet.noneOf(Role.class);
        jdbcClient
                .sql("""
                        SELECT r.rol_adi
                        FROM KULLANICI_ROL kr
                        JOIN ROL r ON r.rol_id = kr.rol_id
                        WHERE kr.kullanici_id = :userId
                        """)
                .param("userId", userId)
                .query(String.class)
                .list()
                .forEach(name -> roles.add(Role.valueOf(name)));
        return roles;
    }

    /**
     * Assigns the role to the user. An existing assignment is left unchanged.
     *
     * @return {@code true} when a new assignment was created
     */
    public boolean addRole(long userId, Role role) {
        int inserted = jdbcClient
                .sql("""
                        INSERT INTO KULLANICI_ROL (kullanici_id, rol_id)
                        SELECT :userId, r.rol_id
                        FROM ROL r
                        WHERE r.rol_adi = :roleName
                          AND NOT EXISTS (SELECT 1 FROM KULLANICI_ROL kr
                                          WHERE kr.kullanici_id = :userId AND kr.rol_id = r.rol_id)
                        """)
                .param("userId", userId)
                .param("roleName", role.name())
                .update();
        return inserted == 1;
    }

    /**
     * Revokes the role. This is the only DELETE the application account eod_app may run (V5); all other records are
     * deactivated, never deleted.
     *
     * @return {@code true} when the user held the role
     */
    public boolean removeRole(long userId, Role role) {
        int deleted = jdbcClient
                .sql("""
                        DELETE FROM KULLANICI_ROL
                        WHERE kullanici_id = :userId
                          AND rol_id = (SELECT rol_id FROM ROL WHERE rol_adi = :roleName)
                        """)
                .param("userId", userId)
                .param("roleName", role.name())
                .update();
        return deleted == 1;
    }
}
