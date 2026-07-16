package com.bolao.copa.arena.repository;
import com.bolao.copa.arena.domain.Sport;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface SportRepository extends JpaRepository<Sport, Long> {
    Optional<Sport> findByCodeIgnoreCase(String code);
    List<Sport> findByActiveTrueOrderByDisplayOrderAscNameAsc();
}
