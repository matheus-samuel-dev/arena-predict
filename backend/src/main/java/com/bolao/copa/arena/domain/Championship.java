package com.bolao.copa.arena.domain;

import com.bolao.copa.arena.domain.ArenaEnums.ChampionshipStatus;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "arena_championships", uniqueConstraints = @UniqueConstraint(columnNames = {"sport_id", "slug", "season"}))
public class Championship {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "sport_id")
    private Sport sport;
    @Column(nullable = false, length = 120)
    private String name;
    @Column(nullable = false, length = 120)
    private String slug;
    @Column(nullable = false, length = 40)
    private String season;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private ChampionshipStatus status = ChampionshipStatus.ACTIVE;
    @Column(length = 300)
    private String imageUrl;
    private Instant startsAt;
    private Instant endsAt;

    public Long getId() { return id; }
    public Sport getSport() { return sport; }
    public void setSport(Sport sport) { this.sport = sport; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getSlug() { return slug; }
    public void setSlug(String slug) { this.slug = slug; }
    public String getSeason() { return season; }
    public void setSeason(String season) { this.season = season; }
    public ChampionshipStatus getStatus() { return status; }
    public void setStatus(ChampionshipStatus status) { this.status = status; }
    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    public Instant getStartsAt() { return startsAt; }
    public void setStartsAt(Instant startsAt) { this.startsAt = startsAt; }
    public Instant getEndsAt() { return endsAt; }
    public void setEndsAt(Instant endsAt) { this.endsAt = endsAt; }
}
