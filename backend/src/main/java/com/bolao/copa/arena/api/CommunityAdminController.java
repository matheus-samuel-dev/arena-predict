package com.bolao.copa.arena.api;

import static com.bolao.copa.arena.api.ExperienceDtos.*;
import static com.bolao.copa.arena.api.AdminOperationsDtos.AdminPageResponse;

import com.bolao.copa.arena.service.CommunityService;
import com.bolao.copa.service.CurrentUserService;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/community")
@PreAuthorize("hasRole('ADMIN')")
public class CommunityAdminController {
    private final CommunityService community;
    private final CurrentUserService currentUsers;
    public CommunityAdminController(CommunityService community, CurrentUserService currentUsers) { this.community = community; this.currentUsers = currentUsers; }
    @GetMapping("/reports") public AdminPageResponse<ReportResponse> reports(@RequestParam(defaultValue = "0") int page,
                                                                               @RequestParam(defaultValue = "30") int size) {
        return AdminPageResponse.from(community.reports(page, size));
    }
    @PatchMapping("/reports/{id}") public ReportResponse moderate(@PathVariable Long id, @Valid @RequestBody ReportModerationRequest request,
                                                                  @AuthenticationPrincipal UserDetails details) {
        return community.moderateReport(id, request, currentUsers.from(details));
    }
    @PatchMapping("/posts/{id}") public Map<String, Object> moderatePost(@PathVariable Long id, @Valid @RequestBody PostModerationRequest request) {
        community.moderatePost(id, request.status()); return Map.of("id", id, "status", request.status());
    }
}
