package tr.edu.etu.endofday.common;

import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** Programmatic transactions for services that must act after a commit or a rollback. */
public final class Transactions {

    private Transactions() {}

    /**
     * A template for READ COMMITTED transactions. The order, cancellation and pickup transactions lock the rows they
     * change first; under READ COMMITTED every later plain read then sees the latest committed data (for example a
     * payment that a concurrent request with the same request id has just committed), and locking reads take no gap
     * locks that could make unrelated inserts wait for each other.
     */
    public static TransactionTemplate readCommitted(PlatformTransactionManager transactionManager) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        return template;
    }
}
