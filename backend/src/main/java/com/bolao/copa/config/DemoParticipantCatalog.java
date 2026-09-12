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

    private static final List<Participant> PARTICIPANTS = List.of(
            participant("jogador@arenapredict.com", "Jogador Demo", "/assets/avatars/jogador-demo.webp", "011100111"),
            participant("marina.costa@arenapredict.com", "Marina Costa", "/assets/avatars/marina-costa.webp", "010010101"),
            participant("rafael.lima@arenapredict.com", "Rafael Lima", "/assets/avatars/rafael-lima.webp", "001000011"),
            participant("beatriz.nunes@arenapredict.com", "Beatriz Nunes", "/assets/avatars/beatriz-nunes.webp", "111010110"),
            participant("camila.rocha@arenapredict.com", "Camila Rocha", "/assets/avatars/camila-rocha.webp", "110101011"),
            participant("lucas.almeida@arenapredict.com", "Lucas Almeida", "/assets/avatars/lucas-almeida.webp", "100100010"),
            participant("ana.ribeiro@arenapredict.com", "Ana Ribeiro", "/assets/avatars/ana-ribeiro.webp", "111110101"),
            participant("diego.ferreira@arenapredict.com", "Diego Ferreira", "/assets/avatars/diego-ferreira.webp", "101010100"),
            participant("sofia.martins@arenapredict.com", "Sofia Martins", null, "111011101"),
            participant("gabriel.souza@arenapredict.com", "Gabriel Souza", null, "110111010"),
            participant("larissa.freitas@arenapredict.com", "Larissa Freitas", null, "101101111"),
            participant("pedro.henrique@arenapredict.com", "Pedro Henrique", null, "011111001"),
            participant("isabela.moraes@arenapredict.com", "Isabela Moraes", null, "111001011"),
            participant("bruno.carvalho@arenapredict.com", "Bruno Carvalho", null, "100111101"),
            participant("julia.azevedo@arenapredict.com", "Júlia Azevedo", null, "011010110"),
            participant("matheus.rocha@arenapredict.com", "Matheus Rocha", null, "110100101"),
            participant("helena.barros@arenapredict.com", "Helena Barros", null, "101011010"),
            participant("caio.nogueira@arenapredict.com", "Caio Nogueira", null, "010101111"),
            participant("alice.teixeira@arenapredict.com", "Alice Teixeira", null, "001111100"),
            participant("vitor.mendes@arenapredict.com", "Vitor Mendes", null, "100010111"),
            participant("renata.alves@arenapredict.com", "Renata Alves", null, "010110010"),
            participant("thiago.monteiro@arenapredict.com", "Thiago Monteiro", null, "001101001"),
            participant("manuela.dias@arenapredict.com", "Manuela Dias", null, "101000110"),
            participant("enzo.correia@arenapredict.com", "Enzo Correia", null, "010001101")
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

    public static boolean contains(String email) {
        return email != null && BY_EMAIL.containsKey(email.toLowerCase(Locale.ROOT));
    }

    private static Participant participant(String email, String name, String avatarUrl, String outcomes) {
        if (outcomes.length() != 9) throw new IllegalArgumentException("Cada perfil demo precisa de nove resultados.");
        return new Participant(email, name, avatarUrl, outcomes);
    }
}
