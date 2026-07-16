package com.bolao.copa.arena.repository;
import com.bolao.copa.arena.domain.*;
import com.bolao.copa.entity.User;
import java.util.Optional;
import java.util.*;
import org.springframework.data.jpa.repository.*;
public interface CommunityLikeRepository extends JpaRepository<CommunityLike, Long> {
    Optional<CommunityLike> findByPostAndUser(CommunityPost post, User user);
    long countByPost(CommunityPost post);
    List<CommunityLike> findByPostInAndUser(Collection<CommunityPost> posts, User user);
    @Query("select l.post.id as postId, count(l.id) as total from CommunityLike l where l.post in :posts group by l.post.id")
    List<PostCount> countGrouped(Collection<CommunityPost> posts);
    interface PostCount { Long getPostId(); long getTotal(); }
}
