# Motor probabilístico esportivo v2 — decisões e limites

Base auditada: 2511582, 09/10/2026. V1 usava uma árvore de finais de série com p=0,5 por mapa e uma curva de recompensa comprimida. Ela é coerente sob esse prior, mas não estima força relativa e não separa confiança/política de pontos.

## Dados reais disponíveis

PandaScore Free: resultados/scores finais, identidade das equipes, formato, campeonato e datas; placar parcial da série. Auditoria: 233 CS2, 30 LoL e 8 Valorant confirmados; mediana de partidas por equipe 2/1/1. GET /csgo/teams/139149/stats retornou 403; times e past retornaram 200. Elenco atual existe no endpoint teams, mas não há lineups históricos datados nem nomes/resultados por mapa no payload efetivamente acessível. Não tratar elenco atual como elenco de partidas antigas.

## Modelo de força

Bradley–Terry regularizado por modalidade, ajustado sobre contagens oficiais de mapas/jogos em séries encerradas: p=logistic(theta_home-theta_away). Maximizar soma ponderada de h*log(p)+a*log(1-p), com penalidade gaussiana lambda/2*sum(theta²). Resultados sob a regra de parada de BO continuam tendo essa verossimilhança, até uma constante combinatória. Identidade/nomes/logos não são features; adversários entram no ajuste conjunto.

Janela de 30 dias, meia-vida de 14 dias e precisão gaussiana 4 são hipóteses explícitas, não estatísticas oficiais. Mínimo de 5 séries independentes por equipe e conectividade do grafo impedem inferir favoritismo por uma única vitória ou comparar ligas desconectadas. Preferir amostra do mesmo formato quando suficiente; caso contrário, conjunto por modalidade com limitação indicada. Elenco e mapas não verificados limitam a confiança; não há classificação HIGH nem alegação de calibração. Confrontos diretos/campeonato atual entram como resultados, sem bônus que os conte duas vezes.

Não adotar Glicko-2 só por sofisticação: a referência recomenda 10–15 partidas por participante/período, acima da cobertura mediana atual. Nenhum rating será chamado de ranking oficial. Avaliar temporalmente contra o baseline 50/50; informar tamanho de amostra, Brier e log-loss, sem declarar precisão ou prontidão comercial.

## Distribuição conjunta

Finais possíveis de BO1/3/5 por programação dinâmica a partir do placar confirmado e p por mapa/jogo. Vencedor, placar exato, total, handicap e vitória em pelo menos um mapa usam a mesma distribuição. Exemplo p=0,8: BO3 0–0 → vencedor home 0,896, total acima 2,5 =0,32; BO3 1–0 → home 0,96 e acima=0,20. Empate 0–0 com p=0,5 pode produzir 2,00× de cada lado legitimamente. Não usar regra de somar porcentagens.

Probabilidade estimada, confiança/cobertura, política de recompensa e multiplicador exibido são campos distintos. Política virtual inversa: referência 1/p (condicional em resultado decisivo quando houver reembolso); limites existentes 1,10–8,00 pertencem exclusivamente à recompensa, com indicação do limite aplicado. Não recortar probabilidades para caber nesses limites.

## Modalidades, segurança e preservação

CS2/Valorant/LoL têm datasets de força separados; não gerar mercados de rounds/objetivos sem dados. Futebol usa contagens de gols/Poisson; basquete, frequência/variância de pontos e período/tempo; tênis, sets/games e vitória por dois. Não usar parâmetros ou dados Demo em projeções reais. Nos adapters tradicionais sem credenciais, hipóteses/fallbacks ficam identificados; não afirmar calibração real.

LIVE sem estado confiável ou fora do escopo/tempo suportado não recebe uma cotação apresentada como atual: suspender a precificação, preservando evento/contratos/snapshot. Ausência de histórico com estado confiável admite prior equilibrado e confiança baixa. Distinguir essas duas ausências.

Aquisição só do banco/snapshots já sincronizados, sem API por visitante. Leitura de histórico em tarefa própria a cada 30s; não abrir REQUIRES_NEW dentro de consultas/confirmações de visitantes, evitando exaustão do pool de conexões. Cache determinístico depende de histórico confirmado/placar/formato, nunca sorteio ou nomes. Snapshot de preço/probabilidade/confiança/versões congelado na confirmação, tanto normal como treino. Resultado usa potencial e multiplicador já aceitos. Não reescrever apostas virtuais antigas, autenticação, páginas ou ranking. Migração apenas aditiva para metadados.

Fontes: [PandaScore planos](https://developers.pandascore.co/docs/plan-reference), [Bradley–Terry](https://www.jstatsoft.org/article/view/v048i09), [Glicko-2](https://www.glicko.net/glicko/glicko2.pdf), [duração NBA](https://official.nba.com/rule-no-5-scoring-and-timing/). Modelo interno estimado; não são probabilidades ou odds oficiais da PandaScore.
