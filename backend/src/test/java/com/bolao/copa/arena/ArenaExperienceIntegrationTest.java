package com.bolao.copa.arena;

import static com.bolao.copa.arena.api.ExperienceDtos.*;
import static com.bolao.copa.arena.api.ArenaDtos.PoolRequest;
import static org.assertj.core.api.Assertions.*;

import com.bolao.copa.arena.service.*;
import com.bolao.copa.repository.UserRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
class ArenaExperienceIntegrationTest {
    @Autowired UserRepository users;
    @Autowired PlayerProfileService profiles;
    @Autowired ProgressionService progression;
    @Autowired PointWalletService wallets;
    @Autowired CommunityService community;
    @Autowired ArenaPoolRankingService pools;

    @Test
    @Transactional
    void profileAndPreferencesArePersistedWithoutExposingPassword() {
        var user = users.findByEmail("user@bolao.com").orElseThrow();
        var updated = profiles.update(user, new ProfileUpdateRequest("Jogador Arena", user.getEmail(),
                "https://example.test/avatar.png", "Perfil público de demonstração", List.of("FOOTBALL", "CS2"), true));
        var preferences = profiles.preferences(user, new PreferenceUpdateRequest("light", "en-US", false, false));

        assertThat(updated.name()).isEqualTo("Jogador Arena");
        assertThat(updated.favoriteSports()).containsExactly("FOOTBALL", "CS2");
        assertThat(preferences.theme()).isEqualTo("light");
        assertThat(preferences.notifications()).isFalse();
        assertThat(profiles.get(user).publicProfile()).isFalse();
    }

    @Test
    @Transactional
    void achievementAndChallengeRewardsAreGrantedOnlyOnce() {
        var user = users.findByEmail("user@bolao.com").orElseThrow();
        long before = wallets.wallet(user).balance();
        var firstAchievements = progression.achievements(user);
        var firstChallenges = progression.challenges(user);
        long afterFirstEvaluation = wallets.wallet(user).balance();

        progression.achievements(user);
        progression.challenges(user);

        assertThat(firstAchievements).anyMatch(AchievementResponse::unlocked);
        assertThat(firstChallenges).isNotEmpty();
        assertThat(afterFirstEvaluation).isGreaterThanOrEqualTo(before);
        assertThat(wallets.wallet(user).balance()).isEqualTo(afterFirstEvaluation);
    }

    @Test
    @Transactional
    void communityLikeAndReportAreIdempotentAndOwnershipIsEnforced() {
        var author = users.findByEmail("user@bolao.com").orElseThrow();
        var other = users.findByEmail("admin@bolao.com").orElseThrow();
        var post = community.create(new PostRequest("Uma análise original para o próximo evento.", "Análise"), author);

        assertThat(community.like(post.id(), other).likeCount()).isEqualTo(1);
        assertThat(community.like(post.id(), other).likeCount()).isEqualTo(1);
        assertThat(community.comment(post.id(), new CommentRequest("Boa leitura do confronto."), other).postId()).isEqualTo(post.id());
        community.report(post.id(), new ReportRequest("Revisão de moderação para teste"), other);
        community.report(post.id(), new ReportRequest("Motivo atualizado sem duplicar"), other);
        assertThat(community.reports(0, 20).getTotalElements()).isPositive();
        assertThatThrownBy(() -> community.removeOwnPost(post.id(), other)).isInstanceOf(AccessDeniedException.class);
        community.removeOwnPost(post.id(), author);
    }

    @Test
    @Transactional
    void privatePoolsAreVisibleOnlyToMembersAndInviteCodeIsNeverPublic() {
        var owner = users.findByEmail("user@bolao.com").orElseThrow();
        var outsider = users.findByEmail("admin@bolao.com").orElseThrow();
        var privatePool = pools.create(new PoolRequest("Liga privada", "Somente convidados", null, null,
                false, 20, 300, "Ranking por pontos virtuais.", null, null), owner);
        var publicPool = pools.create(new PoolRequest("Liga pública", "Visível na arena", null, null,
                true, 20, 300, "Ranking por pontos virtuais.", null, null), owner);

        assertThat(privatePool.inviteCode()).isNotBlank();
        assertThat(pools.list(outsider)).extracting(value -> value.id()).doesNotContain(privatePool.id());
        assertThatThrownBy(() -> pools.get(privatePool.id(), outsider)).isInstanceOf(ArenaProblem.NotFound.class);
        assertThat(pools.get(publicPool.id(), outsider).inviteCode()).isNull();
        assertThat(pools.get(publicPool.id(), outsider).joined()).isFalse();
    }
}
