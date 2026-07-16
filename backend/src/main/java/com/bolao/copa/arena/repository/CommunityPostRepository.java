package com.bolao.copa.arena.repository;
import com.bolao.copa.arena.domain.*;
import com.bolao.copa.arena.domain.ArenaEnums.ContentStatus;
import java.util.Optional;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
public interface CommunityPostRepository extends JpaRepository<CommunityPost, Long> {
    @EntityGraph(attributePaths = "author") Page<CommunityPost> findByStatusOrderByCreatedAtDesc(ContentStatus status, Pageable pageable);
    @EntityGraph(attributePaths = "author") Optional<CommunityPost> findByIdAndStatus(Long id, ContentStatus status);
    Optional<CommunityPost> findBySourceKey(String sourceKey);
}
