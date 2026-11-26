package tr.edu.etu.endofday.report.controller;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tr.edu.etu.endofday.auth.AuthenticatedUser;
import tr.edu.etu.endofday.common.Money;
import tr.edu.etu.endofday.report.ReportRange;
import tr.edu.etu.endofday.report.SalesSummary;
import tr.edu.etu.endofday.report.service.ReportService;

/** The business report (report section 6, "İşletme Raporları") and the platform report of the administrator panel. */
@RestController
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping("/api/v1/businesses/{businessId}/reports")
    @PreAuthorize("hasRole('ISLETME_YETKILISI')")
    public BusinessReportResponse businessReport(
            @PathVariable long businessId,
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        ReportRange range = new ReportRange(from, to);
        SalesSummary sales = reportService.businessReport(user.id(), businessId, range);
        return new BusinessReportResponse(
                businessId,
                from,
                to,
                sales.orderCount(),
                sales.deliveredCount(),
                sales.cancelledCount(),
                sales.notPickedUpCount(),
                sales.unitsSold(),
                Money.format(sales.grossRevenue()),
                Money.format(sales.refundedAmount()),
                Money.format(sales.netRevenue()),
                sales.ratingCount(),
                Money.format(sales.averageRating()),
                distribution(sales));
    }

    @GetMapping("/api/v1/admin/reports/platform")
    @PreAuthorize("hasRole('YONETICI')")
    public PlatformReportResponse platformReport(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        ReportService.PlatformReport report = reportService.platformReport(new ReportRange(from, to));
        SalesSummary sales = report.sales();
        return new PlatformReportResponse(
                from,
                to,
                sales.orderCount(),
                sales.deliveredCount(),
                sales.cancelledCount(),
                sales.notPickedUpCount(),
                sales.unitsSold(),
                Money.format(sales.grossRevenue()),
                Money.format(sales.refundedAmount()),
                Money.format(sales.netRevenue()),
                sales.ratingCount(),
                Money.format(sales.averageRating()),
                distribution(sales),
                report.activeBusinessCount(),
                report.newUserCount());
    }

    /** The number of reviews per rating, keyed "1" to "5". */
    private static Map<String, Long> distribution(SalesSummary sales) {
        Map<String, Long> distribution = new LinkedHashMap<>();
        sales.ratingDistribution().forEach((rating, count) -> distribution.put(rating.toString(), count));
        return distribution;
    }

    /** Body of GET /businesses/{id}/reports. Amounts and the average rating are strings with two decimals. */
    public record BusinessReportResponse(
            long businessId,
            LocalDate from,
            LocalDate to,
            long orderCount,
            long deliveredCount,
            long cancelledCount,
            long notPickedUpCount,
            long unitsSold,
            String grossRevenue,
            String refundedAmount,
            String netRevenue,
            long ratingCount,
            String averageRating,
            Map<String, Long> ratingDistribution) {}

    /** Body of GET /admin/reports/platform: the same figures for the whole platform plus two platform counts. */
    public record PlatformReportResponse(
            LocalDate from,
            LocalDate to,
            long orderCount,
            long deliveredCount,
            long cancelledCount,
            long notPickedUpCount,
            long unitsSold,
            String grossRevenue,
            String refundedAmount,
            String netRevenue,
            long ratingCount,
            String averageRating,
            Map<String, Long> ratingDistribution,
            long activeBusinessCount,
            long newUserCount) {}
}
