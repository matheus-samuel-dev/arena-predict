# Diagnóstico da página Ao vivo — produção — 07/10/2026

**B — IMPLEMENTAÇÃO PRONTA, AGUARDANDO CREDENCIAL.**

PandaScore implementado, mas não operacional porque PANDASCORE_API_TOKEN não está configurado.

Esta classificação registra adapters existentes e o bloqueio de configuração. Não certifica autenticação, cobertura do plano, normalização de payloads reais ou integração ponta a ponta. Nenhum provider foi autenticado nesta auditoria. O critério de parada por falta de credencial foi aplicado, sem alterações no código da aplicação.

## Evidências e ambiente

- Consultas em 07/10/2026, entre 09:22 e 09:29, America/Sao_Paulo.
- Origem publicada: `https://arena-predict.18-231-73-103.sslip.io`.
- Página verificada no Chrome: `/live`, sessão **Jogador Demo / Participante · Demonstração**.
- EC2: `18.231.73.103`; diretório exclusivo consultado: `/home/ubuntu/arena-predict`.
- Commit local e publicado: `337e3d9`; checkout remoto sem alterações.
- Containers `arenapredict-api`, `arenapredict-web` e `arenapredict-postgres` saudáveis.
- `docker compose config --quiet`: sucesso, sem imprimir configuração sensível.
- PostgreSQL 16; timezone da sessão PostgreSQL: `America/Sao_Paulo`.
- Últimas migrations aplicadas: V14, V15 e V16, todas com sucesso. Nenhuma migration executada nesta rodada.
- Consulta de contagens e estado realizada em transação `READ ONLY`.
- Nenhum token, senha, JWT ou resposta de autenticação foi impresso ou gravado.

## Caminho realmente utilizado

```text
HTTPS /live
  → Nginx da EC2
  → frontend em 127.0.0.1:5173
  → GET /api/events/live, na mesma origem
  → Nginx do container web
  → backend:8080
  → ArenaCatalogService.liveEvents()
  → arena_events WHERE status = 'LIVE'
```

O Nginx público encaminha este hostname para `127.0.0.1:5173`. O Nginx do frontend encaminha `/api/` para `backend:8080`, resolvendo o endereço pelo DNS Docker. Os logs do frontend confirmam consultas reais a `/api/events/live` e `/api/sports-sync/status`, com HTTP 200, a cada aproximadamente 30 segundos enquanto a página está visível. Isso comprova o caminho usado pela tela publicada, sem depender apenas do `.env` de build.

O cliente está em `frontend/src/services/api.ts`, `eventsApi.live()`. O backend expõe o endpoint em `ArenaCatalogController`; `ArenaCatalogService.liveEvents()` usa `findByStatusOrderByStartsAtAsc(EventStatus.LIVE)`.

## HTTP público comprovado

O teste foi realizado server-side na EC2 usando HTTPS com validação TLS e duas sessões Demo autorizadas pelo usuário. Os tokens permaneceram apenas na memória do processo da EC2; respostas de login não foram exportadas.

| Identidade | Endpoint | HTTP | Resultado |
|---|---|---:|---|
| Sem autenticação | `/api/events/live` | 401 | Autenticação exigida |
| Participante Demo | `/api/events/live` | 200 | `[]` |
| Admin Demo | `/api/events/live` | 200 | `[]` |
| Participante Demo | `/api/events?page=0&size=48&source=REAL` | 200 | `totalElements: 0` |
| Participante Demo | `/api/sports-sync/status` | 200 | `{"healthy":false,"lastSuccessAt":null}` |
| Admin Demo | `/api/admin/sports-sync/providers` | 200 | Cinco adapters; todos `configured: false` |

As autenticações Demo retornaram HTTP 200. Esse HTTP pertence ao ArenaPredict e **não** comprova autenticação em provider esportivo.

## Banco: contagens atuais

| Origem | Quantidade |
|---|---:|
| Total | 29 |
| REAL: identidade externa preenchida e `demo=false` | 0 |
| DEMO: `demo=true` | 29 |
| Local não Demo e sem identidade externa | 0 |
| Demo arquivado | 0 |

| Status | Quantidade |
|---|---:|
| SCHEDULED | 5 |
| OPEN_FOR_PREDICTIONS | 5 |
| LIVE | 0 |
| FINISHED | 13 |
| POSTPONED | 0 |
| CANCELLED | 6 |

Todos os 29 eventos tinham `external_provider=null` e `demo=true`. Portanto, não existe evento real cuja persistência, placar, identidade externa ou normalização possa ser comprovada neste ambiente.

## Providers implementados, configuração e runtime

| Adapter | Variável esperada | `.env` privado | Container backend | Flag no runtime | Estado da API administrativa |
|---|---|---|---|---|---|
| PandaScore | `PANDASCORE_API_TOKEN` | NÃO CONFIGURADO | NÃO CONFIGURADO | `SPORTS_SYNC_ENABLED=true` | `UNCONFIGURED` |
| API-FOOTBALL | `API_FOOTBALL_KEY` | NÃO CONFIGURADO | NÃO CONFIGURADO | `API_FOOTBALL_ENABLED=false` | `DISABLED` |
| API-BASKETBALL | `API_BASKETBALL_KEY` | NÃO CONFIGURADO | NÃO CONFIGURADO | `API_BASKETBALL_ENABLED=false` | `DISABLED` |
| API-Tennis | `API_TENNIS_KEY` | NÃO CONFIGURADO | NÃO CONFIGURADO | `API_TENNIS_ENABLED=false` | `DISABLED` |
| API-FORMULA-1 | `API_FORMULA1_KEY` | NÃO CONFIGURADO | NÃO CONFIGURADO | `API_FORMULA1_ENABLED=false` | `DISABLED` |

O Compose existente declara as cinco variáveis e utiliza valor vazio quando não preenchidas. A validação do Compose passou; os valores de destino foram verificados somente por presença no container. Não há provider externo operacional. O provider Demo interno é simulado e não conta como integração real. TheSportsDB não participa do registry real existente.

PandaScore tem `PANDASCORE_VIDEOGAMES=csgo,lol,valorant`, `SPORTS_SYNC_PROVIDER=PANDASCORE` e `PANDASCORE_LIVE_SCORES_ENABLED=false`. Essas flags não substituem a credencial nem concedem cobertura de placar.

## Sincronização: heartbeat não é consulta ao provider

Resposta administrativa em 09:27:35:

| Provider | Último heartbeat do scheduler (UTC) | Última tentativa | Último sucesso | Último HTTP externo | Recebidos / inseridos / atualizados | Próximo sync | Último erro |
|---|---|---|---|---|---|---|---|
| API-BASKETBALL | `2026-10-07T12:27:24.677131Z` | `null` | `null` | `null` | 0 / 0 / 0 | `null` | `null` |
| API-FOOTBALL | `2026-10-07T12:27:24.678735Z` | `null` | `null` | `null` | 0 / 0 / 0 | `null` | `null` |
| API-FORMULA-1 | `2026-10-07T12:27:24.679911Z` | `null` | `null` | `null` | 0 / 0 / 0 | `null` | `null` |
| API-Tennis | `2026-10-07T12:27:24.681032Z` | `null` | `null` | `null` | 0 / 0 / 0 | `null` | `null` |
| PandaScore | `2026-10-07T12:27:24.682154Z` | `null` | `null` | `null` | 0 / 0 / 0 | `null` | `null` |

`12:27:24Z` equivale a `09:27:24` em America/Sao_Paulo. O estado bruto persistido contém `UNCONFIGURED` para todos; a API calcula `DISABLED` para os quatro adapters cuja flag está desligada.

Os logs das últimas 24 horas do container backend continham uma mensagem de ausência de `PANDASCORE_API_TOKEN`, zero mensagens `[SPORTS_SYNC] Started provider=` e zero mensagens `[SPORTS_SYNC] Finished provider=`.

`SportsSyncScheduler` chama o coordenador a cada 30 segundos. `SportsSyncService.scheduledSynchronize()` grava o heartbeat antes de chamar `synchronize()`. Em seguida, `synchronize()` retorna imediatamente se a flag está desligada ou `provider.available()` é falso. Essa sequência explica todos os indicadores observados: scheduler ativo, zero consultas externas, zero registros reais.

Não há HTTP 401/403/429 externo observado, duração de chamada, IDs externos, status retornados ou quota consumida a informar: **nenhuma chamada autenticada foi realizada**. Não foram feitas consultas sem token para fabricar prova de conectividade. O último erro nulo significa que não houve tentativa, não que a integração está saudável.

## Conta Demo, UI, status e datas

- `liveEvents()` não depende de `isDemoUser`, tenant, workspace, placar, mercado ou janela de datas. Busca eventos pelo status LIVE. A conta Demo não é a causa do vazio.
- Decisão arquitetural: manter o catálogo esportivo compartilhado para leitura. A identidade Demo pode visualizar eventos reais públicos dentro do acesso autenticado; comandos administrativos, simulações e reset conservam as restrições existentes. Nenhuma permissão foi removida.
- Participante Demo e Admin Demo receberam exatamente o mesmo array vazio. Não foi inserido evento artificial para testar essa visibilidade.
- O botão **Atualizar agora** chama `refreshLive(true)`, que consulta novamente a API ArenaPredict. Não chama provider e não força sincronização externa. O clique na página publicada exibiu o aviso **Central ao vivo atualizada.** e manteve o empty state.
- **Última leitura** é `readAt`, criado no frontend depois da consulta. O tooltip já explica que a sincronização do provider é separada; o texto visível continua pouco explícito. Nenhum horário de sucesso externo foi observado.
- O empty state de participante continua mostrando **A arena está em intervalo / Não há eventos ao vivo agora** mesmo com `healthy=false`. Esse é um problema de comunicação comprovado, não evidência de ausência de jogos no provider. Não foi corrigido nesta rodada, respeitando a parada por falta de credencial e a exigência de provar o backend antes de alterar o frontend.
- O horário local do PostgreSQL não elimina eventos nessa query LIVE, pois ela não contém filtro de data. O mapeamento e a persistência usam `Instant`, e a configuração Hibernate contém `hibernate.jdbc.time_zone=UTC`. Isso é inspeção de implementação; não equivale ao teste com evento real em andamento solicitado para depois da autenticação.
- O upsert procura `(externalProvider, externalId)`, atribui `demo=false` somente ao criar um evento externo novo e usa `lastSyncedAt`/hash de snapshot. Nenhuma atualização de evento real ou idempotência real pôde ser observada porque o banco não possui registros externos.
- Simular resultado e Reset Demo não foram acionados; não houve alteração de eventos, status ou catálogo nesta auditoria.

Captura da aplicação publicada: [live-runtime-2026-10-07.jpg](../qa/live-runtime-2026-10-07.jpg).

## Matriz real de modalidades

**I** = capacidade identificada no código; **F existente** = há testes/fixtures automatizados no repositório, não executados nesta rodada; **comprovado real** = payload autenticado observado e cadeia validada. Nenhuma linha possui comprovação real. As capacidades indicadas com I não constituem promessa de cobertura do plano.

| MODALIDADE | PROVIDER | CONFIGURADO | SINCRONIZA | LIVE | PLACAR | ESTATÍSTICAS | ODDS/MERCADOS |
|---|---|---|---|---|---|---|---|
| Futebol | API-FOOTBALL, I; F existente | NÃO | NÃO | I; não comprovado | I: gols; não comprovado | Sem integração geral de estatísticas | Sem odds externas; mercados virtuais internos |
| Basquete | API-BASKETBALL, I; F existente | NÃO | NÃO | I; não comprovado | I: total/quartos; não comprovado | I: quartos; não comprovado | Sem odds externas; mercados virtuais internos |
| CS2 | PandaScore, I; F existente | NÃO | NÃO | I; não comprovado | I: série; flag desligada; não comprovado | Sem frames/rounds integrados | Sem odds externas; mercados virtuais internos |
| League of Legends | PandaScore, I; F existente | NÃO | NÃO | I; não comprovado | I: série; flag desligada; não comprovado | Sem frames/objetivos integrados | Sem odds externas; mercados virtuais internos |
| Valorant | PandaScore, I; F existente | NÃO | NÃO | I; não comprovado | I: série; flag desligada; não comprovado | Sem frames/rounds integrados | Sem odds externas; mercados virtuais internos |
| Tênis | API-Tennis, I; F existente | NÃO | NÃO | I; não comprovado | I: sets/games em singles; não comprovado | I: sets quando completos; não comprovado | Sem odds externas; mercados virtuais internos |
| Automobilismo | API-FORMULA-1, I; F existente; somente F1 | NÃO | NÃO | I; não comprovado | Sem placar de confronto; classificação I | I: volta/classificação; não comprovado | Sem odds externas; mercados reais bloqueados |

API-Tennis é um adapter separado de API-Sports. Automobilismo aqui significa somente a cobertura F1 implementada, não todas as categorias. TheSportsDB não foi integrado como feed ao vivo.

## Configuração necessária e recriação exata

Na **EC2**, editar somente o arquivo privado `/home/ubuntu/arena-predict/.env`. Para eSports, preencher `PANDASCORE_API_TOKEN` com uma credencial válida e manter `SPORTS_SYNC_ENABLED=true`. Não colocar o valor em variável Vite, comando de shell, código ou Git.

```bash
cd /home/ubuntu/arena-predict
nano .env
chmod 600 .env
docker compose config --quiet
docker compose up -d --no-deps --force-recreate backend
docker compose ps backend
```

O serviço é **backend**, e o container recriado é **arenapredict-api**. `docker restart` não aplica o novo environment do Compose. Não é preciso recriar PostgreSQL ou frontend, executar migration, resetar Demo ou alterar outro projeto na EC2.

Verificar presença sem revelar o token:

```bash
docker compose exec -T backend sh -c 'if [ -n "$PANDASCORE_API_TOKEN" ]; then printf "PANDASCORE_API_TOKEN=CONFIGURADO\n"; else printf "PANDASCORE_API_TOKEN=NÃO CONFIGURADO\n"; fi'
```

Para modalidades tradicionais, preencher o par correspondente somente para o provider que será ativado:

| Modalidade | Credencial privada | Flag necessária |
|---|---|---|
| Futebol | `API_FOOTBALL_KEY` | `API_FOOTBALL_ENABLED=true` |
| Basquete | `API_BASKETBALL_KEY` | `API_BASKETBALL_ENABLED=true` |
| Tênis | `API_TENNIS_KEY` | `API_TENNIS_ENABLED=true` |
| F1 | `API_FORMULA1_KEY` | `API_FORMULA1_ENABLED=true` |

Depois de editar, recriar o mesmo serviço backend. Os adapters tradicionais partem de orçamento local de 10 requests/hora e 100/dia; ajustar as configurações existentes ao plano efetivamente contratado antes da ativação. Não há necessidade de criar novo adapter para diagnosticar esta falta de configuração.

## Como testar depois de fornecer a credencial

Para PandaScore, já existe uma verificação manual server-side sem impressão de token:

```bash
cd /home/ubuntu/arena-predict
python3 qa/check-pandascore.py --env-file .env
```

Esse comando **não foi executado nesta rodada**. Com credencial, consulta os feeds reais running/upcoming/past para os jogos configurados e registra HTTP, contagens, status e amostras de IDs. Sem credencial, o script retorna UNCONFIGURED/zero requests, o que não prova a integração.

Após a recriação, o scheduler tem primeira execução após 15 segundos e verifica feeds a cada 30 segundos. O intervalo running atualmente configurado é 120 segundos. Consultar com Admin Demo ou administrador `/api/admin/sports-sync/providers` e exigir `configured=true`, tentativa real, HTTP 200, sucesso e contadores. Não confundir a presença da chave com autenticação bem-sucedida.

Comparar uma amostra real recebida com `arena_events`: provider/ID, modalidade, campeonato, participantes, UTC, status, score quando presente e `last_synced_at`. Repetir o sync e verificar que o mesmo registro foi atualizado. Recuperar o mesmo ID pela API pública autenticada. Se houver LIVE real, exigir o mesmo ID em `/api/events/live` e na tela. Se running retornar zero, comprovar a cadeia com um evento próximo/finalizado e manter LIVE vazio, sem alterar artificialmente status.

Apenas depois dessa evidência prosseguir com correções de mapping, persistência, API ou copy da página. Falhas/quota devem preservar snapshots existentes; ainda não foi observada resposta real 429 ou Retry-After neste ambiente.

## Validações desta rodada e alterações

- Executadas: Compose config na produção, leitura PostgreSQL, comparação de commit, saúde dos containers, HTTP público sem sessão, acessos Demo autorizados, HTTP autenticado dos endpoints, diagnóstico por provider, inspeção dos logs e verificação visual com clique de atualização.
- Não executados: backend tests, frontend tests, provider fixture suites, PostgreSQL integration tests, `mvn verify`, frontend typecheck, lint e build. A exigência foi condicionada à correção; nenhuma correção de código foi feita após constatar a ausência de todas as credenciais. Resultados anteriores de CI/QA não foram apresentados como validação desta rodada.
- Nenhum teste ou chamada esportiva externa foi apresentado como autenticado.
- Sem deploy, rebuild, restart, migration ou alterações de configuração na EC2.
- Arquivos criados localmente: este relatório e `qa/live-runtime-2026-10-07.jpg`.
- Pendências: credencial válida de pelo menos um provider, sua prova de vida autenticada e a cadeia provider → banco → API → UI. O texto visível da página permanece pendente para a etapa permitida de frontend.

**B — IMPLEMENTAÇÃO PRONTA, AGUARDANDO CREDENCIAL.**
