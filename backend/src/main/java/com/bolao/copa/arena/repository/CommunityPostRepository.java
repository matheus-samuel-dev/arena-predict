package com.bolao.copa.arena.repository;
import com.bolao.copa.arena.domain.*;
import com.bolao.copa.arena.domain.ArenaEnums.ContentStatus;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
public interface CommunityPostRepository extends JpaRepository<CommunityPost, Long> {
    @EntityGraph(attributePaths = "author") Page<CommunityPost> findByStatusOrderByCreatedAtDesc(ContentStatus status, Pageable pageable);
    @EntityGraph(attributePaths = "author") Optional<CommunityPost> findByIdAndStatus(Long id, ContentStatus status);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = "author")
    @Query("select post from CommunityPost post where post.id = :id and post.status = :status")
    Optional<CommunityPost> findByIdAndStatusForUpdate(@Param("id") Long id, @Param("status") ContentStatus status);
    Optional<CommunityPost> findBySourceKey(String sourceKey);
}
