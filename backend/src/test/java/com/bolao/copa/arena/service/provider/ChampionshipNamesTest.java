package com.bolao.copa.arena.service.provider;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ChampionshipNamesTest {
    @ParameterizedTest
    @CsvSource({
        "Playoffs,Thunderpick World Championship,2026,Thunderpick World Championship · 2026",
        "Playoffs,ESL Pro League,Season 24 2026,ESL Pro League · Season 24 2026",
        "Playoffs,ESL Challenger League,Oceania Cup #3 season 52 2026,Oceania Cup #3 season 52 2026",
        "Playoffs,Intel Extreme Masters,IEM Cologne 2026,IEM Cologne 2026",
        "Playoffs,League,,League",
        ",League,,League",
        "2026,,2026,2026"
    })
    void preservesDescriptiveNamesAndQualifiesEditionLabels(String name, String league, String series, String expected) {
        assertThat(ChampionshipNames.resolve(name, league, series)).isEqualTo(expected);
    }
}
