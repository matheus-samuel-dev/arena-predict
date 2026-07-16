package com.bolao.copa.arena.repository;
import com.bolao.copa.arena.domain.*;
import com.bolao.copa.arena.domain.ArenaEnums.ContentStatus;
import java.util.List;
import java.util.Collection;
import org.springframework.data.jpa.repository.*;
public interface CommunityCommentRepository extends JpaRepository<CommunityComment, Long> {
    long countByPostAndStatus(CommunityPost post, ContentStatus status);
    @EntityGraph(attributePaths = "author") List<CommunityComment> findByPostAndStatusOrderByCreatedAtAsc(CommunityPost post, ContentStatus status);
    @Query("select c.post.id as postId, count(c.id) as total from CommunityComment c where c.post in :posts and c.status = :status group by c.post.id")
    List<PostCount> countGrouped(Collection<CommunityPost> posts, ContentStatus status);
    interface PostCount { Long getPostId(); long getTotal(); }
}
