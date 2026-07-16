package com.bolao.copa.arena.repository;
import com.bolao.copa.arena.domain.PlayerProfile;
import com.bolao.copa.entity.User;
import java.util.Optional;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface PlayerProfileRepository extends JpaRepository<PlayerProfile, Long> {
    Optional<PlayerProfile> findByUser(User user);
    List<PlayerProfile> findByUserIn(Collection<User> users);
}
