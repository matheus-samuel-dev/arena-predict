package com.bolao.copa.arena.service;

import com.bolao.copa.arena.api.ArenaDtos.*;
import com.bolao.copa.arena.domain.*;
import com.bolao.copa.arena.domain.ArenaEnums.*;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.stereotype.Component;

/** Versioned product definitions. Sport differences live here, never in client components. */
@Component
public class MarketDefinitionCatalog {
    private final com.fasterxml.jackson.databind.ObjectMapper json;
    public MarketDefinitionCatalog(com.fasterxml.jackson.databind.ObjectMapper json) { this.json = json; }
    public void snapshot(PredictionMarket market, Definition definition) {
        try { market.setDefinitionData(json.writeValueAsString(definition)); }
        catch (Exception error) { throw new IllegalStateException(error); }
    }
    public enum Strategy { WINNER, DOUBLE_CHANCE, TOTAL, HOME_TOTAL, HANDICAP, BOTH_SCORE, EXACT_SCORE, MARGIN, SELECTION, RANK, HEAD_TO_HEAD }
    public record Choice(String key, String label, BigDecimal multiplier) { }
    public record Definition(String code, String name, String category, Strategy strategy, String metric,
                             BigDecimal line, MarketTimingMode timingMode, List<Choice> options,
                             List<ResultField> fields, String settlementDescription) { }
    private static final MarketTimingMode PRE = MarketTimingMode.PRE_MATCH_ONLY;
    private static final MarketTimingMode LIVE = MarketTimingMode.LIVE_ENABLED;

    public List<Definition> definitions(ArenaEvent event, List<EventParticipant> participants) {
        String home = event.getHomeCompetitor() == null ? "Participante 1" : event.getHomeCompetitor().getName();
        String away = event.getAwayCompetitor() == null ? "Participante 2" : event.getAwayCompetitor().getName();
        Builder b = new Builder(home, away, event.getChampionship().getSport().getCode());
        switch (event.getChampionship().getSport().getCode()) {
            case "FOOTBALL" -> {
                b.winner("WINNER", "Resultado da partida · 90 minutos", "Principais", "score", true, PRE);
                b.winner("LIVE_RESULT", "Resultado ao vivo · 90 minutos", "Principais", "score", true, MarketTimingMode.LIVE_ONLY);
                b.add("DOUBLE_CHANCE", "Dupla possibilidade", "Principais", Strategy.DOUBLE_CHANCE, "score", null, PRE,
                        List.of(choice("HOME_DRAW", home + " ou empate", "1.35"), choice("HOME_AWAY", home + " ou " + away, "1.30"), choice("DRAW_AWAY", "Empate ou " + away, "1.50")));
                b.total("TOTAL_GOALS", "Total de gols · 2,5", "Gols", "score", "2.5", LIVE);
                b.add("BOTH_SCORE", "Ambas as equipes marcam", "Gols", Strategy.BOTH_SCORE, "score", null, LIVE, yesNo());
                b.exact("CORRECT_SCORE", "Placar correto · 90 minutos", "Gols", 4, false);
                b.winner("FIRST_HALF_WINNER", "Resultado do primeiro tempo", "1º tempo", "firstHalf", true, PRE);
                b.total("TOTAL_CORNERS", "Total de escanteios · 9,5", "Escanteios", "corners", "9.5", LIVE);
                b.total("TOTAL_CARDS", "Total de cartões · 4,5", "Cartões", "cards", "4.5", LIVE);
            }
            case "BASKETBALL", "AMERICAN_FOOTBALL" -> {
                boolean basketball = event.getChampionship().getSport().getCode().equals("BASKETBALL");
                b.winner("WINNER", "Vencedor da partida · inclui prorrogação", "Principais", "score", !basketball, LIVE);
                b.handicap("POINT_HANDICAP", "Handicap de pontos · " + home + (basketball ? " −4,5" : " −3,5"), "Handicap", "score", basketball ? "-4.5" : "-3.5", LIVE);
                b.total("TOTAL_POINTS", "Total de pontos · " + (basketball ? "215,5" : "44,5"), "Pontos", "score", basketball ? "215.5" : "44.5", LIVE);
                b.winner("FIRST_HALF_WINNER", "Resultado do primeiro tempo", "Períodos", "firstHalf", true, PRE);
                b.winner("FIRST_QUARTER_WINNER", "Resultado do primeiro quarto", "Quartos", "quarter1", true, PRE);
                b.add("HOME_TOTAL", home + " · total de pontos " + (basketball ? "108,5" : "23,5"), "Times", Strategy.HOME_TOTAL, "score", basketball ? "108.5" : "23.5", LIVE, overUnder());
                b.add("WIN_MARGIN", "Margem de vitória", "Principais", Strategy.MARGIN, "score", basketball ? "10" : "7", PRE,
                        b.margin(basketball ? 10 : 7, !basketball));
                b.total("FIRST_HALF_TOTAL", "Pontos no primeiro tempo · " + (basketball ? "105,5" : "21,5"), "Períodos", "firstHalf", basketball ? "105.5" : "21.5", PRE);
            }
            case "TENNIS", "VOLLEYBALL" -> {
                boolean tennis = event.getChampionship().getSport().getCode().equals("TENNIS");
                int bestOf = tennis ? Math.max(3, event.getBestOf()) : 5;
                b.winner("MATCH_WINNER", "Vencedor da partida", "Principais", "score", false, LIVE);
                b.winner("FIRST_SET_WINNER", "Vencedor do primeiro set", "Sets", "set1", false, PRE);
                b.total("TOTAL_SETS", "Total de sets · " + (bestOf == 5 ? "3,5" : "2,5"), "Sets", "score", bestOf == 5 ? "3.5" : "2.5", LIVE);
                b.handicap("SET_HANDICAP", "Handicap de sets · " + home + " −1,5", "Handicap", "score", "-1.5", PRE);
                b.exact("SET_SCORE", "Placar da partida em sets", "Sets", bestOf / 2 + 1, true);
                b.total(tennis ? "TOTAL_GAMES" : "TOTAL_POINTS", tennis ? "Total de games · 22,5" : "Total de pontos · 179,5", tennis ? "Games" : "Pontos", tennis ? "games" : "points", tennis ? "22.5" : "179.5", LIVE);
                if (tennis) {
                    b.handicap("GAME_HANDICAP", "Handicap de games · " + home + " −2,5", "Handicap", "games", "-2.5", LIVE);
                    b.selection("TIEBREAK", "Haverá tie-break", "Games", "tieBreak", yesNo(), PRE);
                }
            }
            case "CS2", "VALORANT", "LEAGUE_OF_LEGENDS", "DOTA2" -> {
                String sport = event.getChampionship().getSport().getCode();
                boolean shooter = sport.equals("CS2") || sport.equals("VALORANT");
                b.winner("SERIES_WINNER", "Vencedor da série", "Série", "score", false, LIVE);
                b.winner("MAP1_WINNER", "Vencedor do primeiro " + (shooter ? "mapa" : "jogo"), "Mapas", "map1", false, PRE);
                if (event.getBestOf() > 1) {
                    String line = event.getBestOf() == 5 ? "4.5" : "2.5";
                    b.total("TOTAL_MAPS", "Total de mapas · " + line.replace('.', ','), "Série", "score", line, LIVE);
                    b.handicap("MAP_HANDICAP", "Handicap de mapas · " + home + " −1,5", "Handicap", "score", "-1.5", LIVE);
                    b.exact("SERIES_SCORE", "Placar correto da série", "Série", event.getBestOf() / 2 + 1, true);
                }
                if (shooter) {
                    b.total("MAP1_TOTAL_ROUNDS", "Total de rounds · primeiro mapa · 21,5", "Rounds", "map1", "21.5", PRE);
                    b.handicap("MAP1_ROUND_HANDICAP", "Handicap de rounds · mapa 1 · " + home + " −2,5", "Handicap", "map1", "-2.5", PRE);
                    b.selection("PISTOL1", "Vencedor do pistol · round 1 do mapa 1", "Pistol", "pistol1", b.sides(false), PRE);
                    if (sport.equals("CS2")) {
                        b.winner("MAP1_HALF1", "Vencedor do primeiro half · mapa 1", "Rounds", "map1Half1", true, PRE);
                        b.winner("MAP1_HALF2", "Vencedor do segundo half · mapa 1", "Rounds", "map1Half2", true, PRE);
                    }
                } else {
                    b.selection("FIRST_BLOOD", "Primeiro abate · jogo 1", "Objetivos", "firstBlood", b.sidesWithNone(), PRE);
                    if (sport.equals("LEAGUE_OF_LEGENDS")) {
                        b.selection("FIRST_TOWER", "Primeira torre · jogo 1", "Objetivos", "firstTower", b.sidesWithNone(), PRE);
                        b.selection("FIRST_DRAGON", "Primeiro dragão · jogo 1", "Objetivos", "firstDragon", b.sidesWithNone(), PRE);
                        b.selection("FIRST_BARON", "Primeiro Barão · jogo 1", "Objetivos", "firstBaron", b.sidesWithNone(), PRE);
                    } else b.selection("FIRST_ROSHAN", "Primeiro abate do Roshan · jogo 1", "Objetivos", "firstRoshan", b.sidesWithNone(), PRE);
                    b.total("MAP1_TOTAL_KILLS", "Total de abates · jogo 1 · " + (sport.equals("DOTA2") ? "45,5" : "26,5"), "Abates", "kills", sport.equals("DOTA2") ? "45.5" : "26.5", PRE);
                }
            }
            case "MOTORSPORT" -> {
                if (participants.size() < 2) return List.of();
                List<Choice> drivers = participants.stream().map(p -> choice(p.getCompetitor().getCode(), p.getCompetitor().getName(), "3.20")).toList();
                b.add("RACE_WINNER", "Vencedor da corrida", "Corrida", Strategy.RANK, "classification", "1", PRE, drivers);
                b.add("TOP3", "Terminar no Top 3", "Classificação", Strategy.RANK, "classification", "3", PRE, drivers.stream().map(c -> choice(c.key(), c.label(), "1.65")).toList());
                if (participants.size() > 5) b.add("TOP5", "Terminar no Top 5", "Classificação", Strategy.RANK, "classification", "5", PRE, drivers.stream().map(c -> choice(c.key(), c.label(), "1.20")).toList());
                b.add("DRIVER_DUEL", "Confronto direto · " + drivers.get(0).label() + " / " + drivers.get(1).label(), "Confrontos", Strategy.HEAD_TO_HEAD, "classification", null, PRE, drivers.subList(0, 2));
                b.selection("FASTEST_LAP", "Volta mais rápida", "Corrida", "fastestLap", drivers, PRE);
                b.selection("SAFETY_CAR", "Safety car durante a corrida", "Corrida", "safetyCar", yesNo(), PRE);
                b.selection("DRIVER_CLASSIFIED", drivers.getFirst().label() + " será classificado", "Classificação", "driverClassified", yesNo(), PRE);
            }
            default -> { return List.of(); }
        }
        return List.copyOf(b.definitions);
    }

    public Optional<Definition> definition(PredictionMarket market, List<EventParticipant> participants) {
        if (market.getTemplateCode() == null) return Optional.empty();
        if (market.getDefinitionData() != null) {
            try { return Optional.of(json.readValue(market.getDefinitionData(), Definition.class)); }
            catch (Exception error) { throw new IllegalStateException("Definição persistida inválida", error); }
        }
        return definitions(market.getEvent(), participants).stream().filter(d -> d.code().equals(market.getTemplateCode())).findFirst();
    }
    public List<ResultField> resultSchema(ArenaEvent event, List<EventParticipant> participants, List<PredictionMarket> markets) {
        Map<String, ResultField> fields = new LinkedHashMap<>();
        markets.stream().filter(m -> m.getStatus() != MarketStatus.CANCELLED).flatMap(m -> definition(m, participants).stream()).flatMap(d -> d.fields().stream()).forEach(f -> fields.putIfAbsent(f.key(), f));
        return List.copyOf(fields.values());
    }
    private static Choice choice(String key, String label, String multiplier) { return new Choice(key, label, new BigDecimal(multiplier)); }
    private static List<Choice> yesNo() { return List.of(choice("YES", "Sim", "1.85"), choice("NO", "Não", "1.85")); }
    private static List<Choice> overUnder() { return List.of(choice("OVER", "Acima", "1.90"), choice("UNDER", "Abaixo", "1.85")); }

    private static class Builder {
        final String home, away;
        final String sport;
        final List<Definition> definitions = new ArrayList<>();
        Builder(String home, String away, String sport) { this.home = home; this.away = away; this.sport = sport; }
        List<Choice> sides(boolean draw) {
            List<Choice> values = new ArrayList<>(List.of(choice("HOME", home, "1.80"), choice("AWAY", away, "1.95")));
            if (draw) values.add(1, choice("DRAW", "Empate", "3.20"));
            return values;
        }
        List<Choice> sidesWithNone() { List<Choice> values = new ArrayList<>(sides(false)); values.add(choice("NONE", "Não ocorreu", "5.00")); return values; }
        void winner(String code, String name, String category, String metric, boolean draw, MarketTimingMode timing) { add(code, name, category, Strategy.WINNER, metric, null, timing, sides(draw)); }
        void total(String code, String name, String category, String metric, String line, MarketTimingMode timing) { add(code, name, category, Strategy.TOTAL, metric, line, timing, overUnder()); }
        void handicap(String code, String name, String category, String metric, String line, MarketTimingMode timing) { add(code, name, category, Strategy.HANDICAP, metric, line, timing, sides(false)); }
        void selection(String code, String name, String category, String metric, List<Choice> choices, MarketTimingMode timing) { add(code, name, category, Strategy.SELECTION, metric, null, timing, choices); }
        void exact(String code, String name, String category, int max, boolean series) {
            List<Choice> choices = new ArrayList<>();
            for (int h = 0; h <= max; h++) for (int a = 0; a <= max; a++)
                if (!series || (Math.max(h, a) == max && h != a)) choices.add(choice(h + "_" + a, h + " × " + a, series ? "3.50" : "8.00"));
            if (!series) choices.add(choice("OTHER", "Outro placar", "6.00"));
            add(code, name, category, Strategy.EXACT_SCORE, "score", null, PRE, choices);
        }
        List<Choice> margin(int split, boolean draw) {
            List<Choice> choices = new ArrayList<>(List.of(choice("HOME_SMALL", home + " por 1 a " + split, "3.00"), choice("HOME_LARGE", home + " por " + (split + 1) + " ou mais", "3.20"), choice("AWAY_SMALL", away + " por 1 a " + split, "3.10"), choice("AWAY_LARGE", away + " por " + (split + 1) + " ou mais", "3.40")));
            if (draw) choices.add(choice("DRAW", "Empate", "10.00"));
            return choices;
        }
        void add(String code, String name, String category, Strategy strategy, String metric, String line, MarketTimingMode timing, List<Choice> choices) {
            String description = switch (strategy) {
                case WINNER -> "Compara o placar do período indicado. Empate sem opção específica devolve os pontos. Em interrupção sem resultado válido, cancele o mercado para reembolsar.";
                case DOUBLE_CHANCE -> "Mais de uma seleção pode vencer: basta um dos resultados indicados ocorrer nos 90 minutos.";
                case TOTAL -> "Compara a soma dos dois participantes com a linha indicada. Igualdade devolve os pontos.";
                case HOME_TOTAL -> "Compara apenas os pontos do primeiro participante com a linha indicada. Igualdade devolve os pontos.";
                case HANDICAP -> "Aplica o ajuste indicado ao primeiro participante e compara os placares. Igualdade devolve os pontos.";
                case BOTH_SCORE -> "Sim quando ambos os participantes têm pelo menos um gol nos 90 minutos.";
                case EXACT_SCORE -> "Compara o placar final registrado com a seleção exata.";
                case MARGIN -> "Classifica a diferença absoluta no placar final, incluindo a prorrogação quando aplicável.";
                case SELECTION -> "Usa a estatística registrada para o período indicado. Safety car exclui VSC quando aplicável.";
                case RANK -> "Cada piloto cuja posição final atende ao Top indicado vence. Usa a classificação oficial.";
                case HEAD_TO_HEAD -> "Vence o piloto com a melhor posição na classificação oficial entre os dois selecionados.";
            };
            if (sport.equals("TENNIS")) description += " Sets completos com tie-break em 6 × 6; o tie-break vale um game. Em abandono, cancele os mercados sem resultado completo.";
            if ((sport.equals("CS2") || sport.equals("VALORANT")) && metric.equals("map1")) description += " Inclui rounds da prorrogação.";
            if (sport.equals("CS2") && (metric.equals("map1Half1") || metric.equals("map1Half2"))) description += " Considera somente o tempo regulamentar, sem rounds da prorrogação.";
            List<ResultField> fields;
            if (metric.equals("score") || metric.equals("classification")) fields = List.of();
            else if (strategy == Strategy.SELECTION) fields = List.of(new ResultField(metric, name, category, "select", true, choices.stream().map(c -> new ResultChoice(c.key(), c.label())).toList()));
            else fields = List.of(new ResultField(metric + "Home", metricLabel(metric) + " · " + home, category, "number", true, List.of()), new ResultField(metric + "Away", metricLabel(metric) + " · " + away, category, "number", true, List.of()));
            definitions.add(new Definition(code, name, category, strategy, metric, line == null ? null : new BigDecimal(line), timing, List.copyOf(choices), fields, description));
        }
        String metricLabel(String metric) {
            return switch (metric) {
                case "firstHalf" -> "Placar no intervalo"; case "quarter1" -> "Pontos no primeiro quarto";
                case "corners" -> "Escanteios"; case "cards" -> "Cartões"; case "games" -> "Games totais (tie-break conta 1 game)";
                case "points" -> "Pontos totais"; case "set1" -> "Placar do primeiro set"; case "map1" -> "Placar do primeiro mapa/jogo";
                case "map1Half1" -> "Rounds do primeiro half (12 rounds)"; case "map1Half2" -> "Rounds do segundo half (sem prorrogação)";
                case "kills" -> "Abates no jogo 1"; default -> metric;
            };
        }
    }
}
