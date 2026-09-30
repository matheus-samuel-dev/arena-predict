package com.bolao.copa.arena.service;

import com.bolao.copa.arena.domain.ArenaEvent;
import com.bolao.copa.arena.domain.Championship;
import com.bolao.copa.arena.domain.Competitor;
import java.util.Objects;

/** Result and lifecycle ownership is independent of the demo flag supplied by an administrator. */
public final class EventDataOwnership {
    private EventDataOwnership() { }

    static void requireManualEvent(ArenaEvent event) {
        if (event.getExternalProvider() != null)
            throw new ArenaProblem.Conflict("Partidas reais sincronizadas são controladas pelo provedor esportivo e não podem ser simuladas ou alteradas manualmente.");
    }

    /** Generic administration must not change the scenario owned by the Demo orchestrator. */
    static void requireUnmanagedDemoEvent(ArenaEvent event) {
        if (event.isDemoManaged() || event.isDemoArchived()
                || (event.getChampionship() != null && event.getChampionship().isDemoManaged()))
            throw new ArenaProblem.Conflict("A competição demonstrativa controlada é somente leitura nesta área. Use as ações da demonstração.");
    }

    static void requireUnmanagedDemoChampionship(Championship championship) {
        if (championship.isDemoManaged())
            throw new ArenaProblem.Conflict("A competição demonstrativa controlada é mantida exclusivamente pelo fluxo Demo.");
    }

    public static void requireCompatibleCatalog(ArenaEvent event) {
        if (!Objects.equals(event.getExternalProvider(), event.getChampionship().getExternalProvider()))
            throw new ArenaProblem.Conflict("Eventos demonstrativos ou manuais usam campeonatos internos; partidas externas usam o catálogo do seu provedor.");
        requireCompatibleCompetitor(event, event.getHomeCompetitor());
        requireCompatibleCompetitor(event, event.getAwayCompetitor());
    }

    public static void requireCompatibleCompetitor(ArenaEvent event, Competitor competitor) {
        if (competitor != null && !Objects.equals(event.getExternalProvider(), competitor.getExternalProvider()))
            throw new ArenaProblem.Conflict("Eventos demonstrativos ou manuais usam participantes internos; partidas externas usam o catálogo do seu provedor.");
    }
}
