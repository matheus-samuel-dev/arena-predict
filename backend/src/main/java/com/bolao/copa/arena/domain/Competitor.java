package com.bolao.copa.arena.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "arena_competitors", uniqueConstraints = @UniqueConstraint(columnNames = {"sport_id", "code"}))
public class Competitor {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "sport_id")
    private Sport sport;
    @Column(nullable = false, length = 120)
    private String name;
    @Column(nullable = false, length = 30)
    private String code;
    @Column(length = 2048)
    private String imageUrl;
    @Column(length = 40)
    private String externalProvider;
    @Column(length = 100)
    private String externalId;
    @Column(nullable = false)
    private boolean providerOwned;
    @Column(length = 40)
    private String acronym;
    @Column(length = 80)
    private String country;
    @Column(nullable = false)
    private boolean active = true;

    public Long getId() { return id; }
    public Sport getSport() { return sport; }
    public void setSport(Sport sport) { this.sport = sport; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    public String getCountry() { return country; }
    public void setCountry(String country) { this.country = country; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public String getExternalProvider() { return externalProvider; }
    public void setExternalProvider(String value) { externalProvider = value; providerOwned = value != null; }
    public String getExternalId() { return externalId; }
    public void setExternalId(String value) { externalId = value; }
    public String getAcronym() { return acronym; }
    public void setAcronym(String value) { acronym = value; }
    @PrePersist @PreUpdate
    private void deriveProviderOwnership() { providerOwned = externalProvider != null; }
}
