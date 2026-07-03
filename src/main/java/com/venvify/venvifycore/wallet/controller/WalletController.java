package com.venvify.venvifycore.wallet.controller;

import com.venvify.venvifycore.common.dto.ApiResponse;
import com.venvify.venvifycore.common.dto.PagedResponse;
import com.venvify.venvifycore.wallet.dto.LedgerEntryResponse;
import com.venvify.venvifycore.wallet.dto.TopupRequest;
import com.venvify.venvifycore.wallet.dto.WalletResponse;
import com.venvify.venvifycore.wallet.service.WalletService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/wallets")
@RequiredArgsConstructor
public class WalletController {

    private final WalletService walletService;

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<WalletResponse>> myWallet(
            @AuthenticationPrincipal String userPublicId) {
        return ResponseEntity.ok(ApiResponse.ok(walletService.getMyWallet(userPublicId)));
    }

    @GetMapping("/me/entries")
    public ResponseEntity<ApiResponse<PagedResponse<LedgerEntryResponse>>> myEntries(
            @AuthenticationPrincipal String userPublicId,
            Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(walletService.listMyEntries(userPublicId, pageable)));
    }

    /** Dev-only (R16): flag tắt hoặc profile prod → 404 như thể không tồn tại. */
    @PostMapping("/me/topup")
    public ResponseEntity<ApiResponse<WalletResponse>> topup(
            @AuthenticationPrincipal String userPublicId,
            @Valid @RequestBody TopupRequest request) {
        return ResponseEntity.ok(
                ApiResponse.ok(walletService.devTopup(userPublicId, request.amount()), "Wallet topped up"));
    }
}
