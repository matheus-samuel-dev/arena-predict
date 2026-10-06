package com.bolao.copa.arena.service.provider.pandascore;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Component
@Validated
@ConfigurationProperties(prefix = "sports.pandascore")
public class PandaScoreProperties implements com.bolao.copa.arena.service.provider.SportsHttpSettings {
    private String apiToken = "";
    private String baseUrl = "https://api.pandascore.co";
    private boolean liveScoresEnabled;
    @NotEmpty private List<@Pattern(regexp = "csgo|lol|valorant") String> videogames = List.of("csgo", "lol", "valorant");
    @Min(100) @Max(30000) private int connectTimeoutMs = 3000;
    @Min(100) @Max(60000) private int readTimeoutMs = 8000;
    @Min(0) @Max(2) private int maxRetries = 1;
    @Min(0) @Max(5000) private int retryBackoffMs = 500;
    @Min(1000) private long failureBackoffMs = 60000;
    @Min(60000) private long rateLimitBackoffMs = 3600000;
    @Min(1) @Max(10000) private int maxRequestsPerHour = 600;
    @Min(0) private int remainingReserve = 20;
    @Min(1) @Max(10) private int maxPages = 3;
    @Min(1) @Max(100) private int pageSize = 100;
    @Min(60000) private long referenceCacheTtlMs = 21600000;

    public String getApiToken() { return apiToken; }
    public void setApiToken(String value) { apiToken = value; }
    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String value) { baseUrl = value; }
    public boolean isLiveScoresEnabled() { return liveScoresEnabled; }
    public List<String> getVideogames() { return videogames; }
    public void setVideogames(List<String> value) { videogames = value; }
    public void setLiveScoresEnabled(boolean value) { liveScoresEnabled = value; }
    public int getConnectTimeoutMs() { return connectTimeoutMs; }
    public void setConnectTimeoutMs(int value) { connectTimeoutMs = value; }
    public int getReadTimeoutMs() { return readTimeoutMs; }
    public void setReadTimeoutMs(int value) { readTimeoutMs = value; }
    public int getMaxRetries() { return maxRetries; }
    public void setMaxRetries(int value) { maxRetries = value; }
    public int getRetryBackoffMs() { return retryBackoffMs; }
    public void setRetryBackoffMs(int value) { retryBackoffMs = value; }
    public long getFailureBackoffMs() { return failureBackoffMs; }
    public void setFailureBackoffMs(long value) { failureBackoffMs = value; }
    public long getRateLimitBackoffMs() { return rateLimitBackoffMs; }
    public void setRateLimitBackoffMs(long value) { rateLimitBackoffMs = value; }
    public int getMaxRequestsPerHour() { return maxRequestsPerHour; }
    public void setMaxRequestsPerHour(int value) { maxRequestsPerHour = value; }
    public int getRemainingReserve() { return remainingReserve; }
    public void setRemainingReserve(int value) { remainingReserve = value; }
    public int getMaxPages() { return maxPages; }
    public void setMaxPages(int value) { maxPages = value; }
    public int getPageSize() { return pageSize; }
    public void setPageSize(int value) { pageSize = value; }
    public long getReferenceCacheTtlMs() { return referenceCacheTtlMs; }
    public void setReferenceCacheTtlMs(long value) { referenceCacheTtlMs = value; }
}
