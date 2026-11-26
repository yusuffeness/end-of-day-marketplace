package tr.edu.etu.endofday.review.repository;

import java.time.LocalDateTime;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Writes DEGERLENDIRME. */
@Repository
public class ReviewRepository {

    private final JdbcClient jdbcClient;

    public ReviewRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public boolean existsForOrder(long orderId) {
        return jdbcClient
                .sql("SELECT EXISTS (SELECT 1 FROM DEGERLENDIRME WHERE siparis_id = :orderId)")
                .param("orderId", orderId)
                .query(Boolean.class)
                .single();
    }

    /** Inserts the review; the primary key on siparis_id allows one review per order. */
    public void insert(long orderId, int rating, String comment, LocalDateTime createdAt) {
        jdbcClient
                .sql("""
                        INSERT INTO DEGERLENDIRME (siparis_id, puan, yorum, olusturulma_zamani)
                        VALUES (:orderId, :rating, :comment, :createdAt)
                        """)
                .param("orderId", orderId)
                .param("rating", rating)
                .param("comment", comment)
                .param("createdAt", createdAt)
                .update();
    }
}
