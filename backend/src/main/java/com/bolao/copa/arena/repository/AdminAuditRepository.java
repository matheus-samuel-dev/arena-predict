package com.bolao.copa.arena.repository;

import com.bolao.copa.arena.domain.AdminAuditEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface AdminAuditRepository extends JpaRepository<AdminAuditEvent, Long> {
    @Query("select audit from AdminAuditEvent audit where "
            + "lower(audit.action) like lower(concat('%', :search, '%')) or "
            + "lower(audit.actorName) like lower(concat('%', :search, '%')) or "
            + "lower(audit.resourceType) like lower(concat('%', :search, '%')) or "
            + "lower(coalesce(audit.resourceId, '')) like lower(concat('%', :search, '%')) or "
            + "lower(coalesce(audit.summary, '')) like lower(concat('%', :search, '%'))")
    Page<AdminAuditEvent> search(String search, Pageable pageable);
}
