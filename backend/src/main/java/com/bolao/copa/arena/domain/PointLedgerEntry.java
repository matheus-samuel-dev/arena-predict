package com.bolao.copa.arena.domain;

import com.bolao.copa.arena.domain.ArenaEnums.PointTransactionType;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "arena_point_transactions")
public class PointLedgerEntry {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "wallet_id")
    private PointWallet wallet;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 32)
    private PointTransactionType type;
    @Column(nullable = false)
    private long amount;
    @Column(nullable = false)
    private long balanceAfter;
    @Column(nullable = false, unique = true, length = 160)
    private String idempotencyKey;
    @Column(length = 60)
    private String referenceType;
    @Column(length = 80)
    private String referenceId;
    @Column(nullable = false, length = 220)
    private String description;
    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    public Long getId() { return id; }
    public PointWallet getWallet() { return wallet; }
    public void setWallet(PointWallet wallet) { this.wallet = wallet; }
    public PointTransactionType getType() { return type; }
    public void setType(PointTransactionType type) { this.type = type; }
    public long getAmount() { return amount; }
    public void setAmount(long amount) { this.amount = amount; }
    public long getBalanceAfter() { return balanceAfter; }
    public void setBalanceAfter(long balanceAfter) { this.balanceAfter = balanceAfter; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
    public String getReferenceType() { return referenceType; }
    public void setReferenceType(String referenceType) { this.referenceType = referenceType; }
    public String getReferenceId() { return referenceId; }
    public void setReferenceId(String referenceId) { this.referenceId = referenceId; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Instant getCreatedAt() { return createdAt; }
}
