package com.bolao.copa.arena.api;

import static com.bolao.copa.arena.api.ExperienceDtos.*;

import com.bolao.copa.arena.service.*;
import com.bolao.copa.entity.User;
import com.bolao.copa.service.CurrentUserService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class ExperienceController {
    private final CurrentUserService currentUsers;
    private final PlayerProfileService profiles;
    private final ProgressionService progression;
    private final CommunityService community;
    public ExperienceController(CurrentUserService currentUsers, PlayerProfileService profiles,
                                ProgressionService progression, CommunityService community) {
        this.currentUsers = currentUsers; this.profiles = profiles; this.progression = progression; this.community = community;
    }

    @GetMapping("/profile") public ProfileResponse profile(@AuthenticationPrincipal UserDetails details) { return profiles.get(user(details)); }
    @PatchMapping("/profile") public ProfileResponse updateProfile(@Valid @RequestBody ProfileUpdateRequest request, @AuthenticationPrincipal UserDetails details) { return profiles.update(user(details), request); }
    @PatchMapping("/profile/password") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void password(@Valid @RequestBody PasswordUpdateRequest request, @AuthenticationPrincipal UserDetails details) { profiles.changePassword(user(details), request); }
    @PatchMapping("/profile/preferences") public PreferenceResponse preferences(@Valid @RequestBody PreferenceUpdateRequest request, @AuthenticationPrincipal UserDetails details) { return profiles.preferences(user(details), request); }
    @GetMapping("/achievements") public List<AchievementResponse> achievements(@AuthenticationPrincipal UserDetails details) { return progression.achievements(user(details)); }
    @GetMapping("/challenges") public List<ChallengeResponse> challenges(@AuthenticationPrincipal UserDetails details) { return progression.challenges(user(details)); }

    @GetMapping("/community/posts") public Page<PostResponse> feed(@RequestParam(defaultValue = "0") int page,
                                                                    @RequestParam(defaultValue = "20") int size,
                                                                    @AuthenticationPrincipal UserDetails details) {
        return community.feed(page, size, user(details));
    }
    @PostMapping("/community/posts") @ResponseStatus(HttpStatus.CREATED)
    public PostResponse post(@Valid @RequestBody PostRequest request, @AuthenticationPrincipal UserDetails details) { return community.create(request, user(details)); }
    @PostMapping("/community/posts/{id}/like") public PostResponse like(@PathVariable Long id, @AuthenticationPrincipal UserDetails details) { return community.like(id, user(details)); }
    @PostMapping("/community/posts/{id}/comments") @ResponseStatus(HttpStatus.CREATED)
    public CommentResponse comment(@PathVariable Long id, @Valid @RequestBody CommentRequest request, @AuthenticationPrincipal UserDetails details) { return community.comment(id, request, user(details)); }
    @GetMapping("/community/posts/{id}/comments") public List<CommentResponse> comments(@PathVariable Long id, @AuthenticationPrincipal UserDetails details) { return community.comments(id, user(details)); }
    @PostMapping("/community/posts/{id}/reports") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void report(@PathVariable Long id, @Valid @RequestBody ReportRequest request, @AuthenticationPrincipal UserDetails details) { community.report(id, request, user(details)); }
    @DeleteMapping("/community/posts/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletePost(@PathVariable Long id, @AuthenticationPrincipal UserDetails details) { community.removeOwnPost(id, user(details)); }
    private User user(UserDetails details) { return currentUsers.from(details); }
}
