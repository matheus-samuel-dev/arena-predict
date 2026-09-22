package com.bolao.copa.config;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class DemoParticipantAvatarTest {
    @Test
    void everySeededParticipantUsesTheExistingArenaGalleryWithBalancedReuse() {
        var participants = DemoParticipantCatalog.participants();
        assertThat(participants).hasSize(24);
        assertThat(participants).extracting(DemoParticipantCatalog.Participant::avatarUrl)
                .doesNotContainNull()
                .allMatch(DemoParticipantCatalog.arenaAvatars()::contains);

        var usage = participants.stream()
                .map(DemoParticipantCatalog.Participant::avatarUrl)
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
        assertThat(usage).containsOnlyKeys(DemoParticipantCatalog.arenaAvatars().toArray(String[]::new));
        assertThat(usage.values()).containsOnly(3L);
        assertThat(DemoParticipantCatalog.byEmail().get("jogador@arenapredict.com").avatarUrl())
                .isEqualTo("/assets/avatars/jogador-demo.webp");
    }

    @Test
    void onlyGalleryPortraitsAreRecognizedAsArenaChoices() {
        assertThat(DemoParticipantCatalog.isArenaAvatar("/assets/avatars/sofia-martins.webp")).isFalse();
        assertThat(DemoParticipantCatalog.isArenaAvatar("/assets/avatars/jogador-demo.webp")).isTrue();
        assertThat(DemoParticipantCatalog.isArenaAvatar("/assets/avatars/ana-ribeiro.webp")).isTrue();
    }
}
