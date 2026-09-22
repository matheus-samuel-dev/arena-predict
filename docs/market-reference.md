# ArenaPredict: referências e decisões de domínio

Pesquisa realizada em 8 de setembro de 2026. As páginas abaixo foram consultadas como fontes primárias para nomenclatura, organização e regras esportivas. O produto usa exclusivamente pontos virtuais e multiplicadores demonstrativos persistidos. Nenhum layout, texto de interface, marca ou asset dessas plataformas foi incorporado.

## Referências conceituais

| Fonte consultada | Observação útil para o domínio |
| --- | --- |
| [bet365 — mercados de futebol](https://help.bet365.com/s/en-bg/sports/football-betting-markets) e [organização das regras](https://help.bet365.com/s/en-bg/sportsrules/soccer) | Resultado, gols, ambas marcam, placar correto, cartões e escanteios são famílias distintas. A duração regulamentar precisa estar explícita. |
| [bet365 — basquete](https://help.bet365.com/s/en-ca/sportsrules/basketball) | Placar final e períodos têm escopos próprios. A inclusão de prorrogação deve constar da regra do mercado. |
| [bet365 — tênis](https://help.bet365.com/s/en-gb/sportsrules/tennis) | Sets e games são unidades diferentes; total/handicap de games depende dos games efetivamente jogados. Um tie-break ordinário conta como um game. Set não disputado não equivale a derrota. |
| [bet365 — eSports](https://help.bet365.com/s/en-gb/sportsrules/esports) | CS2 separa série, mapa, rounds e pistol. Pistol é o primeiro round de cada metade. Prorrogação requer regra explícita. LoL usa abates, estruturas e objetivos; Dota distingue abate de Roshan da coleta de itens. Resultados devem vir de estatísticas registradas. |
| [Pinnacle — regras por modalidade](https://www.pinnacle.com/en/future/betting-rules) | Valorant identifica o primeiro half pelos rounds 1 a 12. Futebol americano possui unidades e regras próprias, inclusive prorrogação. Mudanças no formato de séries afetam os mercados. |
| [bet365 — Fórmula 1](https://help.bet365.com/s/en-gb/sportsrules/motor-racing/formula-1) | A classificação é uma lista ordenada de pilotos. Volta mais rápida e presença de safety car são fatos independentes. Safety car físico e virtual são eventos distintos. |
| [bet365 — vôlei](https://help.bet365.com/s/en-gb/sportsrules/volleyball) | Vencedor, placar em sets, total de pontos e handicap de sets dependem de medidas diferentes; períodos não concluídos precisam de tratamento explícito. |
| [bet365 — indisponibilidade durante seleção](https://help.bet365.com/s/en-bg/sports/bet-placement) | Um mercado ao vivo pode estar temporariamente suspenso, mesmo com evento em andamento. A interface precisa comunicar o motivo específico. |

A revisão técnica de 9 de setembro também verificou a contagem de games e tie-break na [ITF](https://www.itftennis.com/en/about-us/organisation/tennis-glossary/), o fechamento dos sets nas [regras FIVB 2025–2028](https://www.fivb.com/wp-content/uploads/2025/01/FIVB-Volleyball_Rules2025_2028-EN.pdf), a vantagem de dois rounds na prorrogação competitiva do [Valorant](https://playvalorant.com/en-us/news/game-updates/valorant-patch-notes-1-03/) e o formato do CS2 no [regulamento BLAST Premier 2026](https://assets.blast.tv/rulebook/BLAST_Premier_Handbook_2026.pdf). O demo declara seu formato: tênis com tie-break em 6 × 6; CS2 com MR12 regulamentar e prorrogações MR3; halves sem prorrogação e total de rounds do mapa incluindo prorrogação.

## Seleção de produto do ArenaPredict

As decisões abaixo são adaptações próprias para um provider demonstrativo; não representam cópia das regras comerciais dessas fontes. O catálogo de execução é `MarketDefinitionCatalog` no backend. O conjunto gerado é limitado por modalidade e usa dados que o formulário de resultado consegue registrar.

| Modalidade | Famílias escolhidas | Dados necessários |
| --- | --- | --- |
| Futebol | Resultado pré-jogo e ao vivo, dupla possibilidade, total de gols, ambas marcam, placar correto, intervalo, escanteios e cartões | Placar regulamentar; placar do primeiro tempo; escanteios/cartões por equipe |
| Basquete | Vencedor, handicap de pontos, total, total da primeira equipe, vencedor e total do primeiro tempo, primeiro quarto e margem de vitória | Placar final incluindo prorrogação; placar no intervalo e no primeiro quarto |
| Tênis | Vencedor, primeiro set, total e handicap de sets, total e handicap de games, placar em sets e tie-break | Placar final em sets; games do primeiro set e games agregados por atleta; ocorrência de tie-break; melhor de 3 ou 5 |
| CS2 | Série, primeiro mapa, total/handicap de mapas, placar da série, total/handicap de rounds, dois halves e pistol | Formato BO1/BO3/BO5; rounds do primeiro mapa; dois halves regulamentares e vencedor do primeiro pistol |
| Valorant | Série, primeiro mapa, total/handicap de mapas, total/handicap de rounds, pistol e placar da série | Formato da série; rounds do primeiro mapa; vencedor do primeiro pistol |
| League of Legends | Série, primeiro jogo, total/handicap de mapas, placar da série, primeiro abate/torre/dragão/Barão e total de abates | Placar da série; vencedor, abates e primeiros objetivos do primeiro jogo |
| Automobilismo | Corrida, Top 3/Top 5, confronto entre dois pilotos, volta mais rápida, safety car e classificação de um piloto | Ordem final de todos os inscritos; piloto da volta rápida; ocorrência de safety car físico; classificação do piloto indicado |
| Vôlei | Partida, primeiro set, placar em sets, total/handicap de sets e total de pontos | Placar final em sets; pontos do primeiro set e pontos agregados por equipe |
| Futebol americano | Resultado, handicap, total, total da primeira equipe, vencedor e total do primeiro tempo, primeiro quarto e margem de vitória | Placar final incluindo prorrogação; placar no intervalo e no primeiro quarto |
| Dota 2 | Série, primeiro jogo, total/handicap de mapas, placar da série, primeiro abate, primeiro abate do Roshan e total de abates | Placar da série; vencedor, abates e objetivos do primeiro jogo |

O catálogo não inclui empate anula no futebol, primeira torre no Dota 2, construtores na F1 nem mercados de próximos mapas/rounds. BO1 omite total/handicap de mapas e placar da série. O Top 5 exige mais de cinco pilotos inscritos. Essas restrições evitam opções redundantes ou sem dados suficientes.

## Regras operacionais

- Evento e mercado têm ciclos de vida separados. A mensagem de abertura deriva da disponibilidade efetiva dos mercados, calculada pelo backend.
- A janela pré-jogo termina no limite próprio ou no início, o que ocorrer primeiro. Mercados habilitados ao vivo podem continuar; mercados exclusivos ao vivo aguardam o início. Suspensão impede confirmação enquanto vigente.
- Mercados de fatos iniciais, como pistol e primeiro abate, fecham antes do evento no catálogo demonstrativo. Não se oferece um resultado que o placar ao vivo já revelou. Um provider de produção poderá abrir janelas de próximos segmentos quando fornecer progresso confiável.
- O resultado registrado é a evidência de liquidação. Estatística ausente não é zero e não é inferida do vencedor. Dados necessários devem ser fornecidos ou o mercado deve ser cancelado com reembolso explícito.
- Totais usam linhas com meio ponto para evitar ambiguidades comuns. Reembolso e resultados anulados devem usar o fluxo transacional de devolução, sem multiplicador de prêmio.
- Corridas têm múltiplos participantes; mercados Top N podem ter vários vencedores. O confronto entre dois pilotos existe apenas como um mercado dentro da corrida.
- Os valores base demonstrativos ficam no mercado/opção. Quando há contexto ao vivo suficiente, o backend calcula uma cotação determinística sem alterar essa base. O multiplicador confirmado e o retorno potencial são gravados no palpite antes do débito e conservados para a liquidação. A revisão de setembro de 2026 está documentada em `market-business-validation.md`.

## Bolão e liga

O armazenamento compartilhado `ArenaPool` é apropriado porque identidade, escopo, inscrições e ranking têm a mesma estrutura. `PoolType` define comportamento e autoridade, evitando duas entidades duplicadas.

- **Bolão:** grupo social criado por um participante; público ou privado, convite, administração pelo criador e ranking dos palpites explicitamente vinculados ao grupo.
- **Liga:** competição pública criada pela administração; temporada obrigatória; inscrição aberta aos participantes. Palpites finalizados contam automaticamente se registrados após a inscrição e dentro do período e do escopo definidos. Uma única consulta carrega os palpites elegíveis. A liga não exige que o usuário selecione seu identificador ao fazer o palpite.

Classificação por retorno virtual dos acertos, desempate por quantidade de acertos e nome. Não há pagamento automático de prêmio de liga: os dados demo informam isso e não anunciam valores de prêmio. Os desafios existentes mantêm suas regras próprias de recompensa virtual.
