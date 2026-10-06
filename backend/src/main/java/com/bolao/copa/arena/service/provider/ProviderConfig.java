package com.bolao.copa.arena.service.provider;

import org.springframework.core.env.Environment;

/** Config is adapter-local. Its credential is never included in toString/status. */
public final class ProviderConfig implements SportsHttpSettings {
    private final Environment env;
    private final String prefix,key,base;
    private final Authentication auth;
    public ProviderConfig(Environment env,String prefix,String key,String base,Authentication auth) {
        this.env=env;this.prefix=prefix;this.key=key;this.base=base;this.auth=auth;
    }
    private int number(String suffix,int fallback) { int value=env.getProperty(prefix+"_"+suffix,Integer.class,fallback); if(value<1) throw new IllegalArgumentException("Invalid provider limit"); return value; }
    public boolean enabled() { return env.getProperty(prefix+"_ENABLED",Boolean.class,false); }
    public String credentialVariable() { return key; }
    public long intervalMs() { return number("SYNC_INTERVAL_MS",900000); }
    public String getApiToken() { return env.getProperty(key,""); }
    public String getBaseUrl() { return base; }
    public int getConnectTimeoutMs() { return 3000; }
    public int getReadTimeoutMs() { return 8000; }
    public int getMaxRetries() { return 1; }
    public int getRetryBackoffMs() { return 500; }
    public long getFailureBackoffMs() { return 60000; }
    public long getRateLimitBackoffMs() { return 86400000; }
    public int getMaxRequestsPerHour() { return number("REQUESTS_PER_HOUR",10); }
    public int getMaxRequestsPerDay() { return number("REQUESTS_PER_DAY",100); }
    public int getRemainingReserve() { return 1; }
    public int getMaxPages() { return 3; }
    public int getPageSize() { return 100; }
    public Authentication authentication() { return auth; }
    public String credentialName() { return auth==Authentication.FORM?"APIkey":"x-apisports-key"; }
    public String remainingHeader() { return auth==Authentication.FORM?"X-RateLimit-Remaining":"x-ratelimit-requests-remaining"; }
}
