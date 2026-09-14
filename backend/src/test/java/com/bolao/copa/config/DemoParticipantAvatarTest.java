package com.bolao.copa.config;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

class DemoParticipantAvatarTest {
    @Test
    void everySeededParticipantHasADistinctLocalPortrait() {
        var participants = DemoParticipantCatalog.participants();
        assertThat(participants).hasSize(24);
        assertThat(participants).extracting(DemoParticipantCatalog.Participant::avatarUrl)
                .doesNotContainNull().doesNotHaveDuplicates()
                .allMatch(path -> path.matches("/assets/avatars/[a-z]+(?:-[a-z]+)*\\.webp"));
    }
}
