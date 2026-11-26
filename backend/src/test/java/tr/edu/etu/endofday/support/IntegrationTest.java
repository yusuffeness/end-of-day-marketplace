package tr.edu.etu.endofday.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** Base class for tests that run the full application against the shared MySQL test container. */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
public abstract class IntegrationTest {

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MySqlTestContainer::jdbcUrl);
        registry.add("spring.datasource.username", () -> MySqlTestContainer.APP_USER);
        registry.add("spring.datasource.password", () -> MySqlTestContainer.APP_PASSWORD);
        registry.add("spring.flyway.url", MySqlTestContainer::jdbcUrl);
        registry.add("spring.flyway.user", () -> MySqlTestContainer.OWNER_USER);
        registry.add("spring.flyway.password", () -> MySqlTestContainer.OWNER_PASSWORD);
        registry.add("eod.report.datasource.url", MySqlTestContainer::jdbcUrl);
        registry.add("eod.report.datasource.username", () -> MySqlTestContainer.REPORT_USER);
        registry.add("eod.report.datasource.password", () -> MySqlTestContainer.REPORT_PASSWORD);
    }
}
