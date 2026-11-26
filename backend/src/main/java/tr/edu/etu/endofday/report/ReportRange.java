package tr.edu.etu.endofday.report;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import tr.edu.etu.endofday.common.error.ApiException;
import tr.edu.etu.endofday.common.error.RuleErrors;

/**
 * The date range of a report, both days included. An order belongs to the range by its creation time
 * (SIPARIS.olusturulma_zamani), from the start of {@code from} up to, not including, the start of the day after
 * {@code to}.
 */
public record ReportRange(LocalDate from, LocalDate to) {

    public static final int MAX_DAYS = 366;

    /**
     * @throws ApiException {@code EOD-030} when {@code from} is after {@code to} or the range is longer than 366 days
     */
    public ReportRange {
        if (from.isAfter(to) || ChronoUnit.DAYS.between(from, to) + 1 > MAX_DAYS) {
            throw RuleErrors.invalidReportRange();
        }
    }

    public LocalDateTime start() {
        return from.atStartOfDay();
    }

    public LocalDateTime endExclusive() {
        return to.plusDays(1).atStartOfDay();
    }
}
