package com.bolao.copa.arena.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.bolao.copa.arena.domain.ArenaEvent;
import com.bolao.copa.arena.domain.ArenaEnums.EventStatus;
import com.bolao.copa.arena.domain.Championship;
import com.bolao.copa.arena.repository.ArenaEventRepository;
import com.bolao.copa.arena.repository.PredictionMarketRepository;
import com.bolao.copa.arena.service.provider.SportsDataProvider;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class DemoLiveEventOwnershipTest {
    @Test
    @SuppressWarnings("unchecked")
    void legacyLiveProviderCannotFabricateScoresForControlledOrRealEvents() {
        var events = mock(ArenaEventRepository.class);
        var markets = mock(PredictionMarketRepository.class);
        var availability = mock(MarketAvailabilityService.class);
        var provider = mock(SportsDataProvider.class);
        ObjectProvider<SportsDataProvider> providers = mock(ObjectProvider.class);
        when(provider.demo()).thenReturn(true);
        when(provider.providerName()).thenReturn("Demo test fixture");
        when(providers.orderedStream()).thenReturn(Stream.of(provider));
        var championship = new Championship(); championship.setDemoManaged(true);
        var controlled = new ArenaEvent(); controlled.setDemo(true); controlled.setChampionship(championship);
        controlled.setStatus(EventStatus.LIVE);
        var real = new ArenaEvent(); real.setExternalProvider("PANDASCORE"); real.setStatus(EventStatus.LIVE);
        when(events.findByExternalKeyForUpdate("controlled")).thenReturn(Optional.of(controlled));
        when(events.findByExternalKeyForUpdate("real")).thenReturn(Optional.of(real));
        when(provider.liveUpdates()).thenReturn(List.of(
                new SportsDataProvider.LiveEventUpdate("controlled", 1, 1, "10:00", "Mapa 2", "{}"),
                new SportsDataProvider.LiveEventUpdate("real", 1, 1, "10:00", "Mapa 2", "{}")));

        var response = new DemoLiveEventService(events, providers, markets, availability).refresh();

        assertThat(response.get("updated")).isEqualTo(0);
        assertThat(controlled.getHomeScore()).isNull(); assertThat(controlled.getAwayScore()).isNull();
        assertThat(controlled.getClock()).isNull(); assertThat(controlled.getLiveData()).isNull();
        assertThat(real.getHomeScore()).isNull(); assertThat(real.getAwayScore()).isNull();
        verifyNoInteractions(markets, availability);
    }
}
