package com.bolao.copa.arena.service;

import static com.bolao.copa.arena.api.ArenaDtos.*;

import com.bolao.copa.arena.domain.*;
import com.bolao.copa.arena.domain.ArenaEnums.PointTransactionType;
import com.bolao.copa.arena.repository.*;
import com.bolao.copa.entity.User;
import com.bolao.copa.repository.UserRepository;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PointWalletService {
    public static final int INITIAL_DEMO_POINTS = 5_000;
    private final PointWalletRepository wallets;
    private final PointLedgerRepository ledger;
    private final UserRepository users;

    public PointWalletService(PointWalletRepository wallets, PointLedgerRepository ledger,
                              UserRepository users) {
        this.wallets = wallets;
        this.ledger = ledger;
        this.users = users;
    }

    @Transactional
    public WalletResponse wallet(User user) { return response(ensureWallet(user)); }

    @Transactional
    public PointWallet ensureWallet(User user) {
        PointWallet existing = wallets.findByUser(user).orElse(null);
        if (existing != null) return existing;
        User lockedUser = users.findByIdForUpdate(user.getId())
                .orElseThrow(() -> new ArenaProblem.NotFound("Usuário não encontrado."));
        return wallets.findByUser(lockedUser).orElseGet(() -> {
            PointWallet wallet = new PointWallet();
            wallet.setUser(lockedUser);
            wallet.setBalance(INITIAL_DEMO_POINTS);
            wallet.setLifetimeEarned(0);
            wallet = wallets.saveAndFlush(wallet);
            PointLedgerEntry initial = new PointLedgerEntry();
            initial.setWallet(wallet);
            initial.setType(PointTransactionType.INITIAL_BONUS);
            initial.setAmount(INITIAL_DEMO_POINTS);
            initial.setBalanceAfter(INITIAL_DEMO_POINTS);
            initial.setIdempotencyKey("initial-bonus:user:" + lockedUser.getId());
            initial.setReferenceType("USER");
            initial.setReferenceId(lockedUser.getId().toString());
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

    /**
     * Uses the wallet as the canonical per-participant command mutex. Keeping
     * every points/progression path on the same lock order avoids the classic
     * user-row versus wallet-row deadlock under concurrent commands.
     */
    @Transactional
    public void lockParticipant(User user) {
        walletForUpdate(user);
    }

    @Transactional
    public PointLedgerEntry apply(User user, long amount, PointTransactionType type, String idempotencyKey,
                                  String referenceType, String referenceId, String description) {
        PointLedgerEntry existing = ledger.findByIdempotencyKey(idempotencyKey).orElse(null);
        if (existing != null) return compatible(existing, user, amount, type, referenceType, referenceId);
        PointWallet wallet = walletForUpdate(user);
        // The wallet lock serializes every points mutation for this user. Re-read
        // the key after acquiring it so concurrent retries return the committed
        // entry instead of reaching the unique constraint.
        existing = ledger.findByIdempotencyKey(idempotencyKey).orElse(null);
        if (existing != null) return compatible(existing, user, amount, type, referenceType, referenceId);
        long next = wallet.getBalance() + amount;
        if (next < 0) throw new ArenaProblem.RuleViolation("Saldo de pontos insuficiente.");
        wallet.setBalance(next);
        // Refunds restore previously used points; they are not new earnings and
        // therefore must never increase progression/XP.
        if (amount > 0 && countsAsEarned(type)) {
            wallet.setLifetimeEarned(Math.addExact(wallet.getLifetimeEarned(), amount));
        }
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

    private PointWallet walletForUpdate(User user) {
        // Lock before loading a regular entity into the persistence context.
        // Otherwise a concurrent commit between findByUser() and the lock query
        // can make Hibernate reject the lock upgrade as a stale @Version row.
        PointWallet locked = wallets.findByUserForUpdate(user).orElse(null);
        if (locked != null) return locked;
        ensureWallet(user);
        return wallets.findByUserForUpdate(user)
                .orElseThrow(() -> new IllegalStateException("Carteira de pontos não encontrada."));
    }

    private boolean countsAsEarned(PointTransactionType type) {
        return type != PointTransactionType.REFUND && type != PointTransactionType.INITIAL_BONUS;
    }

    private PointLedgerEntry compatible(PointLedgerEntry existing, User user, long amount,
                                        PointTransactionType type, String referenceType, String referenceId) {
        if (!existing.getWallet().getUser().getId().equals(user.getId())
                || existing.getAmount() != amount || existing.getType() != type
                || !Objects.equals(existing.getReferenceType(), referenceType)
                || !Objects.equals(existing.getReferenceId(), referenceId))
            throw new ArenaProblem.Conflict("A chave de idempotência de pontos já foi usada por outra operação.");
        return existing;
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
