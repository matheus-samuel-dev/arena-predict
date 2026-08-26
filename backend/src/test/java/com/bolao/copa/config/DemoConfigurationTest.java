package com.bolao.copa.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.bolao.copa.arena.config.*;
import com.bolao.copa.arena.service.DemoLiveEventService;
import com.bolao.copa.arena.service.provider.DemoSportsDataProvider;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.*;
import org.springframework.core.annotation.Order;

class DemoConfigurationTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(DemoBeans.class);

    @Test
    void demoProviderAndSchedulerRequireMasterDemoSwitch() {
        contextRunner
                .withPropertyValues(
                        "app.demo.live-provider-enabled=true",
                        "app.demo.live-scheduler-enabled=true")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(DemoSportsDataProvider.class);
                    assertThat(context).doesNotHaveBean(DemoLiveRefreshScheduler.class);
                });
    }

    @Test
    void explicitDemoConfigurationEnablesProviderAndScheduler() {
        contextRunner
                .withPropertyValues(
                        "app.demo.enabled=true",
                        "app.demo.live-provider-enabled=true",
                        "app.demo.live-scheduler-enabled=true")
                .run(context -> {
                    assertThat(context).hasSingleBean(DemoSportsDataProvider.class);
                    assertThat(context).hasSingleBean(DemoLiveRefreshScheduler.class);
                });
    }

    @Test
    void progressionDefinitionsAreSeededBeforePredictions() throws Exception {
        Order experience = ArenaExperienceDemoInitializer.class.getDeclaredMethod("seed").getAnnotation(Order.class);
        Order arena = ArenaDemoInitializer.class.getDeclaredMethod("seed").getAnnotation(Order.class);

        assertThat(experience).isNotNull();
        assertThat(arena).isNotNull();
        assertThat(experience.value()).isLessThan(arena.value());
    }

    @Configuration(proxyBeanMethods = false)
    @Import({DemoSportsDataProvider.class, DemoLiveRefreshScheduler.class})
    static class DemoBeans {
        @Bean
        DemoLiveEventService demoLiveEventService() {
            return mock(DemoLiveEventService.class);
        }
    }
}
