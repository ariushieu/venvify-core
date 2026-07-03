package com.venvify.venvifycore.wallet.mapper;

import com.venvify.venvifycore.wallet.dto.LedgerEntryResponse;
import com.venvify.venvifycore.wallet.dto.WalletResponse;
import com.venvify.venvifycore.wallet.entity.LedgerEntry;
import com.venvify.venvifycore.wallet.entity.Wallet;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface WalletMapper {

    @Mapping(target = "balance", source = "balanceCached")
    WalletResponse toResponse(Wallet wallet);

    @Mapping(target = "transactionType", source = "transaction.type")
    @Mapping(target = "transactionRef", source = "transaction.transactionRef")
    LedgerEntryResponse toEntryResponse(LedgerEntry entry);
}
