# Integração esportiva CS2

## Arquitetura preservada e evolução

O projeto usa Java 21/Spring Boot 3.3.5, Spring Security/JWT, JPA, PostgreSQL, Flyway, React 18/TypeScript/Vite, Nginx e Docker Compose. `ArenaEvent`, `Competitor` e `Championship` já representavam partidas, participantes e competições. `SportsDataProvider`, `EsportsDataProvider`, `DemoSportsDataProvider`, o estado `demo`, placares, `Instant` e formatos BO já existiam.

A integração amplia essas estruturas; não cria tabelas paralelas de partidas nem uma segunda regra de pontuação:

```text
PandaScore REST → PandaScoreClient → DTOs/Mapper → SportsDataProvider
  → SportsSyncService → SportsCatalogSyncService / SportsMatchSyncService
  → PostgreSQL → endpoints existentes → React

Resultado final válido → ArenaPredictionService.settleDerived
  → MarketSettlementEngine → carteira/ledger + progressão
  → ranking calculado a partir dos palpites processados
```

`SportsSyncService` conhece a interface do provedor. As chamadas externas ficam no adapter PandaScore. `matchDetails` traz status e placar no mesmo snapshot, evitando consultas redundantes. O adapter Demo legado mantém `liveUpdates` e é filtrado por `provider.demo()`; a jornada controlada `/demo` usa comandos próprios e não depende desse gerador de placares.

## Configuração e credencial

Crie uma conta e obtenha o token no [painel PandaScore](https://app.pandascore.co/). Consulte [autenticação](https://developers.pandascore.co/docs/authentication) e [planos/endpoints](https://developers.pandascore.co/docs/plan-reference). Todas as chamadas usam `Authorization: Bearer` server-side; o token não aparece na URL, DTO de resposta, logs ou bundle React.

No `.env` não versionado da EC2/local:

```dotenv
PANDASCORE_API_TOKEN=YOUR_TOKEN_HERE
SPORTS_SYNC_ENABLED=true
PANDASCORE_LIVE_SCORES_ENABLED=false
```

Essas são as variáveis necessárias para ativar a integração com os padrões conservadores. `.env.example` deixa o token vazio. Sem credencial, nenhum request externo é feito e o backend continua disponível. Não é necessário criar `application-prod.yml`: o projeto já utiliza `application.yml` parametrizado por ambiente tanto localmente quanto no Compose.

| Variável | Padrão | Uso |
|---|---:|---|
| `SPORTS_SYNC_PROVIDER` | `PANDASCORE` | Adapter selecionado |
| `SPORTS_SYNC_ENABLED` | `false` | Habilita sincronização |
| `SPORTS_SYNC_INTERVAL_MS` | `30000` | Verificação local dos feeds vencidos, não uma chamada HTTP por tick |
| `SPORTS_SYNC_INITIAL_DELAY_MS` | `15000` | Espera inicial |
| `SPORTS_SYNC_UPCOMING_INTERVAL_MS` | `900000` | Calendário a cada 15 min |
| `SPORTS_SYNC_RUNNING_INTERVAL_MS` | `120000` | Partidas ao vivo a cada 2 min |
| `SPORTS_SYNC_TRACKED_INTERVAL_MS` | `120000` | Lote de partidas acompanhadas a cada 2 min |
| `SPORTS_SYNC_FINISHED_INTERVAL_MS` | `300000` | Resultados recentes a cada 5 min |
| `SPORTS_SYNC_NEAR_START_MINUTES` | `30` | Antecedência para acompanhamento frequente |
| `SPORTS_SYNC_UPCOMING_DAYS` | `7` | Horizonte do calendário |
| `SPORTS_SYNC_CORRECTION_WINDOW_HOURS` | `72` | Reconsulta de finais/correções recentes |
| `SPORTS_SYNC_TRACKED_BATCH_SIZE` | `20` | Limite de IDs por ciclo de acompanhamento |
| `SPORTS_SYNC_LEASE_MS` | `900000` | Lease do consumidor de sincronização no banco |
| `PANDASCORE_LIVE_SCORES_ENABLED` | `false` | Aceita placar de série REST ao vivo quando presente |
| `PANDASCORE_CONNECT_TIMEOUT_MS` | `3000` | Timeout de conexão |
| `PANDASCORE_READ_TIMEOUT_MS` | `8000` | Timeout de leitura do socket, inclusive do corpo após os cabeçalhos |
| `PANDASCORE_MAX_RETRIES` | `1` | Uma repetição para falha transitória |
| `PANDASCORE_RETRY_BACKOFF_MS` | `500` | Backoff inicial de retry |
| `PANDASCORE_FAILURE_BACKOFF_MS` | `60000` | Pausa após falhas persistentes |
| `PANDASCORE_RATE_LIMIT_BACKOFF_MS` | `3600000` | Pausa conservadora sem indicação do servidor |
| `PANDASCORE_MAX_REQUESTS_PER_HOUR` | `600` | Orçamento local de requests, incluindo retries |
| `PANDASCORE_REMAINING_RESERVE` | `20` | Reserva da quota reportada pelo provedor |
| `PANDASCORE_MAX_PAGES` / `PANDASCORE_PAGE_SIZE` | `3` / `100` | Paginação limitada por feed |
| `PANDASCORE_REFERENCE_CACHE_TTL_MS` | `21600000` | Cache de times/campeonatos explicitamente consultados: 6 h |

`PANDASCORE_BASE_URL` existe para testes/desenvolvimento fora do Compose; mantenha o padrão HTTPS oficial em produção. Não configure destinos de terceiros com uma credencial real.

## Sincronização, resiliência e quota

CS2 usa o prefixo legado `/csgo/`. São usados `/csgo/matches/upcoming`, `/running`, `/past` e `/csgo/matches?filter[id]=...` para reconciliação em lote. A operação de detalhe individual usa `/matches/{id}`, evitando o endpoint específico de CS com restrição de plano. Todos os snapshots são validados como Counter-Strike.

Times e campeonatos vêm embutidos nas partidas, são deduplicados em memória no lote e consultados em lote no banco. Não existe chamada adicional de equipe/campeonato por partida. Os dados persistidos servem como cache para todos os usuários; Redis não foi introduzido. O cache de referências é local ao processo e limitado aos dois catálogos.

Cada feed tem seu próprio último sucesso persistido. A lease no PostgreSQL evita que réplicas façam o mesmo ciclo simultaneamente. Chamadas de rede não mantêm transações de banco abertas. Uma falha em uma partida reverte somente sua transação, e as demais do lote ainda são tentadas. O ciclo fica indisponível em vez de anunciar sucesso completo quando houve erro de persistência.

O plano de calendário documenta 1.000 requisições/hora. O cliente reserva quota consultando `X-Rate-Limit-Remaining`, conta retries no orçamento local e respeita `Retry-After` em 429/5xx quando presente. 429 não gera retry imediato; 401/403 pausa consultas e informa credencial/plano recusado. Não há retry infinito. Os padrões consomem tipicamente cerca de 76 requests/hora com uma página por feed e um lote acompanhado, antes de retries; páginas adicionais aumentam esse total. [Documentação de rate limit](https://developers.pandascore.co/docs/rate-and-connections-limits).

O orçamento local é por processo; quota do servidor e lease coordenam a operação normal. Reinícios perdem o contador local, mas não o limite do provedor. Use um consumidor de sync no deploy atual e dimensione a lease acima do pior tempo de execução ao elevar timeouts, páginas ou retries.

Os limites de páginas impedem varreduras ilimitadas. Ao atingir o teto, há warning: reduza o horizonte ou ajuste páginas respeitando a quota. Partidas sem dois adversários identificáveis, horário ou competição suficiente são adiadas até o provedor completar o payload. Elas não ganham horários/equipes fictícios. Cancelamentos de partidas já conhecidas são reconciliados pelo lote acompanhado; eventos distantes podem ser confirmados somente quando entrarem na janela de acompanhamento.

Quedas, timeout, erro HTTP ou quota esgotada preservam os últimos dados persistidos. O transporte não segue redirecionamentos com a credencial e aplica timeout também se o provedor parar de enviar o corpo depois dos cabeçalhos. O frontend continua consultando apenas o Arena. Refresh usa intervalo leve e pausa em aba oculta; nenhuma chamada de esportes vai do browser à PandaScore.

## Dados e estados

`V11__external_sports_data.sql` adiciona IDs externos, metadados de sincronização/resultado e a tabela de estado do scheduler. Mantém dados legados e Demo. Há unicidade de `(external_provider, external_id)` para eventos, competidores e campeonatos, além de índice composto de provedor/status/horário. `bestOf` e temporada podem ser nulos; logos aceitam URLs maiores.

V12 reforça a separação do catálogo interno/externo com checks e chaves estrangeiras compostas. A migração preserva os registros oficiais e não inventa identidades: casos legados incompatíveis que não possam ser reparados conservadoramente interrompem a atualização para revisão. V13 adiciona o escopo da competição Demo controlada e seu estado transacional. Veja [migrações e isolamento Demo](demo-flow.md#separação-entre-dados-reais-e-demo).

Status são traduzidos exclusivamente no mapper PandaScore: `not_started → SCHEDULED`, `running → LIVE`, `finished → FINISHED`, `canceled → CANCELLED`, `postponed → POSTPONED`. A UI usa os rótulos em português. Não se inventa um estado de interrupção que o provedor não forneça. Snapshots antigos não fazem uma partida ao vivo voltar a agendada; finais podem ser importados diretamente quando a aplicação ficou desligada durante a partida.

Horários permanecem `Instant`/UTC em colunas `timestamp with time zone`; o React apresenta pelo `Intl.DateTimeFormat` do usuário. Placar é associado por ID de equipe, e a inversão da ordem dos adversários no payload não troca os lados dos palpites.

Campos ausentes permanecem nulos. Acrônimo do provedor é separado da chave interna de competidor. Logos usam URL HTTPS do provedor e fallback visual em caso de ausência/erro.

## Placar ao vivo e mapas

Com `PANDASCORE_LIVE_SCORES_ENABLED=false`, partidas podem aparecer AO VIVO, mas o placar é omitido com aviso. Com `true`, apenas pares de placares efetivamente retornados e válidos em `results` são publicados; zeros reais são válidos, ausência não vira zero.

Esse recurso representa o placar da **série/mapas vencidos** da resposta REST. Não implementa frames de rounds nem garante latência de transmissão. Os feeds detalhados via WebSocket exigem plano/coverage próprios e permanecem uma extensão futura do adapter. A flag não concede acesso ao plano. [Referência dos planos](https://developers.pandascore.co/docs/plan-reference), [WebSockets](https://developers.pandascore.co/docs/websockets-overview).

Não há token fornecido no ambiente de implementação; chamadas com credencial real e cobertura/latência contratadas não foram comprovadas. Os testes usam respostas mockadas baseadas no formato documentado, identificadas como fixtures, sem apresentá-las como dados reais recebidos.

## Resultado, pontuação e correções

Partidas externas só publicam mercados que dependem do placar da série e cujo BO é conhecido. São mercados pré-jogo, usando os multiplicadores virtuais do Arena; não são odds fornecidas pela API. Não são gerados mercados de pistol/rounds/mapas que o payload de calendário não consegue liquidar.

Quando chegam status final, placar válido para BO1/3/5 e vencedor coerente, o mesmo `ArenaPredictionService.settleDerived` utilizado pelo sistema processa os palpites. Resultado, créditos, progressão e `resultProcessedAt` são commitados na mesma transação. A proteção combina locks, estados terminais dos mercados, palpites ACTIVE, chaves únicas do ledger e fingerprint do resultado. Replays não redistribuem pontos. O ranking já consulta os palpites processados e não exige outra tabela ou outro job de cálculo.

Cancelamentos antes da liquidação reutilizam o reembolso idempotente existente. Forfeit, empate ou final sem informações suficientes não são forçados a uma vitória padrão.

**Correção após liquidação:** como carteira, XP e conquistas existentes não têm reversão completa, o sistema conserva o resultado aplicado, salva a proposta em `pending_result_data`, marca `result_review_required` e registra auditoria. A UI sinaliza revisão. Não há recálculo automático nem botão que permita sobrescrever silenciosamente o resultado. Mudanças de adversários/BO/campeonato também entram em revisão quando comprometem os contratos existentes.

Para investigação administrativa, compare `home_score`, `away_score`, `winner_external_id`, `result_processed_at` e `pending_result_data` em `arena_events` e os eventos `EXTERNAL_RESULT_REVIEW_REQUIRED` na auditoria. Uma compensação futura deve executar, em transação, o cálculo determinístico da diferença por palpite, lançamentos compensatórios com chave por revisão e reconciliação explícita de XP/conquistas. Não apague ledger, não zere `result_processed_at` e não reabra mercados para “reprocessar”. Essa operação não está implementada nesta versão.

A janela automática de correções é limitada a 72 h por padrão. Correções posteriores exigem investigação operacional; o sistema não promete descobri-las indefinidamente. Finais incompletos que expirarem entram em revisão e deixam de consumir acompanhamento frequente.

## Demo e contratos HTTP

Participante Demo e Administrador Demo acessam o produto completo e podem abrir a jornada `/demo`, em uma competição controlada. A ação pública **Simular resultado** aceita somente a rodada corrente dessa competição, após autorização e validação de estado no backend. Não é um endpoint genérico para qualquer evento marcado Demo. Partidas reais possuem identidade externa e bloqueios transacionais contra edição de resultado, cancelamento manual, troca de origem e simulação.

O reset arquiva somente a rodada controlada, reembolsa palpites ativos e cria a próxima; nunca alcança eventos PandaScore. O histórico e o ledger são preservados. O scheduler legado filtra providers Demo; a jornada controlada tem manutenção própria e cancela a rodada que excede o limite ao vivo, sem inventar placar. Configuração e roteiro completo estão em [Modo Demo](demo-flow.md).

Endpoints existentes `/api/events`, `/api/events/live`, `/api/events/{id}`, `/api/admin/events`, `/api/admin/competitors` e `/api/championships` mantêm os contratos existentes e adicionam metadados opcionais de origem, sincronização e processamento. `bestOf` pode ser nulo para fontes externas. IDs internos existentes não mudam. Os DTOs administrativos também sinalizam revisão e origem.

Endpoint para administrador operacional: `GET /api/admin/sports-sync/status`. Retorna provedor, enabled/configured, estado, última tentativa/sucesso, quota reportada, próxima janela e número de revisões; não retorna configuração sensível. O painel administrativo usa esse endpoint. **Administrador Demo pode consultar esse status**, junto à lista explícita de consultas do backoffice. Comandos genéricos em `/api/admin/**` continuam bloqueados para contas Demo. Veja a [consolidação de 04/10/2026](consolidation-report-2026-10-04.md).

## Testes locais

```bash
cd backend
mvn verify
cd ../frontend
npm ci
npm run test:run
npm run lint
npm run typecheck
npm run build
cd ..
docker compose config --quiet
docker compose build
docker compose up -d
```

Maven não lê `.env`: exporte as variáveis no shell ao executar fora do Docker. O perfil `test` desativa scheduler/token e usa H2 com Flyway; testes externos mockam HTTP, sem depender da PandaScore. Fixtures estão em `backend/src/test/resources/pandascore/`. Também se valida PostgreSQL real com Compose descartável antes da entrega, quando o daemon local está disponível.

Resultados executados, cobertura, evidências e limitações da entrega inicial da integração estão em [qa/sports-validation.md](../qa/sports-validation.md). O [inventário de arquivos](../qa/sports-change-manifest.md) distingue as alterações daquela integração de mudanças locais preexistentes. Essas evidências antecedem o isolamento adicional e a jornada Demo; não substituem a validação das migrations V12–V14 e das permissões atuais.

## Atualização da EC2 depois de publicar o commit

Execute a partir do checkout já implantado, conservando as variáveis de banco/JWT/CORS/portas e o volume atual. Não troque o `.env` por `.env.example` e não use `down -v`.

As **quatro variáveis novas** da jornada Demo, já encaminhadas ao backend pelo Compose, são:

```dotenv
APP_DEMO_CONTROLLED_ENABLED=true
APP_DEMO_ROUND_WINDOW_HOURS=24
APP_DEMO_LIVE_TIMEOUT_MINUTES=15
APP_DEMO_MAINTENANCE_MS=60000
```

Para a apresentação pública, mantenha também `APP_DEMO_ENABLED=true` e `VITE_DEMO_MODE=true`. Use `APP_DEMO_LIVE_PROVIDER_ENABLED=false` e `APP_DEMO_LIVE_SCHEDULER_ENABLED=false` para deixar o placar demonstrativo sob ações explícitas. Essas quatro flags já existiam; não são novas credenciais. Preserve a configuração PandaScore mostrada acima e use identidades Demo diferentes da conta administrativa operacional. Nenhuma senha Demo precisa ser exposta ou configurada para o acesso rápido.

```bash
set -e
umask 077
cd "$(git rev-parse --show-toplevel)"
cp .env ".env.backup.$(date -u +%Y%m%dT%H%M%SZ)"
mkdir -p backups
docker compose exec -T postgres sh -c 'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Fc' \
  > "backups/arena-before-sports-$(date -u +%Y%m%dT%H%M%SZ).dump"
git pull --ff-only
nano .env
# Adicione/atualize PANDASCORE_API_TOKEN, SPORTS_SYNC_ENABLED=true
# e mantenha PANDASCORE_LIVE_SCORES_ENABLED=false até confirmar cobertura.
# Confira também as flags da jornada Demo listadas acima.
docker compose config --quiet
docker compose build backend frontend
docker compose up -d --no-deps backend frontend
docker compose ps
docker compose logs --tail=100 backend
```

Flyway aplica as migrations pendentes até V14 no startup, com `ddl-auto=validate`. Se V12 identificar referências inconsistentes, investigue o diagnóstico e restaure os vínculos verificados; não force a versão do Flyway nem desabilite constraints. Confira saúde dos containers e a jornada `/demo`; depois, com uma conta administrativa operacional, confira o painel Dados esportivos e os eventos com origem PandaScore. A primeira sincronização inicia após 15 s, sujeita à credencial, quota e disponibilidade. Nginx continua encaminhando `/api` internamente; nenhum ajuste destrutivo de EC2 é necessário.
