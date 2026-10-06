package com.bolao.copa.arena.service.provider;

public interface SportsHttpSettings {
    default int getMaxRequestsPerMinute() { return 20; }
    enum Authentication { BEARER, HEADER, FORM }
    String getApiToken(); String getBaseUrl();
    int getConnectTimeoutMs(); int getReadTimeoutMs(); int getMaxRetries(); int getRetryBackoffMs();
    long getFailureBackoffMs(); long getRateLimitBackoffMs(); int getMaxRequestsPerHour(); int getRemainingReserve();
    int getMaxPages(); int getPageSize();
    default int getMaxRequestsPerDay() { return Integer.MAX_VALUE; }
    default Authentication authentication() { return Authentication.BEARER; }
    default String credentialName() { return "Authorization"; }
    default String remainingHeader() { return "X-Rate-Limit-Remaining"; }
}
