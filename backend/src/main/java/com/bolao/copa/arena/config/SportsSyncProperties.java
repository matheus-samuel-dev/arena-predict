package com.bolao.copa.arena.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;

@Validated
@ConfigurationProperties("sports.sync")
public record SportsSyncProperties(
        @DefaultValue("PANDASCORE") String provider,
        @DefaultValue("false") boolean enabled,
        @DefaultValue("900000") @Min(60000) long upcomingIntervalMs,
        @DefaultValue("120000") @Min(30000) long runningIntervalMs,
        @DefaultValue("300000") @Min(60000) long finishedIntervalMs,
        @DefaultValue("120000") @Min(30000) long trackedIntervalMs,
        @DefaultValue("30") @Min(1) int nearStartMinutes,
        @DefaultValue("7") @Min(1) @Max(30) int upcomingDays,
        @DefaultValue("72") @Min(1) @Max(720) int correctionWindowHours,
        @DefaultValue("20") @Min(1) @Max(100) int trackedBatchSize,
        @DefaultValue("900000") @Min(60000) long leaseMs) { }
