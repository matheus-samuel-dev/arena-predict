package com.bolao.copa.arena.api;

import static com.bolao.copa.arena.api.AdminOperationsDtos.*;

import com.bolao.copa.arena.service.AdminOperationsService;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminOperationsController {
    private final AdminOperationsService operations;

    public AdminOperationsController(AdminOperationsService operations) {
        this.operations = operations;
    }

    @GetMapping("/users")
    public AdminPageResponse<AdminUserResponse> users(@RequestParam(defaultValue = "0") int page,
                                                       @RequestParam(defaultValue = "25") int size,
                                                       @RequestParam(required = false) String search) {
        return AdminPageResponse.from(operations.users(page, size, search));
    }

    @GetMapping("/pools")
    public AdminPageResponse<AdminPoolResponse> pools(@RequestParam(defaultValue = "0") int page,
                                                       @RequestParam(defaultValue = "25") int size,
                                                       @RequestParam(required = false) String search) {
        return AdminPageResponse.from(operations.pools(page, size, search));
    }

    @GetMapping("/championships")
    public AdminPageResponse<AdminChampionshipResponse> championships(@RequestParam(defaultValue = "0") int page,
                                                                       @RequestParam(defaultValue = "25") int size,
                                                                       @RequestParam(required = false) String search) {
        return AdminPageResponse.from(operations.championships(page, size, search));
    }

    @GetMapping("/events")
    public AdminPageResponse<AdminEventResponse> events(@RequestParam(defaultValue = "0") int page,
                                                         @RequestParam(defaultValue = "25") int size,
                                                         @RequestParam(required = false) String search) {
        return AdminPageResponse.from(operations.events(page, size, search));
    }

    @GetMapping("/markets")
    public AdminPageResponse<AdminMarketResponse> markets(@RequestParam(defaultValue = "0") int page,
                                                           @RequestParam(defaultValue = "25") int size,
                                                           @RequestParam(required = false) String search) {
        return AdminPageResponse.from(operations.markets(page, size, search));
    }

    @GetMapping("/scoring-rules")
    public List<ScoringRuleResponse> scoringRules() {
        return operations.scoringRules();
    }

    @GetMapping("/reports")
    public List<OperationalReportResponse> reports() {
        return operations.reports();
    }

    @GetMapping("/audit")
    public AdminPageResponse<AuditEntryResponse> audit(@RequestParam(defaultValue = "0") int page,
                                                        @RequestParam(defaultValue = "25") int size,
                                                        @RequestParam(required = false) String search) {
        return AdminPageResponse.from(operations.audit(page, size, search));
    }

    @GetMapping("/settings")
    public List<PublicSettingResponse> settings() {
        return operations.settings();
    }

    @GetMapping("/moderation")
    public AdminPageResponse<ModerationQueueResponse> moderation(@RequestParam(defaultValue = "0") int page,
                                                                  @RequestParam(defaultValue = "25") int size) {
        return AdminPageResponse.from(operations.moderation(page, size));
    }
}
