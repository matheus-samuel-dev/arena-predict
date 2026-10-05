# Operação dos dados esportivos reais

O escopo deste adapter é **CS2, League of Legends e Valorant**. PandaScore cobre eSports; futebol, basquete e tênis precisam de outro provider. Calendário/status/resultados REST não equivalem a frames detalhados de rounds ou mapas. [Cobertura](https://developers.pandascore.co/docs/introduction), [planos](https://developers.pandascore.co/docs/plan-reference).

`PANDASCORE_API_TOKEN` é a única variável de credencial: Compose → backend → `sports.pandascore.api-token` → `PandaScoreProperties` → Bearer HTTP. O token nunca é enviado ao frontend, salvo em DTO ou usado por testes automatizados. Os jogos são selecionados por `PANDASCORE_VIDEOGAMES=csgo,lol,valorant`; esta variável não é uma credencial.

Na EC2 do portfólio, o arquivo privado é `/home/ubuntu/arena-predict/.env`. Para ativar o provider, configure ali **PANDASCORE_API_TOKEN** com a credencial válida. Mantenha `SPORTS_SYNC_ENABLED=true`. Depois recrie o backend: uma simples reinicialização do container existente não atualiza as variáveis do Compose.

```bash
cd /home/ubuntu/arena-predict
nano .env
chmod 600 .env
docker compose config --quiet
docker compose up -d --no-deps --force-recreate backend
```

Não execute `docker compose config` sem `--quiet`, nem imprima `docker inspect` com o bloco de environment. Não passe o token como argumento de comando, query string ou variável `VITE_*`. Nenhuma alteração no banco ou Reset Demo é necessária para preencher a credencial.

O scheduler verifica feeds vencidos a cada 30 segundos, com primeira execução após 15 segundos. Running e partidas acompanhadas: 120 segundos; próximos eventos: 900 segundos; resultados recentes: 300 segundos. A lease no PostgreSQL dura 900 segundos e evita consumidores concorrentes. As chamadas externas ocorrem fora das transações; cada upsert é atômico. Identities `(externalProvider, externalId)` não são substituídas por nomes ou timestamps. Correções de identidades/resultados publicados permanecem sob revisão, preservando a regra já existente.

A migration **V15** adiciona apenas metadados operacionais, sem credentials ou payloads: heartbeat, finalização do ciclo, contadores, duração, último HTTP observado, razão de erro e retry. Aquisição de lease e heartbeat **não** avançam `lastAttemptAt`. Lotes sem IDs acompanhados **não** avançam `lastSuccessAt`.

Um administrador, incluindo o Admin Demo autorizado a consultas, usa `GET /api/admin/sports-sync/status`: configured/enabled, CONFIGURED/SYNCING/ONLINE/DEGRADED/UNAVAILABLE/RATE_LIMITED/UNCONFIGURED/DISABLED, última tentativa/sucesso, heartbeat, próxima execução prevista, contadores e quota. O diagnóstico não contém o token. `receivedCount` contabiliza snapshots normalizados recebidos por feed; inserted/updated contam IDs distintos no ciclo. Um ID criado e novamente recebido no mesmo ciclo conta como inserido, sem duplicar a atualização.

Participantes usam `GET /api/sports-sync/status`, que retorna somente `healthy` e `lastSuccessAt`. A página Ao vivo só anuncia sincronização bem-sucedida no empty state quando há sucesso recente confirmado e a integração está ONLINE. Erros operacionais aparecem exclusivamente no painel de admin. “Última leitura” continua sendo a consulta do frontend ao ArenaPredict, não o horário da consulta ao PandaScore.

`GET /api/events/live` mantém seu contrato de array e lista LIVE independentemente de placar ou mercados. A seção Acontecendo agora do dashboard usa exatamente `ArenaCatalogService.liveEvents()`. A página consulta o nosso backend a cada 30 segundos e pausa em aba oculta; atualização manual e timer compartilham o bloqueio de request em andamento.

Mapeamento documentado: `not_started → SCHEDULED`, `running → LIVE`, `finished → FINISHED`, `canceled → CANCELLED`, `postponed → POSTPONED`. Status desconhecido de jogo suportado causa diagnóstico INVALID_RESPONSE com backoff, não um feed vazio “saudável”. [Ciclo de estados oficial](https://developers.pandascore.co/docs/matches-lifecycle).

Logos existentes são preservados se o snapshot não trouxer imagem. Uma identidade local em `/assets/` não é substituída por uma imagem genérica remota. A ausência de placar não vira zero. `PANDASCORE_LIVE_SCORES_ENABLED=false` é conservador; ative somente após confirmar que o plano/payload fornece o placar REST desejado. Dados detalhados via WebSocket não foram adicionados. Valorant não possui um plano de frames em tempo real na referência atual; status running e resultados REST continuam disponíveis.

Falha, timeout, payload inválido ou HTTP 401/403/429 preservam o último snapshot válido. Há retry limitado para falha transitória, limite local de 600 requests/hora, reserva de quota e respeito a Retry-After. O backoff do coordenador persiste após restart. A página nunca consulta PandaScore diretamente. [Limites do provedor](https://developers.pandascore.co/docs/rate-and-connections-limits).

Após inserir a credencial, faça a verificação **manual real**, server-side:

```bash
cd /home/ubuntu/arena-predict
umask 077
mkdir -p recovery
python3 qa/check-pandascore.py --env-file .env > recovery/pandascore-real-check.json
```

O script consulta running/upcoming/past por jogo, valida TLS, recusa redirects com Bearer, limita paginação e imprime apenas status, quota, contagens e amostras de dados públicos. Nunca imprime a credencial ou o corpo de erro. Sem token, registra UNCONFIGURED, zero requests e exit code 2. Uma resposta 200 de documentação ou uma fixture **não** comprova autenticação real.

Depois da primeira sincronização, confira no status administrativo `lastAttemptAt`, `lastSuccessAt`, `lastHttpStatus`, contadores e `supportedSports`. Confira os eventos reais em `/api/events`, LIVE em `/api/events/live` e os mesmos IDs no dashboard. Ausência de running é normal se chamadas autenticadas/sync estiverem confirmadas e os feeds futuros/encerrados tiverem sido consultados.

REAL mantém identidade externa e resultado do provider. DEMO mantém sandbox, Simular resultado e Reset Demo; esses comandos não afetam REAL. Um evento Demo LIVE aparece com “Demonstração”; um evento real conserva a identificação PandaScore. Não há criação de Demo para preencher a ausência de eventos reais.

Os testes usam mocks e fixtures, com rollback; a validação PostgreSQL usa um banco descartável separado. A CI executa `mvn -B verify` e testes/build do frontend sem token e sem chamadas esportivas externas. Uma execução verde de testes **não** muda a classificação da integração para operacional.
