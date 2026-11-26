package tr.edu.etu.endofday.user;

/**
 * Validation limits and Turkish messages for the account fields, shared by registration and profile update. The
 * lengths follow the KULLANICI columns.
 */
public final class ProfileRules {

    public static final int NAME_MAX_LENGTH = 50;
    public static final int EMAIL_MAX_LENGTH = 254;

    /** In addition to the standard email check, the domain must contain a dot. */
    public static final String EMAIL_PATTERN = "^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$";

    /** 7 to 20 characters of digits, spaces, plus sign, parentheses and hyphens, for example "0532 410 00 01". */
    public static final String PHONE_PATTERN = "^[0-9+() -]{7,20}$";

    public static final String FIRST_NAME_REQUIRED = "Ad zorunludur.";
    public static final String FIRST_NAME_TOO_LONG = "Ad en fazla 50 karakter olabilir.";
    public static final String LAST_NAME_REQUIRED = "Soyad zorunludur.";
    public static final String LAST_NAME_TOO_LONG = "Soyad en fazla 50 karakter olabilir.";
    public static final String EMAIL_REQUIRED = "E-posta adresi zorunludur.";
    public static final String EMAIL_INVALID = "Geçerli bir e-posta adresi giriniz.";
    public static final String EMAIL_TOO_LONG = "E-posta adresi en fazla 254 karakter olabilir.";
    public static final String PHONE_INVALID = "Geçerli bir telefon numarası giriniz.";
    public static final String PASSWORD_REQUIRED = "Parola zorunludur.";

    private ProfileRules() {}
}
