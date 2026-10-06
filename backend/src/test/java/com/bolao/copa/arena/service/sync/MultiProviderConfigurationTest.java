package com.bolao.copa.arena.service.sync;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.bolao.copa.arena.config.SportsSyncProperties;
import com.bolao.copa.arena.repository.ArenaEventRepository;
import com.bolao.copa.arena.service.provider.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class MultiProviderConfigurationTest {
    @Test void legacySelectorCannotOverrideIndependentFlagsOrEnablePandaImplicitly() {
        var football=new ApiFootballProvider(new MockEnvironment().withProperty("API_FOOTBALL_ENABLED","true"),new ObjectMapper());
        var panda=mock(SportsDataProvider.class);when(panda.providerId()).thenReturn("PANDASCORE");when(panda.providerName()).thenReturn("PandaScore");
        when(panda.supportedSports()).thenReturn(List.of("CS2"));
        var registry=new SportsProviderRegistry(List.of(football,panda));
        var props=new SportsSyncProperties("API_FOOTBALL",false,900000,120000,300000,120000,30,7,72,20,900000);
        var coordinator=new MultiProviderSyncCoordinator(registry,mock(SportsSyncService.class),props,mock(SportsSyncStateStore.class),
                mock(SportsCatalogSyncService.class),mock(SportsMatchSyncService.class),mock(ArenaEventRepository.class));
        var views=coordinator.providers();
        assertThat(views.stream().filter(v->v.id().equals("API_FOOTBALL")).findFirst().orElseThrow().sync().enabled()).isTrue();
        assertThat(coordinator.defaultStatus().enabled()).isFalse();
        coordinator.scheduledSynchronize();verify(panda,never()).runningMatches();
        assertThat(coordinator.summary().healthy()).isFalse();
    }
}
