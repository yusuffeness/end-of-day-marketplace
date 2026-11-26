package tr.edu.etu.endofday.user;

/** The roles of the platform (ROL.rol_adi). Constant names equal the database values. */
public enum Role {
    MUSTERI,
    ISLETME_YETKILISI,
    YONETICI;

    private static final String AUTHORITY_PREFIX = "ROLE_";

    /** The Spring Security authority of the role, for example {@code ROLE_MUSTERI}. */
    public String authority() {
        return AUTHORITY_PREFIX + name();
    }
}
