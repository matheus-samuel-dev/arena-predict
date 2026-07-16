package com.bolao.copa.arena.repository;
import com.bolao.copa.arena.domain.*;
import com.bolao.copa.entity.User;
import java.util.Optional;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
public interface CommunityReportRepository extends JpaRepository<CommunityReport, Long> {
    Optional<CommunityReport> findByPostAndReporter(CommunityPost post, User reporter);
    @Override @EntityGraph(attributePaths = {"post", "post.author", "reporter", "moderatedBy"}) Page<CommunityReport> findAll(Pageable pageable);
}
