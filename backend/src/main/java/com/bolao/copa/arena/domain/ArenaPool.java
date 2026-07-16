package com.bolao.copa.arena.domain;

import com.bolao.copa.arena.domain.ArenaEnums.PoolStatus;
import com.bolao.copa.arena.domain.ArenaEnums.PoolType;
import com.bolao.copa.entity.User;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "arena_pools")
public class ArenaPool {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 120)
    private String name;
    @Column(length = 1000)
    private String description;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "sport_id")
    private Sport sport;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "championship_id")
    private Championship championship;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "owner_id")
    private User owner;
    @Column(nullable = false, unique = true, length = 16)
    private String inviteCode;
    @Column(nullable = false)
    private boolean publicPool;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16)
    private PoolType poolType = PoolType.POOL;
    @Column(nullable = false)
    private boolean recurring;
    @Column(nullable = false)
    private int maxParticipants = 100;
    @Column(nullable = false)
    private int virtualPrizePoints;
    @Column(nullable = false, length = 1200)
    private String rules;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private PoolStatus status = PoolStatus.OPEN;
    private Instant startsAt;
    private Instant endsAt;
    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    public Long getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Sport getSport() { return sport; }
    public void setSport(Sport sport) { this.sport = sport; }
    public Championship getChampionship() { return championship; }
    public void setChampionship(Championship championship) { this.championship = championship; }
    public User getOwner() { return owner; }
    public void setOwner(User owner) { this.owner = owner; }
    public String getInviteCode() { return inviteCode; }
    public void setInviteCode(String inviteCode) { this.inviteCode = inviteCode; }
    public boolean isPublicPool() { return publicPool; }
    public void setPublicPool(boolean publicPool) { this.publicPool = publicPool; }
    public PoolType getPoolType() { return poolType; }
    public void setPoolType(PoolType poolType) { this.poolType = poolType; }
    public boolean isRecurring() { return recurring; }
    public void setRecurring(boolean recurring) { this.recurring = recurring; }
    public int getMaxParticipants() { return maxParticipants; }
    public void setMaxParticipants(int maxParticipants) { this.maxParticipants = maxParticipants; }
    public int getVirtualPrizePoints() { return virtualPrizePoints; }
    public void setVirtualPrizePoints(int virtualPrizePoints) { this.virtualPrizePoints = virtualPrizePoints; }
    public String getRules() { return rules; }
    public void setRules(String rules) { this.rules = rules; }
    public PoolStatus getStatus() { return status; }
    public void setStatus(PoolStatus status) { this.status = status; }
    public Instant getStartsAt() { return startsAt; }
    public void setStartsAt(Instant startsAt) { this.startsAt = startsAt; }
    public Instant getEndsAt() { return endsAt; }
    public void setEndsAt(Instant endsAt) { this.endsAt = endsAt; }
    public Instant getCreatedAt() { return createdAt; }
}
