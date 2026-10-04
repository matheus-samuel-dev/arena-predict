package com.bolao.copa.arena.api;

import static com.bolao.copa.arena.api.AdminEngagementDtos.*;
import static com.bolao.copa.arena.api.AdminOperationsDtos.AdminPageResponse;

import com.bolao.copa.arena.service.AdminEngagementService;
import com.bolao.copa.service.CurrentUserService;
import com.bolao.copa.security.DemoAccessPolicy;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminEngagementController {
    private final AdminEngagementService engagement;
    private final CurrentUserService currentUsers;
    private final DemoAccessPolicy demoAccess;
    public AdminEngagementController(AdminEngagementService engagement, CurrentUserService currentUsers, DemoAccessPolicy demoAccess) {
        this.engagement = engagement; this.currentUsers = currentUsers; this.demoAccess = demoAccess;
    }

    @GetMapping("/achievements") public List<AchievementDefinitionResponse> achievements() { return engagement.achievements(); }
    @PostMapping("/achievements") @ResponseStatus(HttpStatus.CREATED)
    public AchievementDefinitionResponse createAchievement(@Valid @RequestBody AchievementDefinitionRequest request) { return engagement.saveAchievement(null, request); }
    @PutMapping("/achievements/{id}") public AchievementDefinitionResponse updateAchievement(@PathVariable Long id, @Valid @RequestBody AchievementDefinitionRequest request) { return engagement.saveAchievement(id, request); }
    @DeleteMapping("/achievements/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void deactivateAchievement(@PathVariable Long id) { engagement.deactivateAchievement(id); }

    @GetMapping("/challenges") public List<ChallengeDefinitionResponse> challenges() { return engagement.challenges(); }
    @PostMapping("/challenges") @ResponseStatus(HttpStatus.CREATED)
    public ChallengeDefinitionResponse createChallenge(@Valid @RequestBody ChallengeDefinitionRequest request) { return engagement.saveChallenge(null, request); }
    @PutMapping("/challenges/{id}") public ChallengeDefinitionResponse updateChallenge(@PathVariable Long id, @Valid @RequestBody ChallengeDefinitionRequest request) { return engagement.saveChallenge(id, request); }
    @DeleteMapping("/challenges/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void deactivateChallenge(@PathVariable Long id) { engagement.deactivateChallenge(id); }

    @GetMapping("/notifications") public AdminPageResponse<AdminNotificationResponse> notifications(@RequestParam(defaultValue = "0") int page,
                                                                                                      @RequestParam(defaultValue = "25") int size,
                                                                                                      @AuthenticationPrincipal UserDetails principal) {
        var result = engagement.notifications(page, size);
        if (demoAccess.isDemoAdmin(currentUsers.from(principal))) result = result.map(value ->
                new AdminNotificationResponse(value.id(), value.userId(), "Usuário #" + value.userId(), value.type(),
                        "Notificação do sistema", "Conteúdo privado protegido na demonstração", null, value.createdAt(), value.read()));
        return AdminPageResponse.from(result);
    }
    @PostMapping("/notifications") @ResponseStatus(HttpStatus.CREATED)
    public NotificationDispatchResponse dispatch(@Valid @RequestBody AdminNotificationRequest request) { return engagement.dispatch(request); }
    @DeleteMapping("/notifications/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void deleteNotification(@PathVariable Long id) { engagement.deleteNotification(id); }
}
