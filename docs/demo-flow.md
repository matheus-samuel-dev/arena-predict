# Demonstração controlada do Arena Predict

A jornada `/demo` permite testar palpite, início da partida, resultado, pontuação e ranking persistidos, sem depender do calendário esportivo real. A competição **Competição de Demonstração**, seus times e seus eventos são identificados como Demo. A integração [PandaScore/CS2](sports-integration.md) continua independente.

## Como experimentar

1. Na tela de login, escolha **Participante Demo**. O acesso rápido abre `/demo`.
2. Escolha um placar de série BO3, por exemplo **2 × 1**, revise os pontos virtuais no modal e confirme.
3. Atualize a página: o palpite deve continuar em **Meus palpites nesta competição**.
4. Use **Administrador Demo** na própria jornada. Confirme **Iniciar partida Demo**; o backend fecha os mercados pré-jogo e bloqueia novos palpites.
5. Use **Simular resultado**, escolha **2 × 1** e confirme. O placar, a liquidação e os créditos são persistidos em uma transação.
6. Retorne a **Participante Demo** e confira o resultado, o status do palpite, a recompensa e o ranking da competição.
7. Como **Administrador Demo**, use **Começar nova rodada** e confirme. Retorne ao participante para repetir o fluxo.

As duas contas e a rodada são compartilhadas. A interface avisa quando outro visitante mudou a rodada e consulta somente o backend Arena Predict, a cada 15 segundos enquanto visível. A troca de perfil solicita outro JWT pelo acesso rápido; não modifica permissões localmente.

## Diagnóstico e arquitetura

O sistema já possuía Spring Boot, autenticação JWT, roles persistidas, JPA/PostgreSQL, migrations Flyway, mercados, carteira com ledger, pontuação idempotente, ranking derivado de palpites e frontend React/TypeScript. O caminho administrativo de resultado já estava centralizado em `AdminEventResultService` e `ArenaCatalogService`.

O catálogo Demo servia para explorar a interface, mas alguns fixtures ao vivo permaneciam ativos indefinidamente. Não havia uma jornada concentrada nem reset com escopo próprio. Os acessos públicos também precisavam de limites mais estreitos que os de um administrador operacional. Havia referências possíveis entre eventos internos e catálogos externos, inclusive no seed histórico que escolhia o primeiro campeonato da modalidade.

A solução acrescenta uma competição controlada, com evento histórico e uma rodada ativa, e reutiliza o domínio existente:

```text
POST /api/auth/demo → identidade persistida → JWT validado normalmente

POST /api/predictions
  → ArenaPredictionService → validações, débito e palpite persistido

POST /api/demo/events/{id}/result
  → DemoScenarioService (autorização, escopo e estado)
  → AdminEventResultService.record
  → ArenaCatalogService.recordResult
  → ArenaPredictionService.settleDerived
  → MarketSettlementEngine + PointWalletService
  → palpites liquidados e ledger persistido
  → ArenaPoolRankingService projeta a classificação
```

O modo demonstração simula apenas o acontecimento externo de conclusão da partida. Não calcula pontos no React, não introduz outra regra de pontuação e não usa temporizadores para fingir processamento.

`DemoScenarioFactory` cria os fixtures persistidos e o histórico usando os mesmos serviços de palpite e resultado. `DemoScenarioService` orquestra início, resultado, reset e manutenção. `DemoScenarioStore` mantém a geração e as referências da rodada em uma linha persistida, bloqueada com `FOR UPDATE` nos comandos. `ControlledDemoLifecycle` inicializa e mantém o cenário. No frontend, `DemoPage` reutiliza `EventCard`, `PredictionComposer`, modal e componentes do produto; `useDemoScenario` impede que uma resposta antiga de polling restaure visualmente uma rodada já substituída.

## Separação entre dados reais e Demo

| Regra | Garantia |
|---|---|
| Uma partida externa não pode ser simulada | Os serviços administrativos verificam a propriedade externa; os comandos Demo aceitam somente o evento ativo da competição controlada |
| O reset não alcança partidas reais | O comando não aceita uma lista de IDs nem campeonato arbitrário; resolve as referências armazenadas em `arena_demo_scenario` e valida seu escopo |
| Sports Sync não assume a competição Demo | O sync continua identificando catálogo e eventos por `externalProvider + externalId`; a competição Demo é interna e tem propriedade explícita |
| Catálogo interno e externo não se misturam | V12 acrescenta `provider_owned`, checks e FKs compostas para campeonato, times e participantes; o serviço também exige o mesmo provedor específico |
| A competição controlada tem escopo próprio | V13 acrescenta `demo_managed` e `demo_archived`, checks e FKs compostas entre competição, eventos e estado da jornada |

V12 não muda placares nem atribui IDs externos inventados. Se um evento interno de confronto direto estava ligado a um campeonato ou time externo, copia somente os metadados para um registro interno e redireciona a referência interna; o registro oficial permanece intacto. Se encontra um evento externo ligado a catálogo interno, interrompe a migração com diagnóstico para restaurar referências verificadas. Referências externas em eventos internos `RACE`/`INDIVIDUAL` também exigem revisão manual, porque códigos de competidores podem estar incorporados em classificações e mercados. A migração não tenta adivinhar esse vínculo.

V13 cria `arena_demo_scenario` e os campos de escopo sem apagar dados existentes. O Flyway aplica V12 como migração Java e V13 como SQL; o Hibernate continua com validação de schema.

## Permissões

`DemoAccessPolicy` resolve a identidade pelas contas persistidas e e-mails reservados da configuração, junto ao papel esperado. Flags enviadas pelo navegador e claims arbitrários não concedem acesso.

| Identidade | Ações permitidas na jornada |
|---|---|
| Participante Demo | Consultar cenário e registrar/cancelar seus palpites elegíveis na competição controlada |
| Administrador Demo | Consultar cenário, iniciar a rodada, informar resultado e iniciar nova rodada |
| Usuário normal, inclusive administrador operacional | Mantém permissões normais; não ganha acesso aos comandos reservados do cenário Demo |

As duas contas Demo não acessam `/api/admin/**`. A conta administrativa pública não administra usuários, catálogos, resultados externos ou configurações. IDs manipulados são revalidados pelo serviço contra a rodada vigente. Palpites da conta Demo fora da competição controlada são recusados; contas comuns também não registram palpites nessa competição.

O JWT mantém assinatura, expiração e verificação de usuário/role persistidos. Desabilitar acesso rápido não promove tokens Demo já emitidos a administradores comuns. O cadastro público recusa os e-mails reservados para impedir que alguém capture uma futura identidade Demo. Use identidades diferentes para Demo e administração operacional.

## Estados e processamento único

Uma rodada nasce `OPEN_FOR_PREDICTIONS`, com mercados pré-jogo. **Iniciar partida Demo** passa para `LIVE`, fecha os mercados e o prazo dos palpites. O resultado BO3 válido passa para `FINISHED` pelo fluxo de domínio existente; a validação recusa séries incompletas, empates e placares incompatíveis.

A chave de resultado é estável por evento. Repetir o mesmo resultado devolve o estado já aplicado; usar a mesma chave com um placar diferente gera conflito. As proteções do domínio e do ledger impedem recompensa duplicada. A partida encerrada não é reaberta pelo reset: uma nova partida é criada.

O ranking da competição é calculado pelo `ArenaPoolRankingService` a partir dos palpites liquidados do histórico de exemplo e da rodada atual, com os mesmos critérios de pontuação e desempate existentes.

## Reset sem apagar histórico

O comando **Começar nova rodada** é o reset da demonstração. A confirmação envia `expectedGeneration`, obtido do cenário mostrado na tela. Dentro da mesma transação:

1. bloqueia a linha de estado e confere a geração;
2. resolve e valida o evento ativo da competição controlada;
3. cancela uma rodada ainda não encerrada e reembolsa palpites ativos pelo serviço existente;
4. marca a rodada anterior como arquivada;
5. cria uma nova rodada aberta e seus mercados;
6. se necessário, repõe os pontos virtuais da conta configurada de Participante Demo;
7. grava a nova geração e registra auditoria.

Um retry da geração imediatamente anterior retorna o cenário atual sem criar outra rodada. Uma confirmação mais antiga gera conflito. O lock no banco coordena também réplicas da aplicação e comandos concorrentes; não depende de um mutex em memória.

Palpites liquidados, resultados, usuários, ledger e histórico global permanecem persistidos. Não há `TRUNCATE`, limpeza global, remoção de usuários ou reversão destrutiva de recompensas. Rodadas arquivadas deixam de compor a classificação corrente; o ranking da competição retorna ao histórico de exemplo. Isso não zera a carteira nem a progressão da conta.

A reposição alcança somente o participante configurado: quando o saldo fica abaixo de **5.000 pontos virtuais**, credita apenas o necessário para chegar a esse valor. O lançamento usa `INITIAL_BONUS` e uma chave por geração, sem gerar XP de atividade. Saldos acima do limite não são reduzidos, e contas normais não recebem reposição. Consultar a página ou repetir a inicialização não repete o crédito.

## Manutenção e fim das partidas Demo eternamente ao vivo

O cenário mantém apenas a rodada conduzida pelos visitantes como candidata ao estado `LIVE`. Se ela permanece ao vivo além do limite configurado, a manutenção a cancela e reembolsa os palpites ativos. Não fabrica vencedor nem placar final. A interface permite começar nova rodada.

Na inicialização do cenário, fixtures legados identificados pelo prefixo interno `demo-`, marcados Demo e sem provedor externo, que ainda estão ao vivo são cancelados pelo domínio. Eventos externos não entram nessa operação. Os schedulers e providers antigos não são responsáveis por conduzir a rodada controlada.

Enquanto a rodada permanece aberta, sua janela é renovada pela manutenção quando expira. Uma rodada iniciada ou encerrada não volta a aberta por essa manutenção.

## Configuração

| Variável | Padrão de runtime/Compose | Função |
|---|---|---|
| `APP_DEMO_ENABLED` | `false` | Provisionamento e acesso rápido das identidades Demo |
| `APP_DEMO_CONTROLLED_ENABLED` | `true` | Habilita o cenário quando o modo Demo também está ativo |
| `VITE_DEMO_MODE` | `false` no Compose | Exibe os acessos Demo; exige rebuild do frontend |
| `APP_DEMO_ROUND_WINDOW_HOURS` | `24` | Janela inicial da rodada; faixa aceita de 1 a 168 horas |
| `APP_DEMO_LIVE_TIMEOUT_MINUTES` | `15` | Tempo máximo ao vivo; faixa aceita de 1 a 120 minutos |
| `APP_DEMO_MAINTENANCE_MS` | `60000` | Intervalo e atraso inicial da manutenção |
| `APP_DEMO_ADMIN_EMAIL` / `APP_DEMO_PARTICIPANT_EMAIL` | Identidades Demo do `.env.example` | Contas reservadas, distintas das operacionais |
| `APP_DEMO_ADMIN_PASSWORD` / `APP_DEMO_PARTICIPANT_PASSWORD` | Vazias | Opcionais, somente para provisionamento no servidor |
| `APP_DEMO_LIVE_PROVIDER_ENABLED` / `APP_DEMO_LIVE_SCHEDULER_ENABLED` | `false` | Recursos legados; não são necessários para a jornada controlada |

O `.env.example` ativa intencionalmente as flags da jornada para apresentação local e deixa segredos vazios. Sem senha configurada, o seed preserva o hash existente ou gera uma senha aleatória para conta nova. O acesso rápido não precisa conhecer essa senha. Não exponha JWT, senha de banco ou token PandaScore em variáveis `VITE_*`.

`PANDASCORE_API_TOKEN`, `SPORTS_SYNC_ENABLED` e demais opções esportivas continuam independentes. O Demo funciona sem credencial externa; isso não comprova conectividade, cobertura ou live score do plano contratado.

## Endpoints

| Método e rota | Uso |
|---|---|
| `POST /api/auth/demo` | Reutilizado; recebe `profile: PARTICIPANT` ou `ADMIN` e retorna sessão JWT |
| `GET /api/demo/scenario` | Cenário, geração, evento, histórico, palpites da identidade atual, ranking e permissões |
| `POST /api/predictions` | Reutilizado para criação persistida; mesmas validações de mercado, pontos e idempotência |
| `POST /api/predictions/{id}/cancel` | Reutilizado para cancelamento elegível do próprio palpite |
| `POST /api/demo/events/{id}/start` | Inicia somente a rodada controlada atual |
| `POST /api/demo/events/{id}/result` | Recebe `homeScore` e `awayScore`; finaliza pelo serviço administrativo existente |
| `POST /api/demo/reset` | Recebe `expectedGeneration`; arquiva e cria a próxima rodada |

As rotas `/api/demo/**` exigem JWT e são registradas quando `APP_DEMO_ENABLED` e `APP_DEMO_CONTROLLED_ENABLED` estão ativos. Operações de escrita exigem Administrador Demo, exceto o palpite, que usa a rota normal com a política específica do participante.

## Testes e validação

As suítes do cenário, política de acesso, segurança HTTP/JWT e isolamento de catálogo estão em `DemoScenarioIntegrationTest`, `DemoAccessPolicyTest`, `DemoSecurityIntegrationTest`, `EventDataOwnershipTest` e `ProviderCatalogIsolationMigrationTest`. No frontend, `demo-scenario.test.tsx` cobre a jornada. Os testes não precisam de token esportivo real.

Execute a validação completa a partir da raiz:

```bash
cd backend
mvn -B verify
cd ../frontend
npm ci
npm run test:run
npm run typecheck
npm run lint
npm run build
cd ..
docker compose config --quiet
docker compose build backend frontend
```

Use uma instância PostgreSQL de teste para validar V1–V13 e repetir o roteiro acima com recarga da página entre etapas. Verifique também rejeições esperadas: participante tentando iniciar/finalizar/resetar; administrador Demo tentando acessar `/api/admin/**`; evento externo enviado ao endpoint Demo; resultado duplicado; confirmação de reset desatualizada. Confirme que dados oficiais permanecem intactos antes e depois do reset.

Na interface, confira desktop, tablet, 430 px, 390 px e 360 px, além de foco, teclado, modais e feedback. Os comandos acima são instruções de reprodução, não uma declaração de aprovação dos testes ou de cobertura visual já executada.

## Implantação e limites

Faça backup antes da atualização. Preserve o volume PostgreSQL, as variáveis PandaScore e as credenciais operacionais existentes. No `.env` privado do deploy de portfólio, habilite `APP_DEMO_ENABLED=true`, `APP_DEMO_CONTROLLED_ENABLED=true` e `VITE_DEMO_MODE=true`, mantendo os geradores legados de placar desativados. Depois de publicar o commit:

```bash
git pull --ff-only
docker compose config --quiet
docker compose build backend frontend
docker compose up -d --no-deps backend frontend
docker compose ps
docker compose logs --tail=100 backend
```

As migrations executam no startup. Uma recusa explícita da V12 exige corrigir referências verificadas; não remova checks, não altere o histórico do Flyway e não invente identidades externas para prosseguir.

A demonstração usa contas públicas compartilhadas, sem isolamento por visitante. Confirmações e locks evitam operações incoerentes, mas outro visitante pode conduzir a mesma rodada. O histórico é preservado e cresce com o uso; não foi adicionada uma política destrutiva de retenção. A reposição permite repetir testes e não representa valor monetário. A integração real continua dependendo de credencial e plano do provedor, com as [limitações de live score e correções oficiais documentadas](sports-integration.md).
