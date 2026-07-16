package com.bolao.copa.arena.service;

import static com.bolao.copa.arena.api.ArenaDtos.*;

import com.bolao.copa.arena.domain.*;
import com.bolao.copa.arena.domain.ArenaEnums.PointTransactionType;
import com.bolao.copa.arena.repository.*;
import com.bolao.copa.entity.User;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PointWalletService {
    public static final int INITIAL_DEMO_POINTS = 5_000;
    private final PointWalletRepository wallets;
    private final PointLedgerRepository ledger;

    public PointWalletService(PointWalletRepository wallets, PointLedgerRepository ledger) {
        this.wallets = wallets;
        this.ledger = ledger;
    }

    @Transactional
    public WalletResponse wallet(User user) { return response(ensureWallet(user)); }

    @Transactional
    public PointWallet ensureWallet(User user) {
        return wallets.findByUser(user).orElseGet(() -> {
            PointWallet wallet = new PointWallet();
            wallet.setUser(user);
            wallet.setBalance(INITIAL_DEMO_POINTS);
            wallet.setLifetimeEarned(INITIAL_DEMO_POINTS);
            wallet = wallets.saveAndFlush(wallet);
            PointLedgerEntry initial = new PointLedgerEntry();
            initial.setWallet(wallet);
            initial.setType(PointTransactionType.INITIAL_BONUS);
            initial.setAmount(INITIAL_DEMO_POINTS);
            initial.setBalanceAfter(INITIAL_DEMO_POINTS);
            initial.setIdempotencyKey("initial-bonus:user:" + user.getId());
            initial.setReferenceType("USER");
            initial.setReferenceId(user.getId().toString());
            initial.setDescription("Bônus inicial de demonstração");
            ledger.save(initial);
            return wallet;
        });
    }

    @Transactional(readOnly = true)
    public List<PointTransactionResponse> transactions(User user) {
        PointWallet wallet = wallets.findByUser(user).orElse(null);
        if (wallet == null) return List.of();
        return ledger.findTop100ByWalletOrderByCreatedAtDesc(wallet).stream().map(this::transactionResponse).toList();
    }

    @Transactional
    public PointLedgerEntry apply(User user, long amount, PointTransactionType type, String idempotencyKey,
                                  String referenceType, String referenceId, String description) {
        PointLedgerEntry existing = ledger.findByIdempotencyKey(idempotencyKey).orElse(null);
        if (existing != null) return existing;
        ensureWallet(user);
        PointWallet wallet = wallets.findByUserForUpdate(user)
                .orElseThrow(() -> new IllegalStateException("Carteira de pontos não encontrada."));
        long next = wallet.getBalance() + amount;
        if (next < 0) throw new ArenaProblem.RuleViolation("Saldo de pontos insuficiente.");
        wallet.setBalance(next);
        if (amount > 0) wallet.setLifetimeEarned(wallet.getLifetimeEarned() + amount);
        if (amount < 0) wallet.setLifetimeUsed(wallet.getLifetimeUsed() + Math.abs(amount));
        wallet.touch();
        PointLedgerEntry entry = new PointLedgerEntry();
        entry.setWallet(wallet);
        entry.setType(type);
        entry.setAmount(amount);
        entry.setBalanceAfter(next);
        entry.setIdempotencyKey(idempotencyKey);
        entry.setReferenceType(referenceType);
        entry.setReferenceId(referenceId);
        entry.setDescription(description);
        return ledger.save(entry);
    }

    private WalletResponse response(PointWallet value) {
        return new WalletResponse(value.getBalance(), value.getLifetimeEarned(), value.getLifetimeUsed(),
                value.getUpdatedAt(), VIRTUAL_POINTS_NOTICE);
    }
    private PointTransactionResponse transactionResponse(PointLedgerEntry value) {
        return new PointTransactionResponse(value.getId(), value.getType(), value.getAmount(), value.getBalanceAfter(),
                value.getDescription(), value.getReferenceType(), value.getReferenceId(), value.getCreatedAt());
    }
}
