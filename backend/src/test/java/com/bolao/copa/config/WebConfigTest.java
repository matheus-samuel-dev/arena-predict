package com.bolao.copa.config;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class WebConfigTest {
    private final WebConfig webConfig = new WebConfig();

    @Test
    void wildcardOriginsAreRejectedAtStartup() {
        assertThatThrownBy(() -> webConfig.corsConfigurationSource("*"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("origens explícitas");

        assertThatThrownBy(() -> webConfig.corsConfigurationSource("https://*.example.com"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("origens explícitas");
    }
}
