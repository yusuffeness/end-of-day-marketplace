package tr.edu.etu.endofday.support;

import java.util.Locale;
import java.util.UUID;

/**
 * Accounts that API tests register through the real endpoints. Their requests commit, so every account uses the
 * reserved domain below and {@link #deleteAll()} removes them through the owner connection (the application account
 * cannot delete users).
 */
public final class TestAccounts {

    public static final String DOMAIN = "hesap.test";
    public static final String PASSWORD = "parola123";

    private TestAccounts() {}

    /** A new, lower-case email address on the test domain, for example {@code ali.1a2b3c4d@hesap.test}. */
    public static String uniqueEmail(String localPart) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        return (localPart + "." + suffix + "@" + DOMAIN).toLowerCase(Locale.ROOT);
    }

    public static long idOf(String email) {
        return OwnerJdbc.client()
                .sql("SELECT kullanici_id FROM KULLANICI WHERE eposta = :email")
                .param("email", email)
                .query(Long.class)
                .single();
    }

    /** Deletes every account on the test domain; their role assignments go with them (ON DELETE CASCADE). */
    public static void deleteAll() {
        OwnerJdbc.client()
                .sql("DELETE FROM KULLANICI WHERE eposta LIKE :pattern")
                .param("pattern", "%@" + DOMAIN)
                .update();
    }
}
