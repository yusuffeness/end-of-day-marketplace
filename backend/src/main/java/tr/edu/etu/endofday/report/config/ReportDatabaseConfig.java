package tr.edu.etu.endofday.report.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Creates the eod_report connection pool from {@code eod.report.datasource.*}; application.yml takes the password from
 * {@code EOD_REPORT_PASSWORD} in the .env file or the environment.
 */
@Configuration(proxyBeanMethods = false)
public class ReportDatabaseConfig {

    @Bean(destroyMethod = "close")
    public ReportDatabase reportDatabase(
            @Value("${eod.report.datasource.url}") String url,
            @Value("${eod.report.datasource.username}") String username,
            @Value("${eod.report.datasource.password}") String password) {
        return new ReportDatabase(url, username, password);
    }
}
