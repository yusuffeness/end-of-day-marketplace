package tr.edu.etu.endofday.user;

import java.util.Locale;

/** Normalization of email addresses before they are stored or looked up. */
public final class EmailAddresses {

    private EmailAddresses() {}

    /**
     * Trims the address and lower-cases it with {@link Locale#ROOT}, so that the result does not depend on the
     * default locale (in a Turkish locale, {@code "I"} would become the dotless {@code "ı"}).
     *
     * @return the normalized address, or {@code null} when {@code email} is {@code null}
     */
    public static String normalize(String email) {
        return email == null ? null : email.strip().toLowerCase(Locale.ROOT);
    }
}
