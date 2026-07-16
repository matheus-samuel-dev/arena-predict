package com.bolao.copa.arena.domain;

import com.bolao.copa.arena.domain.ArenaEnums.ReportStatus;
import com.bolao.copa.entity.User;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "arena_community_reports", uniqueConstraints = @UniqueConstraint(columnNames = {"post_id", "reporter_id"}))
public class CommunityReport {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "post_id") private CommunityPost post;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "reporter_id") private User reporter;
    @Column(nullable = false, length = 300) private String reason;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private ReportStatus status = ReportStatus.PENDING;
    @Column(length = 500) private String moderatorNote;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "moderated_by_id") private User moderatedBy;
    @Column(nullable = false) private Instant createdAt = Instant.now();
    private Instant reviewedAt;

    public Long getId() { return id; }
    public CommunityPost getPost() { return post; } public void setPost(CommunityPost post) { this.post = post; }
    public User getReporter() { return reporter; } public void setReporter(User reporter) { this.reporter = reporter; }
    public String getReason() { return reason; } public void setReason(String reason) { this.reason = reason; }
    public ReportStatus getStatus() { return status; } public void setStatus(ReportStatus status) { this.status = status; }
    public String getModeratorNote() { return moderatorNote; } public void setModeratorNote(String moderatorNote) { this.moderatorNote = moderatorNote; }
    public User getModeratedBy() { return moderatedBy; } public void setModeratedBy(User moderatedBy) { this.moderatedBy = moderatedBy; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getReviewedAt() { return reviewedAt; } public void setReviewedAt(Instant reviewedAt) { this.reviewedAt = reviewedAt; }
}
