package com.bolao.copa.arena.service;

import static org.assertj.core.api.Assertions.*;

import com.bolao.copa.arena.domain.ArenaEvent;
import com.bolao.copa.arena.domain.Championship;
import com.bolao.copa.arena.domain.Competitor;
import org.junit.jupiter.api.Test;

class EventDataOwnershipTest {
    @Test
    void internalEventsCannotBorrowExternalChampionshipOrCompetitors() {
        var event = event(null);
        event.setDemo(true);
        assertThatCode(() -> EventDataOwnership.requireCompatibleCatalog(event)).doesNotThrowAnyException();
        event.getChampionship().setExternalProvider("PANDASCORE");
        assertThatThrownBy(() -> EventDataOwnership.requireCompatibleCatalog(event)).isInstanceOf(ArenaProblem.Conflict.class);
        event.getChampionship().setExternalProvider(null);
        event.getHomeCompetitor().setExternalProvider("PANDASCORE");
        assertThatThrownBy(() -> EventDataOwnership.requireCompatibleCatalog(event)).isInstanceOf(ArenaProblem.Conflict.class);
        // Generic participant lists must enforce the same rule as home/away fields.
        assertThatThrownBy(() -> EventDataOwnership.requireCompatibleCompetitor(event, event.getHomeCompetitor()))
                .isInstanceOf(ArenaProblem.Conflict.class);
    }

    @Test
    void externalEventsRequireCatalogFromTheSameProviderAndCannotBeManuallySettled() {
        var event = event("PANDASCORE");
        assertThatCode(() -> EventDataOwnership.requireCompatibleCatalog(event)).doesNotThrowAnyException();
        assertThatThrownBy(() -> EventDataOwnership.requireManualEvent(event)).isInstanceOf(ArenaProblem.Conflict.class);
        event.getAwayCompetitor().setExternalProvider("OTHER_PROVIDER");
        assertThatThrownBy(() -> EventDataOwnership.requireCompatibleCatalog(event)).isInstanceOf(ArenaProblem.Conflict.class);
        event.getAwayCompetitor().setExternalProvider(null);
        assertThatThrownBy(() -> EventDataOwnership.requireCompatibleCatalog(event)).isInstanceOf(ArenaProblem.Conflict.class);
    }

    private ArenaEvent event(String provider) {
        var championship = new Championship();
        championship.setExternalProvider(provider);
        var home = new Competitor(); home.setExternalProvider(provider);
        var away = new Competitor(); away.setExternalProvider(provider);
        var event = new ArenaEvent(); event.setExternalProvider(provider);
        event.setChampionship(championship); event.setHomeCompetitor(home); event.setAwayCompetitor(away);
        return event;
    }
}
