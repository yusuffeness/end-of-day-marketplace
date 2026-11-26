package tr.edu.etu.endofday.common;

/** Normalization of free text request fields. */
public final class TextInputs {

    private TextInputs() {}

    /** Trims the value; {@code null} stays {@code null}. */
    public static String trim(String value) {
        return value == null ? null : value.strip();
    }

    /** Trims the value and turns an empty result into {@code null}, for optional fields. */
    public static String trimToNull(String value) {
        String trimmed = trim(value);
        return trimmed == null || trimmed.isEmpty() ? null : trimmed;
    }
}
