# ArenaPredict — mercados, multiplicadores e liquidação

Revisão de 21/09/2026. Escopo: regras de mercados e palpites com pontos virtuais. Sem alterações de identidade visual, navegação ou recursos sociais.

## Causa da suspensão de escanteios

O seed `ArenaMarketDemoInitializer` suspendia explicitamente `TOTAL_CORNERS` na primeira atualização do evento `demo-football-live`. No PostgreSQL local, o mercado 25 estava `SUSPENDED` sem ação administrativa correspondente. Não era uma interrupção causada pelo relógio, estatística ou partida. `MarketAvailabilityService` atribuía incorretamente toda suspensão ao administrador.

A atribuição arbitrária foi removida. A migração V9 reabre somente esse fixture demo, ainda ao vivo, sem auditoria administrativa de alteração do mercado. Ela adiciona `status_reason`. V10 corrige a mesma suspensão artificial no fixture de primeiro pistol do Valorant. Ações administrativas reais continuam protegidas. O mercado 19 de resultado ao vivo já tinha cancelamento administrativo real e reembolso: ele foi preservado; uma nova edição do mercado foi criada para o portfólio.

## Estados e disponibilidade

| Estado | Regra |
| --- | --- |
| OPEN | Aceita novas seleções ativas dentro da janela e com evento elegível. |
| SUSPENDED | Bloqueio temporário com motivo; pode voltar a OPEN enquanto a janela continuar válida. |
| CLOSED | Fechado definitivamente para novos palpites. Não pode reabrir, inclusive após correção posterior do placar. |
| SETTLED | Resultado processado; repetição não paga novamente. |
| CANCELLED | Sem novos palpites; palpites ativos recebem de volta os pontos utilizados, uma única vez. |
| DRAFT | Aguarda publicação, abertura da janela ou confirmação do evento ao vivo. |

O backend fornece `status` efetivo, `availability.allowed`, código, rótulo e motivo. A confirmação usa a mesma regra com bloqueios de evento e mercado. O frontend não deduz estados a partir do texto da mensagem. Prazos expirados e resultados já determinados prevalecem sobre suspensões temporárias. Atualizações do evento persistem o fechamento dos mercados afetados; GET não escreve no banco.

- `PRE_MATCH_ONLY`: fecha no prazo ou início, o que ocorrer primeiro.
- `LIVE_ENABLED`: pode continuar durante o evento, respeitando suspensão e prazo próprio.
- `LIVE_ONLY`: aguarda o evento LIVE.
- Eventos encerrados/cancelados não aceitam palpites. Quando o horário previsto passou, mas não há confirmação LIVE, o backend informa que aguarda atualização.
- Suspensão administrativa aplicada pelos endpoints registra motivo e auditoria reais. Uma suspensão legada sem origem registrada informa essa ausência, sem inventar autor.

## Motor demonstrativo v1

`DemoProbabilityEngine` é uma função determinística do estado esportivo e da definição persistida do mercado. Não usa nomes de equipes, aleatoriedade, margem comercial, exposição ou dinheiro. Calcula uma distribuição de resultados possíveis, soma a probabilidade dos resultados vencedores da seleção e converte `multiplicador = 1 / probabilidade`.

Limites: **1,01x a 100,00x**, duas casas, arredondamento HALF_UP. Valores muito improváveis podem atingir o mesmo teto. O potencial em pontos é `floor(pontos × multiplicador confirmado)`. Não há arredondamento aleatório.

| Modalidade | Variáveis e modelo |
| --- | --- |
| Futebol | Placar, minuto/segundos, tempo restante e acréscimos informados. Incrementos de gols por Poisson, taxa demonstrativa total de 2,6/90 minutos. Equipes futuras simétricas: a vantagem vem do placar e do tempo, não do nome. Acréscimos ausentes usam hipótese explícita de cinco minutos; o relógio nominal sozinho não simula apito final. |
| Basquete | Quarto, duração de 10/12 minutos, relógio regressivo, diferença de pontos e posses restantes. Distribuição de pontuação futura com variância por posse; empate modelado com desempate simétrico. Não reutiliza o modelo de gols. |
| Tênis | Melhor de 3/5, sets vencidos e games atuais quando fornecidos. Recursão da chance de fechar o set e a partida; sem inventar sacador ou usar relógio. Próximos sets têm chance neutra. |
| CS2 | BO1/3/5, mapas vencidos, mapa atual e rounds disponíveis. Corrida até 13 rounds; fronteiras de prorrogação MR3. A metade está representada pelo progresso dos rounds; não há ajuste por lado/economia sem dados confiáveis. |
| Valorant | Mesma abstração de série, com prorrogação por vantagem de dois rounds, distinta das fronteiras MR3 do CS2. |
| LoL / Dota 2 | Situação real da série/mapas. Sem fabricar ouro, torres, objetivos ou kills ausentes. |
| Automobilismo | Base demonstrativa estável; o provider não traz feed suficiente de posições/voltas para recalcular. A interface informa o modo estático. |
| Vôlei | Sets e pontos atuais quando presentes; alvo de 25, ou 15 no set decisivo, vantagem de dois. |

Trata-se de um modelo esportivo simplificado, não de probabilidades calibradas a dados históricos. Não inclui força individual, lesões, cartões como efeito sobre gols, serviço de tênis, economia de rounds ou prorrogação completa do basquete. Essas hipóteses ficam explícitas para não anunciar precisão que o provider não possui.

### Mercados derivados

Totais, total da primeira equipe, handicap, ambas marcam e dupla possibilidade usam a distribuição compatível com sua métrica. Em futebol, total de gols usa o placar; escanteios/cartões exigem arrays reais do provider por equipe. Taxas demo de 10 escanteios e 4,5 cartões por 90 minutos. Linha e quantidade já ocorrida determinam quantos eventos adicionais seriam necessários. Mercado com resultado já conhecido fecha.

Estatística ausente ou estratégia não modelada mantém a base estável e retorna `pricingMode=STATIC` com `pricingReason` explícito. Não transforma ausência em zero. O catálogo inclui a linha de gols 4,5 para permitir um mercado útil no exemplo 3×1.

## Snapshot, consistência e desempenho

Na confirmação, o servidor bloqueia evento/mercado, revalida a disponibilidade e calcula a cotação. O frontend envia `expectedMultiplier`; divergência retorna conflito antes de debitar pontos. O palpite armazena multiplicador e potencial próprios. Recalcular o evento não altera palpites existentes; a liquidação usa o potencial salvo.

O débito, palpite e recompensa são transacionais. Chaves de idempotência impedem repetição de confirmação/crédito. Cancelamento devolve a quantidade utilizada, sem multiplicar. O formulário administrativo valida os dados necessários antes de liquidar o conjunto de mercados.

O cálculo não persiste cotações em cada GET e não adiciona polling. O intervalo existente é de 30 segundos, com limpeza do timer e pausa quando o documento está oculto na página de detalhe. A listagem mantém carregamento em lote, com teste de limite de consultas. As atualizações demo e a renovação do calendário usam bloqueio de evento para evitar conflito na inicialização.

## Validação executada

### Suítes e builds

- Backend: `mvn -q test package`, **195 testes, zero falhas, zero erros**, pacote gerado. JVM local 25; a imagem Docker compila/executa com Java 21.
- Frontend: **18 arquivos / 142 testes** aprovados; `npm run typecheck` e `npm run build` aprovados.
- Docker: imagens de backend/frontend construídas; PostgreSQL, API e web saudáveis na execução. Flyway validou as dez migrações, incluindo V9/V10.
- `git diff --check`: sem erro de whitespace.

Testes adicionados cobrem os sete cenários de futebol solicitados, espelhamento das equipes, acréscimos, determinismo, limites, estatística ausente, relógios malformados, totais, vantagem de dez pontos no basquete, sets/games no tênis, BO1/3/5 e rounds nos shooters, vôlei e série de LoL. Os testes de integração cobrem snapshot em seis modalidades, cotação desatualizada recusada, reabertura de suspensão, fechamento permanente mesmo após correção do resultado e preservação da base ao editar metadados. A suíte também verifica cancelamento, reembolso, liquidação repetida, rollback de resultado incompleto e consultas em lote.

### E2E HTTP com PostgreSQL real

Eventos QA separados dos eventos principais. Participante e administrador autenticados pelos endpoints demo; palpites e liquidação via API real. Apenas a progressão esportiva dos fixtures `qa-market-*` é injetada por SQL com IDs delimitados, sem alterar snapshots ou créditos diretamente.

| Modalidade / evento | Multiplicador do líder antes → depois | Palpites | Resultado |
| --- | --- | --- | --- |
| Basquete / 41 | 1,40 → 1,01 | 296 / 297 | WON / LOST, crédito único |
| Tênis / 42 | 2,00 → 1,07 | 298 / 299 | WON / LOST, crédito único |
| CS2 / 43 | 1,69 → 1,01 | 300 / 301 | WON / LOST, crédito único |
| Valorant / 44 | 1,69 → 1,01 | 302 / 303 | WON / LOST, crédito único |
| LoL / 45 | 2,00 → 1,33 | 304 / 305 | WON / LOST, crédito único |
| Automobilismo / 46 | 3,20 → 3,20 | 306 / 307 | WON / LOST, classificação e crédito único |

Em cada modalidade: débito de 40 + 30 pontos conferido no extrato; snapshot imutável após alterar o contexto; confirmação repetida retorna o mesmo palpite; resultado reaplicado duas vezes sem crédito adicional. Saldo reconciliado com eventuais recompensas independentes dos desafios demo (550 pontos no primeiro caso e 180 no segundo). Essas recompensas não são confundidas com pagamento do mercado.

O cancelamento do antigo evento QA 31 restituiu exatamente **110 pontos** de três palpites ativos (293–295). Repetição retornou zero reembolsos. As transações 578–580 comprovam a devolução, sem duplicidade.

Evidências versionáveis: `qa/market-backend-summary.json`, `qa/market-api-e2e.json`, `qa/market-refund-ledger.json`, `qa/market-cancellation-evidence.json`. Reprodução: `qa/market-e2e.ps1`, fases `prepare`, `exercise`, `advance-football`, `verify-football` e `verify-persistence`. Logs completos locais: `qa/market-backend-final.log`, `qa/market-frontend-tests.log`, `qa/market-frontend-typecheck.log`, `qa/market-frontend-build.log`, `qa/market-docker-backend-final.log`.

### Navegador

O evento principal exibiu 3×1 aos 83:24, pré-jogo bloqueado e quatro mercados abertos. O resultado ao vivo mostrou **1,01x / 98,67x / 100,00x**, contra a antiga base **1,80x / 3,20x / 1,95x**. Escanteios 9,5 mostrou **1,38x / 3,63x**, abriu o formulário ao clicar e não apresentou suspensão artificial. Console sem erros/avisos nessa verificação.

O fluxo completo de futebol foi executado na interface com o evento QA **40**:

1. Participante abriu o evento 3×1 aos 20 minutos e selecionou o mercado OPEN.
2. Confirmou Palmeiras: **40 pontos × 1,16x = 46 pontos potenciais**, palpite **308**.
3. Confirmou Flamengo: **30 pontos × 23,45x = 703 pontos potenciais**, palpite **309**.
4. Saldo inicial **9.356**; após os dois débitos, **9.286**. O extrato contém exatamente dois débitos, somando 70.
5. Relógio avançado para 83:00: mercado passou a **1,01x / 93,21x / 100,00x**. Os palpites permaneceram em **1,16x / 23,45x**, conferidos na API e em “Meus palpites”.
6. Administrador registrou 3×1, intervalo 1×0, escanteios 7×4 e cartões 3×2; marcou encerramento e liquidação pelo formulário existente.
7. Palpite 308 ficou **WON**, recompensado em **46**; 309 ficou **LOST**, recompensa **0**.
8. Saldo final **9.332**. Transação de recompensa **604**, referência 308, única.
9. Reprocessamento via API duas vezes e repetição da operação na interface não pagaram novamente. Uma medição posterior repetiu o mesmo resultado com segurança.
10. Novo login do participante mostrou os estados finais, coeficientes originais e saldo correto.

Evidências: `qa/market-browser-before-settlement.json`, `qa/market-browser-settlement.json`, `qa/market-browser-settlement.log` e `qa/market-browser-network.json`.

### Persistência e Network

Após reiniciar o Docker e os serviços, foram relidos **12 palpites das seis modalidades adicionais**, mantendo status, multiplicador, potencial e recompensa. Cada vencedor ainda tinha exatamente um crédito. Evidência: `qa/market-persistence.json`.

Console do navegador: nenhum erro ou aviso capturado na validação final. Tráfego HTTP conferido pelos logs de acesso do Nginx, incluindo métodos, rotas e códigos de resposta. Na janela registrada houve 84 respostas 200, duas 201, uma 204, uma 304, **um 504 na primeira confirmação** e **um 499 no envio do resultado** (cliente encerrou a espera). O backend havia efetivado as operações; repetir as mesmas intenções recuperou os comprovantes, sem duplicar débito ou prêmio. Não são omitidos como se todo o tráfego tivesse sido bem-sucedido na primeira tentativa.

Medição posterior, pelo mesmo endereço local: três leituras do evento principal em **2.058–2.144 ms** e repetição do resultado liquidado em **2.150 ms**, todas concluídas. Nenhum erro de aplicação foi registrado no log do backend nessa janela. O teste de consultas em lote passou e o intervalo de atualização existente permaneceu em 30 segundos; não foi adicionado loop ou escrita de cotação em GET.

## Pendências e limites reais

- **Latência intermitente do ambiente local:** duas operações demoraram além do limite de espera da interface/proxy. A recuperação idempotente foi comprovada; a causa precisa dessa lentidão não foi isolada. A validação funcional está concluída, mas não equivale a um teste de carga nem a uma garantia de latência em produção.
- O Docker Desktop apresentou sockets temporários inacessíveis ao retomar a máquina. As pastas de sockets foram isoladas de forma reversível e recriadas; volumes e banco foram preservados. Ao concluir, PostgreSQL, API e web estão saudáveis.
- O modelo é demonstrativo e limitado aos dados existentes. Automobilismo e métricas sem contexto ao vivo permanecem estáticos, com indicação explícita. Serviço de tênis, ouro/objetivos ausentes e posições/voltas não são inventados.
- Eventos e palpites QA foram preservados para auditoria. Os eventos do fluxo concluído estão encerrados; o evento principal do portfólio permanece ao vivo e utilizável.
- Nenhum recurso visual, social, de ranking, avatar, sidebar ou movimentação financeira foi implementado nesta rodada.

## Arquivos alterados nesta rodada

- `backend/src/main/java/com/bolao/copa/arena/api/ArenaDtos.java`
- `backend/src/main/java/com/bolao/copa/arena/config/ArenaDemoInitializer.java`
- `backend/src/main/java/com/bolao/copa/arena/config/ArenaDemoScheduleMaintainer.java`
- `backend/src/main/java/com/bolao/copa/arena/config/ArenaMarketDemoInitializer.java`
- `backend/src/main/java/com/bolao/copa/arena/domain/PredictionMarket.java`
- `backend/src/main/java/com/bolao/copa/arena/service/ArenaCatalogService.java`
- `backend/src/main/java/com/bolao/copa/arena/service/ArenaPredictionService.java`
- `backend/src/main/java/com/bolao/copa/arena/service/DemoLiveEventService.java`
- `backend/src/main/java/com/bolao/copa/arena/service/DemoProbabilityEngine.java`
- `backend/src/main/java/com/bolao/copa/arena/service/MarketAvailabilityService.java`
- `backend/src/main/java/com/bolao/copa/arena/service/MarketDefinitionCatalog.java`
- `backend/src/main/java/com/bolao/copa/arena/service/provider/DemoSportsDataProvider.java`
- `backend/src/main/resources/db/migration/V10__repair_demo_pistol_suspension.sql`
- `backend/src/main/resources/db/migration/V9__market_suspension_provenance.sql`
- `backend/src/test/java/com/bolao/copa/arena/config/ArenaDemoScheduleMaintainerTest.java`
- `backend/src/test/java/com/bolao/copa/arena/DemoProbabilityEngineTest.java`
- `backend/src/test/java/com/bolao/copa/arena/MultimarketIntegrationTest.java`
- `backend/src/test/java/com/bolao/copa/arena/service/provider/DemoSportsDataProviderTest.java`
- `docs/market-business-validation.md`
- `docs/market-reference.md`
- `frontend/src/components/EventCard.tsx`
- `frontend/src/components/PredictionComposer.tsx`
- `frontend/src/services/api.ts`
- `frontend/src/test/market-cards.test.tsx`
- `frontend/src/test/prediction-composer.test.tsx`
- `frontend/src/types/index.ts`
- `qa/market-api-e2e.json`
- `qa/market-backend-summary.json`
- `qa/market-browser-before-settlement.json`
- `qa/market-browser-network.json`
- `qa/market-browser-settlement.json`
- `qa/market-cancellation-evidence.json`
- `qa/market-e2e-fixtures.json`
- `qa/market-e2e.ps1`
- `qa/market-persistence.json`
- `qa/market-refund-ledger.json`
- `qa/market-response-times.json`
