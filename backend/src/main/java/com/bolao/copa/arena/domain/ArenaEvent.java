package com.bolao.copa.arena.domain;

import com.bolao.copa.arena.domain.ArenaEnums.EventFormat;
import com.bolao.copa.arena.domain.ArenaEnums.EventStatus;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "arena_events")
public class ArenaEvent {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true, length = 100)
    private String externalKey;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "championship_id")
    private Championship championship;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "home_competitor_id")
    private Competitor homeCompetitor;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "away_competitor_id")
    private Competitor awayCompetitor;
    @Column(nullable = false, length = 180)
    private String title;
    @Column(length = 100)
    private String stage;
    @Column(length = 160)
    private String venue;
    @Column(length = 160)
    private String broadcast;
    @Column(length = 300)
    private String imageUrl;
    @Column(nullable = false)
    private Instant startsAt;
    @Column(nullable = false)
    private Instant predictionClosesAt;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 32)
    private EventStatus status = EventStatus.SCHEDULED;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private EventFormat format = EventFormat.STANDARD;
    @Column(nullable = false)
    private int bestOf = 1;
    private Integer homeScore;
    private Integer awayScore;
    @Column(length = 80)
    private String clock;
    @Column(length = 80)
    private String period;
    @Column(length = 4000)
    private String liveData;
    @Column(length = 16000)
    private String resultData;
    @Column(nullable = false)
    private boolean featured;
    @Column(nullable = false)
    private boolean demo;
    @Version
    private long version;

    public Long getId() { return id; }
    public String getExternalKey() { return externalKey; }
    public void setExternalKey(String externalKey) { this.externalKey = externalKey; }
    public Championship getChampionship() { return championship; }
    public void setChampionship(Championship championship) { this.championship = championship; }
    public Competitor getHomeCompetitor() { return homeCompetitor; }
    public void setHomeCompetitor(Competitor homeCompetitor) { this.homeCompetitor = homeCompetitor; }
    public Competitor getAwayCompetitor() { return awayCompetitor; }
    public void setAwayCompetitor(Competitor awayCompetitor) { this.awayCompetitor = awayCompetitor; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getStage() { return stage; }
    public void setStage(String stage) { this.stage = stage; }
    public String getVenue() { return venue; }
    public void setVenue(String venue) { this.venue = venue; }
    public String getBroadcast() { return broadcast; }
    public void setBroadcast(String broadcast) { this.broadcast = broadcast; }
    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    public Instant getStartsAt() { return startsAt; }
    public void setStartsAt(Instant startsAt) { this.startsAt = startsAt; }
    public Instant getPredictionClosesAt() { return predictionClosesAt; }
    public void setPredictionClosesAt(Instant predictionClosesAt) { this.predictionClosesAt = predictionClosesAt; }
    public EventStatus getStatus() { return status; }
    public void setStatus(EventStatus status) { this.status = status; }
    public EventFormat getFormat() { return format; }
    public void setFormat(EventFormat format) { this.format = format; }
    public int getBestOf() { return bestOf; }
    public void setBestOf(int bestOf) { this.bestOf = bestOf; }
    public Integer getHomeScore() { return homeScore; }
    public void setHomeScore(Integer homeScore) { this.homeScore = homeScore; }
    public Integer getAwayScore() { return awayScore; }
    public void setAwayScore(Integer awayScore) { this.awayScore = awayScore; }
    public String getClock() { return clock; }
    public void setClock(String clock) { this.clock = clock; }
    public String getPeriod() { return period; }
    public void setPeriod(String period) { this.period = period; }
    public String getLiveData() { return liveData; }
    public String getResultData() { return resultData; }
    public void setResultData(String value) { resultData = value; }
    public void setLiveData(String liveData) { this.liveData = liveData; }
    public boolean isFeatured() { return featured; }
    public void setFeatured(boolean featured) { this.featured = featured; }
    public boolean isDemo() { return demo; }
    public void setDemo(boolean demo) { this.demo = demo; }
}
