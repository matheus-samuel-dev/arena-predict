package com.bolao.copa.arena.service;

import com.bolao.copa.arena.domain.*;
import com.bolao.copa.arena.domain.ArenaEnums.*;
import com.bolao.copa.arena.api.ArenaDtos.ResultField;
import com.bolao.copa.arena.service.MarketDefinitionCatalog.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.stereotype.Component;

/** Pure, deterministic settlement calculation. No writes and no provider/network calls. */
@Component
public class MarketSettlementEngine {
    public record Outcome(Set<String> winningKeys, boolean refund) {
        public static Outcome winner(String key) { return new Outcome(Set.of(key), false); }
        public static Outcome voided() { return new Outcome(Set.of(), true); }
    }
    private final ObjectMapper json;
    public MarketSettlementEngine(ObjectMapper json) { this.json = json; }
    public Map<String, String> data(ArenaEvent event) {
        if (event.getResultData() == null || event.getResultData().isBlank()) return Map.of();
        try { return json.readValue(event.getResultData(), new TypeReference<Map<String,String>>() {}); }
        catch (Exception error) { throw new IllegalStateException("Resultado persistido inválido", error); }
    }
    public void storeData(ArenaEvent event, Map<String, String> supplied, List<ResultField> schema) {
        if (supplied == null) return;
        Map<String, ResultField> allowed = new HashMap<>(); schema.forEach(field -> allowed.put(field.key(), field));
        Map<String, String> values = new TreeMap<>(data(event));
        for (var input : supplied.entrySet()) {
            ResultField field = allowed.get(input.getKey());
            if (field == null) throw new ArenaProblem.RuleViolation("Estatística não suportada para os mercados deste evento: " + input.getKey());
            String value = input.getValue() == null ? "" : input.getValue().trim();
            if (value.isEmpty()) { values.remove(input.getKey()); continue; }
            if (value.length() > 80) throw new ArenaProblem.RuleViolation("Valor muito longo: " + field.label());
            if (field.type().equals("number")) number(value, field.label());
            else if (field.options().stream().noneMatch(c -> c.value().equals(value))) throw new ArenaProblem.RuleViolation("Seleção inválida: " + field.label());
            values.put(input.getKey(), value);
        }
        try { event.setResultData(json.writeValueAsString(values)); }
        catch (Exception error) { throw new IllegalStateException(error); }
    }
    public void validateEvent(ArenaEvent event, List<EventParticipant> participants, boolean finished) {
        Map<String, String> values = data(event);
        String sport = event.getChampionship().getSport().getCode();
        if (sport.equals("MOTORSPORT")) {
            if (finished && (participants.size() < 2 || participants.stream().anyMatch(p -> p.getPosition() == null)
                    || participants.stream().map(EventParticipant::getPosition).distinct().count() != participants.size()
                    || participants.stream().anyMatch(p -> p.getPosition() < 1 || p.getPosition() > participants.size())))
                throw new ArenaProblem.RuleViolation("Informe uma classificação completa, sem repetir posições, de 1 até o número de pilotos.");
            return;
        }
        if (event.getHomeScore() == null || event.getAwayScore() == null) throw new ArenaProblem.RuleViolation("Informe o placar dos dois participantes.");
        int home = event.getHomeScore(), away = event.getAwayScore();
        if (home < 0 || away < 0 || home > 1000 || away > 1000) throw new ArenaProblem.RuleViolation("Placar fora dos limites permitidos.");
        Set<String> seriesSports = Set.of("CS2", "VALORANT", "LEAGUE_OF_LEGENDS", "DOTA2", "TENNIS", "VOLLEYBALL");
        if (finished && seriesSports.contains(sport)) {
            int bestOf = sport.equals("VOLLEYBALL") ? 5 : sport.equals("TENNIS") ? Math.max(3, event.getBestOf()) : event.getBestOf();
            int target = bestOf / 2 + 1;
            if (Math.max(home, away) != target || Math.min(home, away) >= target)
                throw new ArenaProblem.RuleViolation("Placar da série inválido para melhor de " + bestOf + ": um participante precisa vencer " + target + " mapas/sets.");
        }
        if (finished && sport.equals("BASKETBALL") && home == away)
            throw new ArenaProblem.RuleViolation("O basquete exige um vencedor após a prorrogação.");
        if (Set.of("FOOTBALL", "BASKETBALL", "AMERICAN_FOOTBALL").contains(sport)) {
            checkContained(values, "firstHalf", home, away, "O placar no intervalo não pode superar o final.");
            checkContained(values, "quarter1", home, away, "O primeiro quarto não pode superar o placar final.");
            if (hasPair(values, "firstHalf")) checkContained(values, "quarter1", n(values,"firstHalfHome"), n(values,"firstHalfAway"), "O primeiro quarto não pode superar o placar no intervalo.");
        }
        if (hasPair(values, "set1")) {
            int h = n(values,"set1Home"), a = n(values,"set1Away");
            if (h == a || (sport.equals("TENNIS") && !validTennisSet(h,a)) || (sport.equals("VOLLEYBALL") && !validVolleyballSet(h,a,25)))
                throw new ArenaProblem.RuleViolation("Placar inválido no primeiro set.");
            if ((h > a && home == 0) || (a > h && away == 0)) throw new ArenaProblem.RuleViolation("O vencedor do primeiro set precisa ter ao menos um set na série.");
            checkContained(values, "set1", valueOrMax(values, "gamesHome", "pointsHome"), valueOrMax(values, "gamesAway", "pointsAway"), "O primeiro set não pode superar os games/pontos totais.");
        }
        if (hasPair(values, "map1")) {
            int h = n(values,"map1Home"), a = n(values,"map1Away");
            boolean shooter = sport.equals("CS2") || sport.equals("VALORANT");
            if (h == a || (shooter && !validShooterMap(sport,h,a))
                    || (!shooter && (Math.max(h,a) != 1 || Math.min(h,a) != 0)))
                throw new ArenaProblem.RuleViolation("Placar inválido no primeiro mapa/jogo.");
            if ((h > a && home == 0) || (a > h && away == 0)) throw new ArenaProblem.RuleViolation("O vencedor do primeiro mapa precisa ter ao menos um mapa na série.");
            if (values.containsKey("pistol1") && ((values.get("pistol1").equals("HOME") && h == 0) || (values.get("pistol1").equals("AWAY") && a == 0)))
                throw new ArenaProblem.RuleViolation("O vencedor do pistol precisa ter ao menos um round no primeiro mapa.");
            checkContained(values, "map1Half1", h, a, "O primeiro half não pode superar o total de rounds do mapa.");
            checkContained(values, "map1Half2", h, a, "O segundo half não pode superar o total de rounds do mapa.");
            if (hasPair(values, "map1Half1") && n(values,"map1Half1Home") + n(values,"map1Half1Away") != 12)
                throw new ArenaProblem.RuleViolation("O primeiro half do CS2 deve ter exatamente 12 rounds.");
            if (hasPair(values, "map1Half1") && hasPair(values, "map1Half2")) {
                int hh = n(values,"map1Half1Home") + n(values,"map1Half2Home"), aa = n(values,"map1Half1Away") + n(values,"map1Half2Away");
                if (hh > h || aa > a || (Math.max(h,a) == 13 && (hh != h || aa != a)) || (Math.max(h,a) > 13 && (hh != 12 || aa != 12)))
                    throw new ArenaProblem.RuleViolation("Os halves precisam somar o placar regulamentar; prorrogação começa em 12 × 12.");
            }
        }
        if (sport.equals("TENNIS")) {
            if (hasPair(values,"set1") && Math.min(n(values,"set1Home"),n(values,"set1Away")) == 6 && "NO".equals(values.get("tieBreak")))
                throw new ArenaProblem.RuleViolation("Um set encerrado em 7 × 6 exige tie-break.");
            if (finished && hasPair(values,"games")) validateTennisGames(home,away,values);
        }
    }
    public Outcome evaluate(Definition definition, ArenaEvent event, List<EventParticipant> participants) {
        Map<String, String> values = data(event);
        for (ResultField field : definition.fields()) if (!values.containsKey(field.key())) throw new ArenaProblem.RuleViolation("Informe " + field.label() + " para liquidar “" + definition.name() + "”.");
        if (definition.strategy() == Strategy.SELECTION) return Outcome.winner(values.get(definition.metric()));
        if (definition.strategy() == Strategy.RANK) {
            Set<String> keys = new TreeSet<>();
            for (EventParticipant p : participants) {
                if (p.getPosition() == null) throw new ArenaProblem.RuleViolation("Informe a classificação final para liquidar a corrida.");
                if (p.getPosition() <= definition.line().intValue()) keys.add(p.getCompetitor().getCode());
            }
            return new Outcome(keys, false);
        }
        if (definition.strategy() == Strategy.HEAD_TO_HEAD) {
            Set<String> keys = definition.options().stream().map(Choice::key).collect(java.util.stream.Collectors.toSet());
            List<EventParticipant> selected = participants.stream().filter(p -> keys.contains(p.getCompetitor().getCode())).toList();
            if (selected.size() != 2 || selected.stream().anyMatch(p -> p.getPosition() == null)) throw new ArenaProblem.RuleViolation("Informe as posições dos dois pilotos do confronto.");
            return Outcome.winner(selected.stream().min(Comparator.comparing(EventParticipant::getPosition)).orElseThrow().getCompetitor().getCode());
        }
        int home = definition.metric().equals("score") ? event.getHomeScore() : n(values, definition.metric() + "Home");
        int away = definition.metric().equals("score") ? event.getAwayScore() : n(values, definition.metric() + "Away");
        String winner = home > away ? "HOME" : home < away ? "AWAY" : "DRAW";
        return switch (definition.strategy()) {
            case WINNER -> definition.options().stream().anyMatch(c -> c.key().equals(winner)) ? Outcome.winner(winner) : Outcome.voided();
            case DOUBLE_CHANCE -> new Outcome(winner.equals("HOME") ? Set.of("HOME_DRAW", "HOME_AWAY") : winner.equals("AWAY") ? Set.of("HOME_AWAY", "DRAW_AWAY") : Set.of("HOME_DRAW", "DRAW_AWAY"), false);
            case BOTH_SCORE -> Outcome.winner(home > 0 && away > 0 ? "YES" : "NO");
            case TOTAL, HOME_TOTAL -> compared(BigDecimal.valueOf(home + (definition.strategy() == Strategy.TOTAL ? away : 0)).compareTo(definition.line()), "OVER", "UNDER");
            case HANDICAP -> compared(BigDecimal.valueOf(home).add(definition.line()).compareTo(BigDecimal.valueOf(away)), "HOME", "AWAY");
            case EXACT_SCORE -> {
                String key = home + "_" + away;
                yield Outcome.winner(definition.options().stream().anyMatch(c -> c.key().equals(key)) ? key : "OTHER");
            }
            case MARGIN -> Outcome.winner(winner.equals("DRAW") ? "DRAW" : winner + (Math.abs(home-away) <= definition.line().intValue() ? "_SMALL" : "_LARGE"));
            default -> throw new IllegalStateException("Estratégia não suportada");
        };
    }
    private Outcome compared(int comparison, String above, String below) { return comparison == 0 ? Outcome.voided() : Outcome.winner(comparison > 0 ? above : below); }
    private int number(String value, String label) { try { int number = Integer.parseInt(value); if (number < 0 || number > 10000) throw new NumberFormatException(); return number; } catch (NumberFormatException error) { throw new ArenaProblem.RuleViolation("Informe um número inteiro entre 0 e 10000: " + label); } }
    private int n(Map<String,String> values, String key) { return number(values.get(key), key); }
    private boolean hasPair(Map<String,String> values, String prefix) { return values.containsKey(prefix + "Home") && values.containsKey(prefix + "Away"); }
    private void checkContained(Map<String,String> values, String prefix, int home, int away, String error) { if (hasPair(values,prefix) && (n(values,prefix + "Home") > home || n(values,prefix + "Away") > away)) throw new ArenaProblem.RuleViolation(error); }
    private int valueOrMax(Map<String,String> values, String first, String second) { return values.containsKey(first) ? n(values,first) : values.containsKey(second) ? n(values,second) : Integer.MAX_VALUE; }
    private boolean validTennisSet(int h, int a) { int max = Math.max(h,a), min = Math.min(h,a); return (max == 6 && min <= 4) || (max == 7 && (min == 5 || min == 6)); }
    private boolean validVolleyballSet(int h, int a, int target) { int max = Math.max(h,a), min = Math.min(h,a); return max == target && min <= target-2 || max > target && max-min == 2; }
    private boolean validShooterMap(String sport, int h, int a) {
        int max=Math.max(h,a), min=Math.min(h,a);
        if (max == 13) return min <= 11;
        if (sport.equals("VALORANT")) return max >= 14 && max-min == 2;
        // CS2 uses MR12 regulation followed by repeated six-round MR3 overtimes.
        return max >= 16 && (max-16)%3 == 0 && min >= max-4 && min <= max-2;
    }
    private void validateTennisGames(int homeSets,int awaySets,Map<String,String> values) {
        int homeGames=n(values,"gamesHome"), awayGames=n(values,"gamesAway");
        boolean hadTieBreak=false;
        if (hasPair(values,"set1")) {
            int h=n(values,"set1Home"), a=n(values,"set1Away");
            homeSets-=h>a?1:0; awaySets-=a>h?1:0;
            homeGames-=h; awayGames-=a;
            hadTieBreak=Math.min(h,a)==6;
        }
        if (!possibleTennisGames(homeSets,awaySets,homeGames,awayGames,hadTieBreak,values.get("tieBreak"),new HashSet<>()))
            throw new ArenaProblem.RuleViolation("Games totais, sets e tie-break não correspondem a uma partida completa com tie-break em 6 × 6.");
    }
    private record TennisState(int homeSets,int awaySets,int homeGames,int awayGames,boolean tieBreak) { }
    private boolean possibleTennisGames(int hs,int as,int hg,int ag,boolean tieBreak,String expected,Set<TennisState> visited) {
        if (hs<0 || as<0 || hg<0 || ag<0 || hg>7*(hs+as) || ag>7*(hs+as) || (tieBreak && "NO".equals(expected))) return false;
        if (hs+as == 0) return hg == 0 && ag == 0 && (expected == null || tieBreak == expected.equals("YES"));
        if (!visited.add(new TennisState(hs,as,hg,ag,tieBreak))) return false;
        // Standard completed sets only. A tie-break contributes one game (7-6).
        for (int losingGames=0;losingGames<=6;losingGames++) {
            int winningGames=losingGames<5?6:7;
            boolean nextTieBreak=tieBreak || losingGames==6;
            if (hs>0 && possibleTennisGames(hs-1,as,hg-winningGames,ag-losingGames,nextTieBreak,expected,visited)) return true;
            if (as>0 && possibleTennisGames(hs,as-1,hg-losingGames,ag-winningGames,nextTieBreak,expected,visited)) return true;
        }
        return false;
    }
}
