package com.bolao.copa.arena.service;

import static com.bolao.copa.arena.api.ExperienceDtos.*;

import com.bolao.copa.arena.domain.*;
import com.bolao.copa.arena.domain.ArenaEnums.*;
import com.bolao.copa.arena.repository.*;
import com.bolao.copa.entity.User;
import java.time.Instant;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CommunityService {
    private final CommunityPostRepository posts;
    private final CommunityCommentRepository comments;
    private final CommunityLikeRepository likes;
    private final CommunityReportRepository reports;
    private final PlayerProfileRepository profiles;
    private final ArenaNotificationService notifications;
    private final AdminAuditService audit;

    public CommunityService(CommunityPostRepository posts, CommunityCommentRepository comments,
                            CommunityLikeRepository likes, CommunityReportRepository reports,
                            PlayerProfileRepository profiles, ArenaNotificationService notifications,
                            AdminAuditService audit) {
        this.posts = posts; this.comments = comments; this.likes = likes; this.reports = reports;
        this.profiles = profiles; this.notifications = notifications;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public Page<PostResponse> feed(int page, int size, User current) {
        int safeSize = Math.max(1, Math.min(size, 50));
        Page<CommunityPost> result = posts.findByStatusOrderByCreatedAtDesc(ContentStatus.PUBLISHED,
                PageRequest.of(Math.max(0, page), safeSize));
        List<CommunityPost> pagePosts = result.getContent();
        if (pagePosts.isEmpty()) return new PageImpl<>(List.of(), result.getPageable(), result.getTotalElements());
        Map<Long, String> avatars = new HashMap<>();
        profiles.findByUserIn(pagePosts.stream().map(CommunityPost::getAuthor).distinct().toList())
                .forEach(profile -> avatars.put(profile.getUser().getId(), profile.getAvatarUrl()));
        Map<Long, Long> likeCounts = new HashMap<>();
        likes.countGrouped(pagePosts).forEach(count -> likeCounts.put(count.getPostId(), count.getTotal()));
        Map<Long, Long> commentCounts = new HashMap<>();
        comments.countGrouped(pagePosts, ContentStatus.PUBLISHED).forEach(count -> commentCounts.put(count.getPostId(), count.getTotal()));
        Set<Long> liked = new HashSet<>();
        likes.findByPostInAndUser(pagePosts, current).forEach(like -> liked.add(like.getPost().getId()));
        List<PostResponse> content = pagePosts.stream().map(post -> response(post, current,
                avatars.get(post.getAuthor().getId()), likeCounts.getOrDefault(post.getId(), 0L),
                commentCounts.getOrDefault(post.getId(), 0L), liked.contains(post.getId()))).toList();
        return new PageImpl<>(content, result.getPageable(), result.getTotalElements());
    }

    @Transactional
    public PostResponse create(PostRequest request, User author) {
        CommunityPost post = new CommunityPost(); post.setAuthor(author); post.setContent(request.content().trim());
        if (request.topic() != null && !request.topic().isBlank()) post.setTopic(request.topic().trim());
        return response(posts.save(post), author);
    }

    @Transactional
    public PostResponse like(Long postId, User user) {
        CommunityPost post = publishedForUpdate(postId);
        likes.findByPostAndUser(post, user).orElseGet(() -> { CommunityLike like = new CommunityLike(); like.setPost(post); like.setUser(user); return likes.save(like); });
        return response(post, user);
    }

    @Transactional
    public CommentResponse comment(Long postId, CommentRequest request, User author) {
        CommunityPost post = published(postId);
        CommunityComment comment = new CommunityComment(); comment.setPost(post); comment.setAuthor(author); comment.setContent(request.content().trim());
        comment = comments.save(comment);
        if (!post.getAuthor().getId().equals(author.getId())) notifications.create(post.getAuthor(), NotificationType.COMMENT,
                "Novo comentário", author.getName() + " comentou em sua publicação.", "/community");
        return commentResponse(comment, author);
    }

    @Transactional(readOnly = true)
    public List<CommentResponse> comments(Long postId, User current) {
        CommunityPost post = published(postId);
        return comments.findByPostAndStatusOrderByCreatedAtAsc(post, ContentStatus.PUBLISHED).stream()
                .map(comment -> commentResponse(comment, current)).toList();
    }

    @Transactional
    public void report(Long postId, ReportRequest request, User reporter) {
        CommunityPost post = publishedForUpdate(postId);
        if (post.getAuthor().getId().equals(reporter.getId())) throw new ArenaProblem.RuleViolation("Você não pode denunciar sua própria publicação.");
        CommunityReport report = reports.findByPostAndReporter(post, reporter).orElseGet(() -> {
            CommunityReport created = new CommunityReport(); created.setPost(post); created.setReporter(reporter); return created;
        });
        report.setReason(request.reason().trim());
        if (report.getStatus() != ReportStatus.PENDING) report.setStatus(ReportStatus.PENDING);
        reports.save(report);
    }

    @Transactional
    public void removeOwnPost(Long postId, User user) {
        CommunityPost post = posts.findById(postId).orElseThrow(() -> new ArenaProblem.NotFound("Publicação não encontrada."));
        if (!post.getAuthor().getId().equals(user.getId())) throw new org.springframework.security.access.AccessDeniedException("A publicação pertence a outro usuário.");
        post.setStatus(ContentStatus.REMOVED); post.touch();
    }

    @Transactional(readOnly = true)
    public Page<ReportResponse> reports(int page, int size) {
        return reports.findAll(PageRequest.of(Math.max(0, page), Math.max(1, Math.min(size, 100)), Sort.by(Sort.Direction.DESC, "createdAt")))
                .map(this::reportResponse);
    }

    @Transactional
    public ReportResponse moderateReport(Long id, ReportModerationRequest request, User moderator) {
        CommunityReport report = reports.findById(id).orElseThrow(() -> new ArenaProblem.NotFound("Denúncia não encontrada."));
        report.setStatus(request.status()); report.setModeratorNote(request.moderatorNote()); report.setModeratedBy(moderator); report.setReviewedAt(Instant.now());
        if (Boolean.TRUE.equals(request.hidePost())) { report.getPost().setStatus(ContentStatus.HIDDEN); report.getPost().touch(); }
        audit.record("COMMUNITY_REPORT_MODERATED", "COMMUNITY_REPORT", report.getId(),
                "Denúncia moderada com status " + report.getStatus());
        return reportResponse(report);
    }

    @Transactional
    public void moderatePost(Long id, ContentStatus status) {
        CommunityPost post = posts.findById(id).orElseThrow(() -> new ArenaProblem.NotFound("Publicação não encontrada."));
        post.setStatus(status); post.touch();
        audit.record("COMMUNITY_POST_MODERATED", "COMMUNITY_POST", post.getId(),
                "Publicação moderada com status " + status);
    }

    private CommunityPost published(Long id) {
        return posts.findByIdAndStatus(id, ContentStatus.PUBLISHED).orElseThrow(() -> new ArenaProblem.NotFound("Publicação não encontrada."));
    }
    private CommunityPost publishedForUpdate(Long id) {
        return posts.findByIdAndStatusForUpdate(id, ContentStatus.PUBLISHED)
                .orElseThrow(() -> new ArenaProblem.NotFound("Publicação não encontrada."));
    }
    private PostResponse response(CommunityPost post, User current) {
        String avatar = profiles.findByUser(post.getAuthor()).map(PlayerProfile::getAvatarUrl).orElse(null);
        return response(post, current, avatar, likes.countByPost(post),
                comments.countByPostAndStatus(post, ContentStatus.PUBLISHED), likes.findByPostAndUser(post, current).isPresent());
    }
    private PostResponse response(CommunityPost post, User current, String avatar, long likeCount,
                                  long commentCount, boolean likedByCurrentUser) {
        return new PostResponse(post.getId(), new CommunityAuthor(post.getAuthor().getId(), post.getAuthor().getName(), avatar),
                post.getAuthor().getName(), avatar, post.getContent(), post.getTopic(), post.getCreatedAt(), post.getUpdatedAt(),
                likeCount, commentCount, likedByCurrentUser, post.getAuthor().getId().equals(current.getId()));
    }
    private CommentResponse commentResponse(CommunityComment value, User current) {
        String avatar = profiles.findByUser(value.getAuthor()).map(PlayerProfile::getAvatarUrl).orElse(null);
        return new CommentResponse(value.getId(), value.getPost().getId(), new CommunityAuthor(value.getAuthor().getId(), value.getAuthor().getName(), avatar),
                value.getContent(), value.getCreatedAt(), value.getAuthor().getId().equals(current.getId()));
    }
    private ReportResponse reportResponse(CommunityReport value) {
        String excerpt = value.getPost().getContent().length() <= 120 ? value.getPost().getContent() : value.getPost().getContent().substring(0, 120) + "…";
        return new ReportResponse(value.getId(), value.getPost().getId(), excerpt, value.getReporter().getId(), value.getReporter().getName(),
                value.getReason(), value.getStatus(), value.getModeratorNote(), value.getCreatedAt(), value.getReviewedAt());
    }
}
