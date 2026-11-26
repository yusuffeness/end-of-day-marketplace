package tr.edu.etu.endofday.report.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tr.edu.etu.endofday.business.service.BusinessAccess;
import tr.edu.etu.endofday.common.error.ApiException;
import tr.edu.etu.endofday.report.ReportRange;
import tr.edu.etu.endofday.report.SalesSummary;
import tr.edu.etu.endofday.report.repository.BusinessReportRepository;
import tr.edu.etu.endofday.report.repository.PlatformReportRepository;

/**
 * The business report of an owner (from the base tables, as eod_app) and the platform report of the administrator
 * (from the reporting views, as eod_report).
 */
@Service
public class ReportService {

    private final BusinessAccess businessAccess;
    private final BusinessReportRepository businessReportRepository;
    private final PlatformReportRepository platformReportRepository;

    public ReportService(
            BusinessAccess businessAccess,
            BusinessReportRepository businessReportRepository,
            PlatformReportRepository platformReportRepository) {
        this.businessAccess = businessAccess;
        this.businessReportRepository = businessReportRepository;
        this.platformReportRepository = platformReportRepository;
    }

    /**
     * @throws ApiException {@code EOD-404} for an unknown business, {@code EOD-403} when the user does not own it
     */
    @Transactional(readOnly = true)
    public SalesSummary businessReport(long userId, long businessId, ReportRange range) {
        businessAccess.requireOwned(userId, businessId);
        return businessReportRepository.summarize(businessId, range);
    }

    /** Not transactional: the queries run on the separate eod_report pool, outside the application's transactions. */
    public PlatformReport platformReport(ReportRange range) {
        return new PlatformReport(
                platformReportRepository.summarize(range),
                platformReportRepository.countActiveBusinesses(),
                platformReportRepository.countNewUsers(range));
    }

    /** The platform-wide figures. */
    public record PlatformReport(SalesSummary sales, long activeBusinessCount, long newUserCount) {}
}
