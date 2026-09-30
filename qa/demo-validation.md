# Entrega da demonstração controlada — Arena Predict

Registro de 30/09/2026. Este relatório descreve a evolução Demo sobre a integração esportiva já existente, com testes, builds e jornada no navegador validados. A captura de imagens da rodada final teve uma limitação técnica, descrita nos itens 15 e 17.

## 1. Diagnóstico anterior

O Arena Predict já tinha acesso rápido como Participante Demo e Administrador Demo, catálogo demonstrativo, palpites persistidos, carteira com ledger, resultados administrativos, pontuação e ranking. A integração CS2 com PandaScore já acrescentava identidade externa, sincronização de status/placar, resultado automático e controles de idempotência.

Faltava uma jornada concentrada e repetível de demonstração. Fixtures legados podiam permanecer ao vivo indefinidamente; o administrador público tinha uma superfície de acesso mais ampla que a necessária para uma apresentação. O reset precisava de um escopo explícito que não alcançasse eventos oficiais.

## 2. Arquitetura encontrada

Backend Spring Boot 3.3.5/Java 21, Spring Security com JWT e roles persistidas, JPA/Hibernate, PostgreSQL 16 e Flyway. Frontend React 18/TypeScript/Vite; Nginx serve os arquivos e encaminha `/api` ao backend. Docker Compose é o caminho de implantação existente, inclusive na EC2.

O processamento já era centralizado em `AdminEventResultService`, `ArenaCatalogService`, `ArenaPredictionService`, `MarketSettlementEngine`, `PointWalletService` e `ArenaPoolRankingService`. Não foi criada outra regra de pontuação ou outro ranking para simular o efeito no navegador.

## 3. Problemas encontrados

- Ausência de competição e estado persistido próprios para a jornada Demo.
- Possibilidade de referências cruzadas entre eventos internos e catálogos de provedores.
- Seed histórico que podia escolher um campeonato externo da modalidade.
- Necessidade de restringir contas públicas Demo sem enfraquecer JWT, RBAC ou permissões das contas operacionais.
- Necessidade de impedir que CRUD, geração de mercados e resultados administrativos genéricos alterassem a competição controlada.
- Risco de uma resposta antiga de polling sobrescrever visualmente uma rodada já iniciada ou restaurada.
- Necessidade de desfazer integralmente uma tentativa de resultado inválido, incluindo a transição para ao vivo.
- Um pedido automático do navegador a `/favicon.ico` retornava 404; o Nginx agora o redireciona ao ícone SVG existente.

## 4. Solução escolhida

Foi adicionada a **Competição de Demonstração**, com histórico persistido e uma rodada ativa BO3 **Aurora Demo × Horizonte Demo**. `DemoScenarioService` coordena início, conclusão e reset; `DemoScenarioFactory` provisiona o cenário pelos serviços existentes; `DemoScenarioStore` guarda a geração e os IDs da competição e dos eventos em `arena_demo_scenario`. Os comandos bloqueiam essa linha no banco e executam em transação.

A página `/demo` concentra o roteiro e reutiliza cards, compositor de palpites, modais e autenticação. As respostas vêm do backend. O polling consulta somente o Arena a cada 15 segundos com a página visível e descarta respostas obsoletas.

A integração `SportsDataProvider`/PandaScore, seus schedulers e o processamento de resultados reais continuam independentes. Detalhes de frequência, rate limit, timeout, resultados e correções oficiais permanecem em [Integração esportiva](../docs/sports-integration.md). Esta entrega não substitui a integração por fixtures Demo.

## 5. Arquivos criados

Inventário da evolução Demo; caminhos relativos à raiz do repositório. A integração esportiva anterior tem [inventário separado](sports-change-manifest.md).

| Área | Arquivos |
|---|---|
| API | `backend/src/main/java/com/bolao/copa/arena/api/DemoScenarioController.java`, `DemoScenarioDtos.java` no mesmo diretório |
| Orquestração | `backend/src/main/java/com/bolao/copa/arena/service/demo/DemoScenarioService.java`, `DemoScenarioFactory.java`, `DemoScenarioStore.java` |
| Inicialização | `backend/src/main/java/com/bolao/copa/arena/config/ControlledDemoLifecycle.java` |
| Política de acesso | `backend/src/main/java/com/bolao/copa/security/DemoAccessPolicy.java` |
| Migrations | `backend/src/main/java/db/migration/V12__isolate_provider_catalog.java`, `backend/src/main/resources/db/migration/V13__controlled_demo_scenario.sql` |
| Testes de domínio | `backend/src/test/java/com/bolao/copa/arena/DemoScenarioIntegrationTest.java`, `ProviderCatalogIsolationMigrationTest.java` |
| Testes de isolamento | `backend/src/test/java/com/bolao/copa/arena/service/EventDataOwnershipTest.java`, `DemoLiveEventOwnershipTest.java` |
| Testes de segurança | `backend/src/test/java/com/bolao/copa/security/DemoAccessPolicyTest.java`, `backend/src/test/java/com/bolao/copa/config/DemoSecurityIntegrationTest.java` |
| Fixtures de usuários comuns | `backend/src/test/java/com/bolao/copa/support/RegularTestUsers.java` |
| Jornada React | `frontend/src/pages/DemoPage.tsx`, `frontend/src/hooks/useDemoScenario.ts`, `frontend/src/demo.css` |
| Testes React | `frontend/src/test/demo-scenario.test.tsx`, `frontend/src/test/demo-admin-isolation.test.tsx` |
| Documentação e QA | `docs/demo-flow.md`, `qa/demo-api-e2e.ps1`, `qa/demo-api-e2e.json`, este relatório |

Os logs de execução em `qa/demo-*.log` são evidências locais; podem estar ignorados pelo Git. A [evidência do navegador](demo-browser-validation.json) registra a jornada final e as medidas DOM. A [captura desktop de uma rodada aberta](demo-desktop-open.png) foi salva em uma execução anterior; não há um conjunto completo de capturas móveis da rodada final.

## 6. Arquivos alterados

O diretório do backend nos grupos abaixo é `backend/src/main/java/com/bolao/copa/`.

| Área | Arquivos alterados nesta evolução |
|---|---|
| Entidades | `arena/domain/ArenaEvent.java`, `Championship.java`, `Competitor.java`, `EventParticipant.java` |
| Repositórios | `arena/repository/ArenaEventRepository.java`, `ArenaPredictionRepository.java` |
| Resultado e catálogo | `arena/service/AdminEventResultService.java`, `ArenaCatalogService.java`, `AdminOperationsService.java`, `EventDataOwnership.java` |
| Palpites e ranking | `arena/service/ArenaPredictionService.java`, `ArenaPoolRankingService.java` |
| Mercados e Demo legado | `arena/service/MarketAvailabilityService.java`, `MarketDefinitionCatalog.java`, `MarketTemplateService.java`, `DemoLiveEventService.java` |
| Seeds | `arena/config/ArenaDemoInitializer.java`, `ArenaMarketDemoInitializer.java`, `ArenaRankingDemoInitializer.java` |
| Contratos administrativos | `arena/api/ArenaAdminController.java`, `ArenaDtos.java`, `AdminOperationsDtos.java` |
| Autenticação | `config/SecurityConfig.java`, `dto/AuthDtos.java`, `service/AuthService.java`, `service/DemoAuthService.java` |
| Integração preservada | Validações de catálogo em `arena/service/sync/SportsMatchSyncService.java` e fixtures de testes esportivos foram adaptadas ao isolamento entre origens |
| Configuração | `backend/src/main/resources/application.yml`, `backend/src/test/resources/application-test.yml`, `.env.example`, `docker-compose.yml` |
| Servidor web | `frontend/nginx.conf`: compatibilidade do caminho convencional de favicon com o SVG existente |
| Navegação e experiência | `frontend/src/App.tsx`, `components/AppShell.tsx`, `EventCard.tsx`, `PredictionComposer.tsx`; `pages/LoginPage.tsx`, `AdminPages.tsx`, `DashboardPage.tsx`, `EventsPage.tsx`, `HelpPage.tsx`, `PerformancePages.tsx`; `styles.css` |
| Contratos React | `frontend/src/services/api.ts`, `frontend/src/types/index.ts` |
| Regressão automatizada | Testes de autenticação, CORS, configuração de segurança, contratos administrativos, palpites, invariantes e multimercados; testes React de login, cards, resultados e compositor |
| Documentação | `README.md`, `docs/sports-integration.md`, `qa/README.md` |

`EventDataOwnership.java` foi introduzido durante a integração esportiva e ampliado nesta evolução. O `git status` também inclui outros arquivos daquela integração ainda não commitada. **As alterações preexistentes em `frontend/src/pages/CommunityPage.tsx` e `frontend/src/test/community.test.tsx` foram preservadas e não são atribuídas a esta entrega.**

## 7. Migrations

- **V12, Java:** adiciona propriedade de provedor em campeonatos, times, eventos e participantes; checks, unicidade auxiliar e FKs compostas impedem cruzamento entre catálogo interno e externo. Repara conservadoramente referências internas de confronto direto por cópias internas de metadados, sem alterar registros oficiais. Referências oficiais inconsistentes ou cenários legados que exigem reescrita insegura interrompem a migração com diagnóstico.
- **V13, SQL:** adiciona `demo_managed`, `demo_archived` e o estado persistido `arena_demo_scenario`, com checks e FKs que limitam a competição controlada ao catálogo interno.
- A **V11 da integração esportiva foi preservada**. Não houve remoção de dados nem mudança de migrations antigas para encobrir inconsistências. O Hibernate continua com `ddl-auto=validate`.

O upgrade **V11 → V12 → V13 foi executado com sucesso no PostgreSQL 16 do Compose de QA**, preservando o volume. As instalações de teste H2 também aplicaram as migrations. A suíte adicional de domínio aplicou **V1–V13** no banco PostgreSQL 16 isolado `arena_demo_it`, com os resultados do item 15.

## 8. Endpoints criados e reutilizados

| Método e rota | Contrato |
|---|---|
| `GET /api/demo/scenario` | Novo: geração, competição, rodada, histórico, palpites, ranking e permissões |
| `POST /api/demo/events/{id}/start` | Novo: inicia somente a rodada atual |
| `POST /api/demo/events/{id}/result` | Novo: recebe `homeScore` e `awayScore`, valida BO3 e usa o resultado transacional existente |
| `POST /api/demo/reset` | Novo: recebe `expectedGeneration`, arquiva a rodada e cria a seguinte |
| `POST /api/auth/demo` | Reutilizado: recebe exclusivamente o perfil permitido e retorna JWT; resposta acrescenta `demoProfile` |
| `POST /api/predictions` | Reutilizado: registro real do palpite, débito, elegibilidade e idempotência |
| `POST /api/predictions/{id}/cancel` | Reutilizado: cancelamento elegível do próprio palpite |

As rotas Demo exigem autenticação. Os comandos de controle exigem Administrador Demo; o palpite exige Participante Demo. Contratos de leitura acrescentam identificação da competição controlada para a interface apresentar o modo somente leitura na administração genérica.

## 9. Serviços reutilizados

O resultado segue `DemoScenarioService → AdminEventResultService.record → ArenaCatalogService.recordResult → ArenaPredictionService.settleDerived → MarketSettlementEngine + PointWalletService`. A progressão continua no mecanismo existente e o ranking é projetado por `ArenaPoolRankingService` a partir dos palpites liquidados.

`MarketTemplateService` e `MarketDefinitionCatalog` geram os mercados da série. `AdminAuditService` registra início, reset e expiração. O reset cancela e reembolsa pelo mesmo `ArenaPredictionService`; não ajusta saldo diretamente no React ou por limpeza de tabelas.

## 10. Fluxo Demo

1. Participante Demo entra em `/demo`, escolhe um placar BO3 e confirma o palpite.
2. O backend grava o palpite e debita os pontos virtuais. Recarregar a página mantém o estado.
3. A troca para Administrador Demo autentica a outra identidade pelo servidor.
4. **Iniciar partida Demo** fecha os mercados pré-jogo e bloqueia novos palpites e cancelamentos fora do prazo.
5. **Simular resultado** pede placar e confirmação. O backend valida a série, finaliza, processa os palpites e persiste os créditos.
6. Ao voltar ao participante, a tela mostra resultado, palpite, recompensa e ranking devolvidos pelo backend.
7. **Começar nova rodada** oferece confirmação e permite repetir a experiência.

Não há placar ao vivo fictício produzido para a rodada controlada. Ela só recebe o placar escolhido no comando explícito de resultado Demo.

## 11. Isolamento de dados

Eventos reais continuam associados a `externalProvider + externalId`. O fluxo Demo exige evento interno, marcado Demo, não arquivado, pertencente à competição controlada e igual ao ID ativo guardado em `arena_demo_scenario`. IDs arbitrários, inclusive de outros eventos Demo, são recusados.

A V12 impede referências entre catálogo interno e externo também no banco; os serviços conferem o provedor específico. A V13 vincula o estado e os eventos à competição controlada. O Sports Sync mantém sua identificação externa e não assume dados internos da jornada. O gerador legado de placar é impedido de atuar em eventos oficiais ou controlados.

A administração genérica apresenta a competição, seus times, eventos e mercados como **somente leitura**. Guards no backend bloqueiam edição, resultado, classificação, criação/alteração e geração de mercados que tentem contornar esse fluxo; o frontend também remove essas opções dos seletores. A existência de uma role operacional `ADMIN` não substitui as regras de propriedade.

## 12. Reset

O reset bloqueia o estado, valida `expectedGeneration`, cancela e reembolsa a rodada ainda pendente, arquiva o evento anterior e cria um novo evento aberto na mesma transação. Não reabre partidas encerradas e não apaga usuários, resultados ou lançamentos do ledger.

Uma repetição da geração imediatamente anterior retorna o estado corrente sem outra rodada; confirmação mais antiga gera conflito. Duas requisições concorrentes para a mesma geração criam somente uma nova rodada. O lock é do banco e funciona entre instâncias do backend.

A classificação corrente exclui rodadas arquivadas e volta ao histórico de exemplo mais a rodada ativa. Carteira e progressão não são zeradas. Se o participante configurado estiver abaixo de **5.000 pontos virtuais**, um lançamento idempotente repõe somente a diferença. Contas comuns e dados externos não recebem essa operação.

## 13. Segurança

- Identidades Demo são resolvidas por conta persistida, e-mail reservado e role esperada; flags do browser não concedem autoridade.
- As duas contas Demo recebem `403` em `/api/admin/**` e não acessam a administração operacional.
- Participante Demo não pode iniciar, concluir ou resetar; Administrador Demo não registra palpites.
- Contas normais mantêm suas permissões, mas não ganham comandos Demo nem palpites na competição controlada.
- O cadastro público não permite capturar os e-mails reservados. Desativar o acesso rápido não transforma tokens Demo existentes em contas comuns privilegiadas.
- JWT continua validando assinatura, expiração, usuário e role persistidos.
- Resultado repetido não duplica recompensa; resultado diferente com a mesma chave gera conflito. Uma série inválida faz rollback integral.
- A credencial PandaScore continua somente no backend, por variável de ambiente. Nenhum token foi acrescentado aos arquivos versionáveis.

## 14. Testes adicionados

`DemoScenarioIntegrationTest` cobre persistência, resultado, carteira, ranking, replay, reset com reembolso, preservação do ledger, isolamento de evento oficial e usuários comuns, concorrência, manutenção, autorização HTTP, rollback de resultado inválido e administração genérica somente leitura.

`DemoAccessPolicyTest` e `DemoSecurityIntegrationTest` cobrem os perfis, JWT, roles persistidas, cadastro reservado, comandos permitidos e bloqueios. `ProviderCatalogIsolationMigrationTest` testa upgrade, reparo conservador, checks/FKs e interrupção de referências inseguras. `EventDataOwnershipTest` e `DemoLiveEventOwnershipTest` cobrem a origem dos dados e o bloqueio dos geradores legados.

`demo-scenario.test.tsx` cobre o fluxo, confirmações, dados devolvidos pelo servidor, erros recuperáveis, submissão duplicada, polling obsoleto e troca de perfil. `demo-admin-isolation.test.tsx` cobre restrições nos formulários administrativos. Fixtures das suítes existentes passaram a usar usuários comuns quando o cenário testado exige permissões operacionais.

O [roteiro E2E da API](demo-api-e2e.ps1) exercita HTTP, Nginx, backend e PostgreSQL reais do ambiente local isolado. APIs esportivas externas permanecem mockadas nos testes.

## 15. Resultado dos testes

| Verificação executada | Resultado confirmado |
|---|---|
| Backend completo, `mvn -B verify` | **356 testes aprovados**, zero falhas, erros ou testes ignorados; `qa/demo-backend-full.log` |
| Reexecução após fortalecer o rollback HTTP | **10 testes de `DemoScenarioIntegrationTest` aprovados**; `qa/demo-backend-rollback.log` |
| Frontend, `npm run test:run` | **192 testes em 22 arquivos aprovados**; `qa/demo-frontend-tests.log` |
| E2E API no Compose de QA | **PASS, 30 requisições**, todas com status esperado; [evidência JSON](demo-api-e2e.json) |
| Upgrade Flyway PostgreSQL 16 | **V11 → V12 → V13 aplicado**; aplicação iniciou com schema validado |
| Suíte adicional de domínio em PostgreSQL 16 | **39 testes aprovados**, zero falhas, erros ou testes ignorados; `qa/demo-postgres-tests.log` |
| Navegador: jornada, medidas DOM, teclado e console | **Aprovado nas verificações executadas**, com limitação de screenshots; [evidência JSON](demo-browser-validation.json) |

No E2E API, o evento **30**, palpite **316** e resultado **2–1** produziram **35 pontos de recompensa** e **1º lugar** no ranking Demo. Repetir o resultado manteve carteira e ledger; placar incompatível foi recusado. O reset foi idempotente e o reset de outra rodada reembolsou o palpite **317**. Respostas `403`, `409` e `422` do roteiro são rejeições deliberadamente testadas, sem falha inesperada registrada nas 30 requisições.

A execução PostgreSQL incluiu `DemoScenarioIntegrationTest`, `DemoSecurityIntegrationTest`, `SportsSyncIntegrationTest` e `ExternalMatchSettlementIntegrationTest` no banco isolado `arena_demo_it`. Ela exercitou o domínio Demo, permissões, sincronização e liquidação de fixtures externos no mesmo mecanismo de banco usado pelo deploy.

No navegador, foi confirmado um palpite **2–1 de 10 pontos**, persistido após recarga. A troca para administrador permitiu iniciar a partida, bloquear os palpites sem inventar placar ao vivo e registrar **2–1**. Ao retornar ao participante, a interface mostrou o palpite vencedor, **35 pontos** e **1º lugar**. O reset com confirmação criou uma nova rodada, na qual outro palpite foi confirmado. A tecla Escape fechou o modal e devolveu o foco ao botão de reset.

As medidas DOM nas larguras solicitadas **360, 390, 430, 768 e 1440 px** não encontraram overflow horizontal ou imagens quebradas. O console final não registrou warnings ou erros. A captura da rodada final falhou por timeout de `Page.captureScreenshot`; por isso, essas medidas e ações não são apresentadas como uma revisão completa de screenshots móveis atuais. A captura desktop anterior continua disponível como evidência da rodada aberta.

O ambiente de QA estava **sem token PandaScore e com zero eventos externos**. Portanto, esse E2E não comprova proteção de uma partida oficial já importada no runtime. Essa proteção foi exercitada pelos testes com fixtures externos persistidos e pelas constraints; autenticação e cobertura da API contratada continuam sem validação real.

## 16. Resultado dos builds e Docker

| Verificação executada | Resultado |
|---|---|
| Backend `mvn -B verify` | **BUILD SUCCESS**, JAR Spring Boot gerado |
| Frontend `npm run lint` | **Aprovado** |
| Frontend `npm run typecheck` | **Aprovado** |
| Frontend `npm run build` | **Aprovado** |
| Build Docker backend e frontend | **Aprovado**, estágios Java 21 e Node 22 |
| `docker compose config --quiet` | **Aprovado** com o ambiente privado de QA |
| Inicialização da stack de QA | **3 contêineres saudáveis**: PostgreSQL, backend e frontend |

Logs locais: `qa/demo-frontend-lint.log`, `qa/demo-frontend-typecheck.log`, `qa/demo-frontend-build.log`, `qa/demo-docker-build.log` e `qa/demo-docker-start.log`. O teste usou a stack isolada `arena-sports-qa`, sem alterar a EC2 ou remover volumes.

A inspeção final dos logs não encontrou erro de backend ou resposta HTTP de erro em operações da jornada. O único 404 observado era o pedido automático de favicon: corrigido e conferido em [validação Nginx](demo-nginx-validation.json), após novo build da imagem frontend. `nginx -t`, o favicon e o healthcheck passaram.

As novas variáveis são `APP_DEMO_CONTROLLED_ENABLED=true`, `APP_DEMO_ROUND_WINDOW_HOURS=24`, `APP_DEMO_LIVE_TIMEOUT_MINUTES=15` e `APP_DEMO_MAINTENANCE_MS=60000`. Para apresentar a jornada, também habilite as flags existentes `APP_DEMO_ENABLED=true` e `VITE_DEMO_MODE=true` (esta exige rebuild do frontend). Mantenha os geradores legados `APP_DEMO_LIVE_PROVIDER_ENABLED=false` e `APP_DEMO_LIVE_SCHEDULER_ENABLED=false`.

Preserve `PANDASCORE_API_TOKEN` e a configuração esportiva existentes. O [README contém os comandos exatos para backup e atualização da EC2 após publicar o commit](../README.md#atualização-de-uma-implantação-dockeraws). Nenhum deploy ou commit foi feito como parte desta validação.

## 17. Limitações restantes

- A jornada usa duas contas e uma rodada compartilhadas. Outro visitante pode avançar a mesma rodada; confirmações, geração e locks protegem a consistência, sem isolamento por visitante.
- O reset preserva histórico, carteira e progressão; não representa apagar toda a atividade da conta. O histórico arquivado cresce com o uso e não ganhou política destrutiva de retenção.
- Após 15 minutos ao vivo por padrão, a manutenção cancela a rodada e reembolsa, sem inventar um resultado. A verificação ocorre a cada 60 segundos; a janela aberta é renovada quando expira.
- A credencial PandaScore não foi fornecida nesta validação. Importação autenticada, quota contratada e live score real continuam dependendo do token e do plano. Nenhum fixture foi apresentado como resposta real do provedor.
- O tratamento de correções oficiais da integração real continua sendo o documentado em [resultado, pontuação e correções](../docs/sports-integration.md#resultado-pontuação-e-correções), sem crédito incremental duplicado.
- A captura de screenshots da rodada final encontrou timeout de `Page.captureScreenshot`. A jornada, o teclado, o console e as medidas DOM foram conferidos, mas não foi produzido um conjunto completo de screenshots móveis atuais. Evidências antigas de `qa/browser-audit.json` pertencem à validação de 12/09/2026 e não comprovam a nova jornada.

O roteiro de uso, as decisões do reset, as permissões e as opções de configuração estão em [Demonstração controlada](../docs/demo-flow.md).
