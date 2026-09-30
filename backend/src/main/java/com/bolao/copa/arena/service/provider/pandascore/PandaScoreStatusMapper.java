package com.bolao.copa.arena.service.provider.pandascore;

import com.bolao.copa.arena.domain.ArenaEnums.EventStatus;
import java.util.Optional;

/** Unknown statuses are rejected; an unrecognized state must never reopen predictions. */
public final class PandaScoreStatusMapper {
    private PandaScoreStatusMapper() { }

    public static Optional<EventStatus> map(String status) {
        if (status == null) return Optional.empty();
        return Optional.ofNullable(switch (status) {
            case "not_started" -> EventStatus.SCHEDULED;
            case "running" -> EventStatus.LIVE;
            case "finished" -> EventStatus.FINISHED;
            case "canceled" -> EventStatus.CANCELLED;
            case "postponed" -> EventStatus.POSTPONED;
            default -> null;
        });
    }
}
