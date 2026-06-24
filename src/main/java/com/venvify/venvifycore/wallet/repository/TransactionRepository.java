package com.venvify.venvifycore.wallet.repository;

import com.venvify.venvifycore.wallet.entity.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    Optional<Transaction> findByPublicId(String publicId);

    /** Idempotency: tra theo ref để tránh xử lý callback trùng (SPEC §5.6). */
    Optional<Transaction> findByTransactionRef(String transactionRef);

    boolean existsByTransactionRef(String transactionRef);
}
