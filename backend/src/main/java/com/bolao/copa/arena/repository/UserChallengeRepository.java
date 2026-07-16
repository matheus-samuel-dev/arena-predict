package com.bolao.copa.arena.repository;
import com.bolao.copa.arena.domain.*;
import com.bolao.copa.entity.User;
import java.util.*;
import org.springframework.data.jpa.repository.*;
public interface UserChallengeRepository extends JpaRepository<UserChallenge, Long> {
    Optional<UserChallenge> findByUserAndChallenge(User user, ChallengeDefinition challenge);
    @EntityGraph(attributePaths = "challenge") List<UserChallenge> findByUser(User user);
}
