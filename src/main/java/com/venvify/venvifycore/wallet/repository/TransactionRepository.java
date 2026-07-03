package com.venvify.venvifycore.wallet.repository;

import com.venvify.venvifycore.wallet.entity.Transaction;
import com.venvify.venvifycore.wallet.enums.TransactionStatus;
import com.venvify.venvifycore.wallet.enums.TransactionType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.Optional;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    Optional<Transaction> findByPublicId(String publicId);

    /** Idempotency: tra theo ref để tránh xử lý callback trùng (SPEC §5.6). */
    Optional<Transaction> findByTransactionRef(String transactionRef);

    boolean existsByTransactionRef(String transactionRef);

    // ---- admin (P6 §4 — đọc-only phục vụ CSKH/đối soát) ----

    /** Mọi filter đều tuỳ chọn (null = bỏ); from/to lọc trên created_at. */
    @Query(value = """
            select t from Transaction t join fetch t.user left join fetch t.event
            where (:ref is null or t.transactionRef = :ref)
              and (:type is null or t.type = :type)
              and (:userPublicId is null or t.user.publicId = :userPublicId)
              and (:fromTime is null or t.createdAt >= :fromTime)
              and (:toTime is null or t.createdAt <= :toTime)
            order by t.id desc""",
            countQuery = """
            select count(t) from Transaction t
            where (:ref is null or t.transactionRef = :ref)
              and (:type is null or t.type = :type)
              and (:userPublicId is null or t.user.publicId = :userPublicId)
              and (:fromTime is null or t.createdAt >= :fromTime)
              and (:toTime is null or t.createdAt <= :toTime)""")
    Page<Transaction> adminSearch(@Param("ref") String ref,
                                  @Param("type") TransactionType type,
                                  @Param("userPublicId") String userPublicId,
                                  @Param("fromTime") Instant fromTime,
                                  @Param("toTime") Instant toTime,
                                  Pageable pageable);

    /** KPI GMV = tổng tiền vé bán ra (mua mới + resale) đã SUCCESS. */
    @Query("select coalesce(sum(t.amount), 0) from Transaction t where t.type in :types and t.status = :status")
    long sumAmountByTypeInAndStatus(@Param("types") Collection<TransactionType> types,
                                    @Param("status") TransactionStatus status);
}
