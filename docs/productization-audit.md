# Inventário anterior à productização — 06/10/2026

Checkout e EC2: ab33232. Banco publicado: 29 eventos Demo, nenhum evento externo; PandaScore UNCONFIGURED, sem última tentativa/sucesso. Não há credenciais para os novos feeds. A auditoria foi concluída antes das mudanças de implementação.

| Modalidade no banco | Eventos | Provider anterior | Eventos/Live/score reais | Mercados e liquidação existentes | Suporte anterior |
|---|---:|---|---|---|---|
| Futebol | 7 | Nenhum | Não | Resultado 90 min, dupla possibilidade, gols, ambas marcam, placar, intervalo, escanteios/cartões com dados manuais | Demo |
| Basquete | 3 | Nenhum | Não | Vencedor com prorrogação, spread, total, quartos/intervalo/margem | Demo |
| Vôlei | 1 | Nenhum | Não | Sets, handicap, pontos | Demo temporário |
| Tênis | 4 | Nenhum | Não | Vencedor, sets/games, handicap, tie-break | Demo |
| Automobilismo | 3 | Nenhum | Não | Vencedor, pódio/top N, confronto por classificação | Demo |
| Futebol americano | 1 | Nenhum | Não | Vencedor, pontos, spread, períodos | Demo temporário |
| CS2 | 6 | PandaScore | Adapter pronto, não autenticado | Série/total/handicap/placar; rounds/pistol só com dados Demo | READY_FOR_CREDENTIAL |
| Valorant | 2 | PandaScore | Adapter pronto, não autenticado | Série/total/handicap/placar; rounds só com dados Demo | READY_FOR_CREDENTIAL |
| League of Legends | 1 | PandaScore | Adapter usa LOL, domínio usa LEAGUE_OF_LEGENDS | Série e objetivos no domínio; alias precisa consolidação | Integração parcial |
| Dota 2 | 1 | Nenhum habilitado | Não | Série e objetivos | Demo temporário; PandaScore possui cobertura, fora do escopo atual |

Todos possuem catálogo/identidade e fallback de logos. Estatísticas e resultados Demo são fornecidos explicitamente ao motor existente; não comprovam consulta real. Não há obrigação de remover modalidade sem feed.

Arquitetura anterior: SportsDataProvider → PandaScoreClient/mapper → coordenador que escolhe um provider por SPORTS_SYNC_PROVIDER → catálogo/upsert → PostgreSQL. Identidades externas, transações por evento, lease durável, retry/backoff, snapshot, logs seguros e V1–V15 existem. O frontend lê o banco, não chama o fornecedor.

Regras existentes: MarketDefinitionCatalog define contratos por modalidade; MarketTemplateService congela a definição; MarketSettlementEngine calcula WON/LOST/refund; ArenaPredictionService liquida com chaves de ledger, locks e marcador de resultado; PointWalletService e ProgressionService propagam pontos/conquistas/notificações; ArenaPoolRankingService projeta os palpites processados. Correções publicadas ficam sob revisão auditável. DemoScenarioService mantém a jornada isolada.

Multiplicadores: DemoProbabilityEngine v2 já tem modelos diferentes de futebol, basquete e séries/sets, limites 1,05–15 e snapshot no palpite. Porém eventos externos ainda recebem valores STATIC das opções; origem explícita e prematch determinístico precisam evolução. Não é necessário reescrever a carteira ou o ranking.

Plano: registry sobre o contrato existente; capacidades por modalidade/provider; normalização canônica de participantes, score, resultados e classificação; adapters API-Football, API-Basketball, API-Tennis e API-Formula-1 com configuração independente; sync e mercados guiados por dados liquidáveis; manter Sandbox e backoffice. APIs documentadas, nenhuma raspagem. Comercialização/redistribuição/logos serão declarados como dependentes de confirmação contratual, nunca presumidos pela existência do endpoint.
