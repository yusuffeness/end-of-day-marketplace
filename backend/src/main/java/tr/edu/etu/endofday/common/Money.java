package tr.edu.etu.endofday.common;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Amounts in Turkish lira. They are {@link BigDecimal} values with two decimal places in Java and JSON strings such as
 * {@code "120.00"} in the API, so that no client converts them to binary floating point.
 */
public final class Money {

    public static final int SCALE = 2;

    /** Up to 8 integer digits and up to 2 decimals, matching DECIMAL(10, 2). */
    public static final String PATTERN = "^[0-9]{1,8}(\\.[0-9]{1,2})?$";

    private Money() {}

    /** The amount with exactly two decimals, for example {@code "60.00"}; {@code null} stays {@code null}. */
    public static String format(BigDecimal amount) {
        return amount == null
                ? null
                : amount.setScale(SCALE, RoundingMode.UNNECESSARY).toPlainString();
    }

    /** Parses a value that already matched {@link #PATTERN}; {@code null} stays {@code null}. */
    public static BigDecimal parse(String value) {
        return value == null ? null : new BigDecimal(value).setScale(SCALE, RoundingMode.UNNECESSARY);
    }
}
