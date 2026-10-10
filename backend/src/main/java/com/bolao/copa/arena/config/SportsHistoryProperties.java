package com.bolao.copa.arena.config;

import jakarta.validation.constraints.*;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("sports.history")
public record SportsHistoryProperties(@DefaultValue("true") boolean enabled,
        @DefaultValue("30") @Min(4) @Max(90) int days,
        @DefaultValue("30000") @Min(30000) long intervalMs,
        @DefaultValue("CS2") @NotEmpty List<String> sports) { }
