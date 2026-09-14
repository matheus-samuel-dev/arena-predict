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
            participant("sofia.martins@arenapredict.com", "Sofia Martins", "/assets/avatars/sofia-martins.webp", "111011101"),
            participant("gabriel.souza@arenapredict.com", "Gabriel Souza", "/assets/avatars/gabriel-souza.webp", "110111010"),
            participant("larissa.freitas@arenapredict.com", "Larissa Freitas", "/assets/avatars/larissa-freitas.webp", "101101111"),
            participant("pedro.henrique@arenapredict.com", "Pedro Henrique", "/assets/avatars/pedro-henrique.webp", "011111001"),
            participant("isabela.moraes@arenapredict.com", "Isabela Moraes", "/assets/avatars/isabela-moraes.webp", "111001011"),
            participant("bruno.carvalho@arenapredict.com", "Bruno Carvalho", "/assets/avatars/bruno-carvalho.webp", "100111101"),
            participant("julia.azevedo@arenapredict.com", "Júlia Azevedo", "/assets/avatars/julia-azevedo.webp", "011010110"),
            participant("matheus.rocha@arenapredict.com", "Matheus Rocha", "/assets/avatars/matheus-rocha.webp", "110100101"),
            participant("helena.barros@arenapredict.com", "Helena Barros", "/assets/avatars/helena-barros.webp", "101011010"),
            participant("caio.nogueira@arenapredict.com", "Caio Nogueira", "/assets/avatars/caio-nogueira.webp", "010101111"),
            participant("alice.teixeira@arenapredict.com", "Alice Teixeira", "/assets/avatars/alice-teixeira.webp", "001111100"),
            participant("vitor.mendes@arenapredict.com", "Vitor Mendes", "/assets/avatars/vitor-mendes.webp", "100010111"),
            participant("renata.alves@arenapredict.com", "Renata Alves", "/assets/avatars/renata-alves.webp", "010110010"),
            participant("thiago.monteiro@arenapredict.com", "Thiago Monteiro", "/assets/avatars/thiago-monteiro.webp", "001101001"),
            participant("manuela.dias@arenapredict.com", "Manuela Dias", "/assets/avatars/manuela-dias.webp", "101000110"),
            participant("enzo.correia@arenapredict.com", "Enzo Correia", "/assets/avatars/enzo-correia.webp", "010001101")
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
