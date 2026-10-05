package com.bolao.copa.arena.service.sync;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.bolao.copa.arena.config.SportsSyncProperties;
import com.bolao.copa.arena.repository.ArenaEventRepository;
import com.bolao.copa.arena.service.provider.*;
import com.bolao.copa.arena.service.sync.SportsSyncStateStore.Feed;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SportsSyncServiceTest {
    SportsDataProvider provider=mock(SportsDataProvider.class);
    SportsSyncStateStore state=mock(SportsSyncStateStore.class);
    SportsCatalogSyncService catalog=mock(SportsCatalogSyncService.class);
    SportsMatchSyncService matches=mock(SportsMatchSyncService.class);
    ArenaEventRepository events=mock(ArenaEventRepository.class);
    SportsSyncService sync;
    @BeforeEach void setUp() {
        when(provider.providerId()).thenReturn("PANDASCORE"); when(provider.providerName()).thenReturn("PandaScore");
        when(provider.available()).thenReturn(true);
        when(provider.lastHttpStatus()).thenReturn(null);
        when(state.claim(eq("PANDASCORE"),anyString(),any(),any())).thenReturn(true);
        when(catalog.synchronize(anyString(),anyList())).thenReturn(new SportsCatalogSyncService.References(Map.of(),Map.of()));
        sync=new SportsSyncService(List.of(provider),properties(true),state,catalog,matches,events);
    }
    @Test void missingTokenAndDisabledSyncNeverCallTheNetwork() {
        when(provider.available()).thenReturn(false); sync.synchronize();
        verify(provider,never()).runningMatches(); verifyNoInteractions(state);
        when(provider.available()).thenReturn(true);
        new SportsSyncService(List.of(provider),properties(false),state,catalog,matches,events).synchronize();
        verify(provider,never()).runningMatches();
    }
    @Test void feedIntervalsAvoidPollingEveryFeedAtSchedulerFrequency() {
        when(state.lastFeed(eq("PANDASCORE"),any())).thenReturn(Instant.now());
        sync.synchronize();
        verify(provider,never()).runningMatches(); verify(provider,never()).upcomingMatches(any(),any());
        verify(provider,never()).finishedMatches(any()); verify(provider,never()).matchDetails(anyList());
        verify(state).release(eq("PANDASCORE"),anyString());
    }
    @Test void onlyRunningFeedIsFetchedWhenItsIntervalExpires() {
        when(state.lastFeed(eq("PANDASCORE"),any())).thenReturn(Instant.now());
        when(state.lastFeed("PANDASCORE",Feed.RUNNING)).thenReturn(Instant.now().minusSeconds(121));
        sync.synchronize();
        verify(provider).runningMatches(); verify(provider,never()).upcomingMatches(any(),any());
        verify(state).completed(eq("PANDASCORE"),eq(Feed.RUNNING),any());
    }
    @Test void unavailableApiKeepsPersistedDataAndReleasesLease() {
        when(provider.runningMatches()).thenThrow(new SportsProviderException(SportsProviderException.Reason.UNAVAILABLE,"safe",Instant.now().plusSeconds(60)));
        assertThatCode(sync::synchronize).doesNotThrowAnyException();
        verifyNoInteractions(catalog,matches);
        verify(state).runCompleted(eq("PANDASCORE"),any(),eq("UNAVAILABLE"),contains("salvos"),any(),
                eq(0),eq(0),eq(0),eq(0),eq(0),anyLong(),isNull(),eq("UNAVAILABLE"));
        verify(state).release(eq("PANDASCORE"),anyString());
    }
    @Test void rateLimitAndCooldownPreventFurtherRequests() {
        when(provider.runningMatches()).thenThrow(new SportsProviderException(SportsProviderException.Reason.RATE_LIMITED,"safe",Instant.now().plusSeconds(3600)));
        sync.synchronize();
        verify(state).runCompleted(eq("PANDASCORE"),any(),eq("RATE_LIMITED"),anyString(),any(),
                eq(0),eq(0),eq(0),eq(0),eq(0),anyLong(),isNull(),eq("RATE_LIMITED"));
        verify(provider,never()).upcomingMatches(any(),any());
        clearInvocations(provider,state);
        when(provider.nextAllowedRequestAt()).thenReturn(Instant.now().plusSeconds(3600));
        sync.synchronize(); verifyNoInteractions(state); verify(provider,never()).runningMatches();
    }
    @Test void secondReplicaCannotPollWhileLeaseIsOwned() {
        when(state.claim(eq("PANDASCORE"),anyString(),any(),any())).thenReturn(false);
        sync.synchronize(); verify(provider,never()).runningMatches(); verifyNoInteractions(catalog,matches);
    }
    @Test void emptyTrackingDoesNotClaimAnExternalSuccess() {
        when(state.lastFeed(eq("PANDASCORE"),any())).thenReturn(Instant.now());
        when(state.lastFeed("PANDASCORE",Feed.TRACKED)).thenReturn(null);
        sync.synchronize();
        verify(matches).quarantineExpiredResults(eq("PANDASCORE"),any(),eq(20));
        verify(state).checkedWithoutRequest(eq("PANDASCORE"),eq(Feed.TRACKED),any());
        verify(state,never()).completed(anyString(),any(),any());
        verify(provider,never()).matchDetails(anyList());
        verify(state,never()).attempted(anyString(),any());
    }

    @Test void schedulerHeartbeatDoesNotInventAProviderAttemptWithoutToken() {
        when(provider.available()).thenReturn(false);
        sync.scheduledSynchronize();
        verify(state).schedulerTick(eq("PANDASCORE"),any());
        verify(state,never()).attempted(anyString(),any());
        verify(provider,never()).runningMatches();
    }

    @Test void presentCredentialDoesNotClaimHealthBeforeAnySuccessfulResponse() {
        when(state.snapshot("PANDASCORE")).thenReturn(new SportsSyncStateStore.Snapshot(null,null,"UNCONFIGURED",null));
        assertThat(sync.status().configured()).isTrue();
        assertThat(sync.status().status()).isEqualTo("CONFIGURED");
        assertThat(sync.summary().healthy()).isFalse();
        assertThat(sync.summary().lastSuccessAt()).isNull();
    }

    @Test void persistedProviderCooldownSurvivesANewClientInstance() {
        Instant now=Instant.parse("2026-10-05T12:00:00Z"), retry=now.plusSeconds(3600);
        when(state.snapshot("PANDASCORE")).thenReturn(new SportsSyncStateStore.Snapshot(now,now,"RATE_LIMITED","safe",
                now,now,now,now,now,now,retry,0,0,0,0,0,0L,429,"RATE_LIMITED"));
        var fixed=new SportsSyncService(List.of(provider),properties(true),state,catalog,matches,events,java.time.Clock.fixed(now,java.time.ZoneOffset.UTC));
        fixed.synchronize();
        verify(provider,never()).runningMatches();
        verify(state,never()).claim(anyString(),anyString(),any(),any());
        assertThat(fixed.status().nextAllowedRequestAt()).isEqualTo(retry);
        assertThat(fixed.status().nextSyncAt()).isAfterOrEqualTo(retry);
    }

    @Test void staleSuccessNeverTellsParticipantsTheIntegrationIsHealthy() {
        Instant now=Instant.parse("2026-10-05T12:00:00Z"), stale=now.minusSeconds(600);
        when(state.snapshot("PANDASCORE")).thenReturn(new SportsSyncStateStore.Snapshot(stale,stale,"ONLINE","safe"));
        var fixed=new SportsSyncService(List.of(provider),properties(true),state,catalog,matches,events,java.time.Clock.fixed(now,java.time.ZoneOffset.UTC));
        assertThat(fixed.status().status()).isEqualTo("DEGRADED");
        assertThat(fixed.summary().healthy()).isFalse();
        assertThat(fixed.summary().lastSuccessAt()).isEqualTo(stale);
    }
    private SportsSyncProperties properties(boolean enabled) {
        return new SportsSyncProperties("PANDASCORE",enabled,900000,120000,300000,120000,30,7,72,20,900000);
    }
}
