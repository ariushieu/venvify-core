package com.venvify.venvifycore.wallet.repository;

import com.venvify.venvifycore.wallet.entity.LedgerEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, Long> {

    /** Sao kê sort theo id (R15/F5) — created_at có thể tie trong cùng micro giây; id thì không
     *  (insert tuần tự dưới khóa ví). */
    Page<LedgerEntry> findByWalletIdOrderByIdDesc(Long walletId, Pageable pageable);

    /** Nguồn sự thật của số dư — dùng để reconcile với wallets.balance_cached (D2). */
    @Query("select coalesce(sum(l.amount), 0) from LedgerEntry l where l.wallet.id = :walletId")
    long sumAmountByWalletId(@Param("walletId") Long walletId);
}
