package com.bolao.copa.arena.repository;
import com.bolao.copa.arena.domain.*;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface CompetitorRepository extends JpaRepository<Competitor, Long> {
    Optional<Competitor> findBySportAndCodeIgnoreCase(Sport sport, String code);
    List<Competitor> findBySportOrderByNameAsc(Sport sport);
}
