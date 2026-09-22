package com.bolao.copa.config;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Single source of truth for the deterministic people shown in the public demo.
 * Passwords deliberately do not live here: only the two quick-access identities
 * can authenticate, while the remaining profiles make rankings and community
 * surfaces look populated without increasing the public attack surface.
 */
public final class DemoParticipantCatalog {
    private DemoParticipantCatalog() {
    }

    public record Participant(String email, String name, String avatarUrl, String outcomes) {
    }

    private static final String ARENA_1 = "/assets/avatars/jogador-demo.webp";
    private static final String ARENA_2 = "/assets/avatars/ana-ribeiro.webp";
    private static final String ARENA_3 = "/assets/avatars/beatriz-nunes.webp";
    private static final String ARENA_4 = "/assets/avatars/camila-rocha.webp";
    private static final String ARENA_5 = "/assets/avatars/diego-ferreira.webp";
    private static final String ARENA_6 = "/assets/avatars/lucas-almeida.webp";
    private static final String ARENA_7 = "/assets/avatars/marina-costa.webp";
    private static final String ARENA_8 = "/assets/avatars/rafael-lima.webp";

    private static final List<String> ARENA_AVATARS = List.of(
            ARENA_1, ARENA_2, ARENA_3, ARENA_4, ARENA_5, ARENA_6, ARENA_7, ARENA_8);

    private static final List<Participant> PARTICIPANTS = List.of(
            participant("jogador@arenapredict.com", "Jogador Demo", ARENA_1, "011100111"),
            participant("marina.costa@arenapredict.com", "Marina Costa", ARENA_7, "010010101"),
            participant("rafael.lima@arenapredict.com", "Rafael Lima", ARENA_8, "001000011"),
            participant("beatriz.nunes@arenapredict.com", "Beatriz Nunes", ARENA_3, "111010110"),
            participant("camila.rocha@arenapredict.com", "Camila Rocha", ARENA_4, "110101011"),
            participant("lucas.almeida@arenapredict.com", "Lucas Almeida", ARENA_6, "100100010"),
            participant("ana.ribeiro@arenapredict.com", "Ana Ribeiro", ARENA_2, "111110101"),
            participant("diego.ferreira@arenapredict.com", "Diego Ferreira", ARENA_5, "101010100"),
            participant("sofia.martins@arenapredict.com", "Sofia Martins", ARENA_3, "111011101"),
            participant("gabriel.souza@arenapredict.com", "Gabriel Souza", ARENA_4, "110111010"),
            participant("larissa.freitas@arenapredict.com", "Larissa Freitas", ARENA_5, "101101111"),
            participant("pedro.henrique@arenapredict.com", "Pedro Henrique", ARENA_6, "011111001"),
            participant("isabela.moraes@arenapredict.com", "Isabela Moraes", ARENA_1, "111001011"),
            participant("bruno.carvalho@arenapredict.com", "Bruno Carvalho", ARENA_4, "100111101"),
            participant("julia.azevedo@arenapredict.com", "Júlia Azevedo", ARENA_7, "011010110"),
            participant("matheus.rocha@arenapredict.com", "Matheus Rocha", ARENA_8, "110100101"),
            participant("helena.barros@arenapredict.com", "Helena Barros", ARENA_5, "101011010"),
            participant("caio.nogueira@arenapredict.com", "Caio Nogueira", ARENA_8, "010101111"),
            participant("alice.teixeira@arenapredict.com", "Alice Teixeira", ARENA_2, "001111100"),
            participant("vitor.mendes@arenapredict.com", "Vitor Mendes", ARENA_3, "100010111"),
            participant("renata.alves@arenapredict.com", "Renata Alves", ARENA_2, "010110010"),
            participant("thiago.monteiro@arenapredict.com", "Thiago Monteiro", ARENA_1, "001101001"),
            participant("manuela.dias@arenapredict.com", "Manuela Dias", ARENA_7, "101000110"),
            participant("enzo.correia@arenapredict.com", "Enzo Correia", ARENA_6, "010001101")
    );

    private static final Map<String, Participant> BY_EMAIL = PARTICIPANTS.stream()
            .collect(Collectors.toUnmodifiableMap(
                    value -> value.email().toLowerCase(Locale.ROOT), Function.identity()));

    public static List<Participant> participants() {
        return PARTICIPANTS;
    }

    public static Map<String, Participant> byEmail() {
        return BY_EMAIL;
    }

    public static List<String> arenaAvatars() {
        return ARENA_AVATARS;
    }

    public static boolean isArenaAvatar(String avatarUrl) {
        return avatarUrl != null && ARENA_AVATARS.contains(avatarUrl.trim());
    }

    public static boolean contains(String email) {
        return email != null && BY_EMAIL.containsKey(email.toLowerCase(Locale.ROOT));
    }

    private static Participant participant(String email, String name, String avatarUrl, String outcomes) {
        if (outcomes.length() != 9) throw new IllegalArgumentException("Cada perfil demo precisa de nove resultados.");
        return new Participant(email, name, avatarUrl, outcomes);
    }
}
