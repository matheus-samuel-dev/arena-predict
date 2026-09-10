package com.bolao.copa.arena.repository;
import com.bolao.copa.arena.domain.*;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
public interface ChampionshipRepository extends JpaRepository<Championship, Long> {
    boolean existsBySport(Sport sport);
    @Override @EntityGraph(attributePaths = "sport") Page<Championship> findAll(Pageable pageable);
    @EntityGraph(attributePaths = "sport") List<Championship> findAllByOrderByNameAsc();
    @EntityGraph(attributePaths = "sport") List<Championship> findBySportOrderByNameAsc(Sport sport);
    @EntityGraph(attributePaths = "sport")
    @Query("select championship from Championship championship where " +
            "lower(championship.name) like lower(concat('%', :search, '%')) or " +
            "lower(championship.slug) like lower(concat('%', :search, '%')) or " +
            "lower(championship.season) like lower(concat('%', :search, '%')) or " +
            "lower(championship.sport.name) like lower(concat('%', :search, '%'))")
    Page<Championship> search(@Param("search") String search, Pageable pageable);
    Optional<Championship> findBySportAndSlugAndSeason(Sport sport, String slug, String season);
}
