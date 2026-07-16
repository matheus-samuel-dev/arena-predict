package com.bolao.copa.arena.repository;

import com.bolao.copa.arena.domain.*;
import java.util.List;
import java.util.Collection;
import org.springframework.data.jpa.repository.*;

public interface EventParticipantRepository extends JpaRepository<EventParticipant, Long> {
    @EntityGraph(attributePaths = "competitor")
    List<EventParticipant> findByEventOrderByDisplayOrderAsc(ArenaEvent event);
    @EntityGraph(attributePaths = "competitor")
    List<EventParticipant> findByEventInOrderByEventIdAscDisplayOrderAsc(Collection<ArenaEvent> events);
}
