package com.venvify.venvifycore.wallet.repository;

import com.venvify.venvifycore.wallet.entity.LedgerEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, Long> {

    /** Sao kê sort theo id (R15/F5) — created_at có thể tie trong cùng micro giây; id thì không
     *  (insert tuần tự dưới khóa ví). */
    Page<LedgerEntry> findByWalletIdOrderByIdDesc(Long walletId, Pageable pageable);

    /** Nguồn sự thật của số dư — dùng để reconcile với wallets.balance_cached (D2). */
    @Query("select coalesce(sum(l.amount), 0) from LedgerEntry l where l.wallet.id = :walletId")
    long sumAmountByWalletId(@Param("walletId") Long walletId);

    // ---- queries gộp cho ReconciliationJob (money-core §4) — không N+1 ----

    /** Bất biến 1: tổng toàn bộ sổ phải = 0. */
    @Query("select coalesce(sum(l.amount), 0) from LedgerEntry l")
    long sumAll();

    /** Bất biến 2: SUM theo ví, đối chiếu balance_cached từng ví trong 1 query. */
    @Query("select l.wallet.id as walletId, sum(l.amount) as total from LedgerEntry l group by l.wallet.id")
    List<WalletSum> sumGroupByWallet();

    /** Bất biến 3: transaction nào có tổng bút toán ≠ 0 là sổ lệch — trả thẳng danh sách vi phạm. */
    @Query("select l.transaction.id as transactionId, sum(l.amount) as total "
            + "from LedgerEntry l group by l.transaction.id having sum(l.amount) <> 0")
    List<TransactionSum> findUnbalancedTransactions();

    interface WalletSum {
        Long getWalletId();

        Long getTotal();
    }

    interface TransactionSum {
        Long getTransactionId();

        Long getTotal();
    }
}
