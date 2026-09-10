# ArenaPredict — relatório de finalização

Atualizado em 9 de setembro de 2026. O produto continua restrito a pontos virtuais, sem depósito, saque ou dinheiro real. A validação usa a aplicação real em Chromium, Spring Boot e PostgreSQL 16, no ambiente local isolado `http://localhost:5174`. Os dados do ambiente original foram preservados.

## 1. Causa do espaço branco nos cards

As listagens usavam CSS Grid com o alinhamento padrão `stretch`: o card curto ocupava a altura da linha definida pelo maior vizinho. Alturas mínimas adicionais no confronto e nos cards compactos ampliavam o vazio. Corridas, por possuírem mais participantes, expunham o problema com frequência.

## 2. Correção aplicada

Os grids de eventos, destaques, compactos e detalhes usam `align-items: start`; cards usam `align-self: start`, `height: auto` e `min-height: 0`. O confronto perdeu a altura mínima artificial. O rodapé permite quebra de linha e a prévia de corrida é compacta. A correção não depende de esconder o conteúdo do card com `overflow: hidden`. A auditoria mediu cards com alinhamento próprio e conferiu visualmente desktop e celular. A listagem prioriza eventos ao vivo e próximos eventos; o histórico aparece depois, do mais recente ao mais antigo.

## 3. Causa de “Aberto” versus “Indisponível”

O rótulo vinha do estado legado do evento, enquanto a confirmação usava a fase do evento e um prazo global. Assim, um evento podia continuar anunciado como aberto depois de seu limite, e a passagem para ao vivo bloqueava todos os mercados. O frontend também deduzia disponibilidade sem compartilhar a decisão do backend.

## 4. Nova regra de disponibilidade

`MarketAvailabilityService` fornece `allowed`, código, rótulo e motivo. A mesma regra é usada na projeção da API e na confirmação do palpite sob bloqueio transacional. Ela considera estado do evento, estado do mercado, opções ativas, abertura, fechamento e modo pré-jogo/ao vivo.

- Pré-jogo encerra no início ou no fechamento configurado. O prazo legado do evento é usado apenas quando falta prazo próprio do mercado.
- Mercados habilitados ao vivo podem continuar; os exclusivos ao vivo aguardam a confirmação de evento ao vivo.
- Suspensão, fechamento, liquidação, cancelamento, adiamento e espera por atualização possuem mensagens específicas.
- Um evento só anuncia abertura quando `availableMarketCount > 0`. O frontend não usa o antigo enum de evento para habilitar uma seleção.
- O catálogo demo mantém mercados de fatos iniciais, como pistol e primeiro objetivo, apenas no pré-jogo. Ainda não existe abertura automática de próximos segmentos.
- Mercados cujo resultado o placar ao vivo já revelou são bloqueados. Exemplos: gols acima de 2,5 com placar 2 × 1; ambas marcam com gol dos dois times; handicap −1,5 em BO3 depois de o adversário vencer um mapa. Uma série com placar completo também fecha, ainda que o operador não tenha marcado o evento como encerrado.

## 5. Mercados por modalidade

O catálogo atual tem os conjuntos abaixo. As contagens são de definições para um evento padrão, não do total de registros históricos.

| Modalidade | Quantidade | Seleção implementada |
| --- | ---: | --- |
| Futebol | 9 | Resultado pré-jogo, resultado ao vivo, dupla possibilidade, total de gols, ambas marcam, placar correto, primeiro tempo, total de escanteios e total de cartões |
| Basquete | 8 | Vencedor com prorrogação, handicap de pontos, total, vencedor do primeiro tempo, vencedor do primeiro quarto, total da primeira equipe, margem e total do primeiro tempo |
| Tênis | 8 | Vencedor, primeiro set, total de sets, handicap de sets, placar em sets, total de games, handicap de games e tie-break |
| CS2 | 10 em BO3/BO5 | Série, primeiro mapa, total de mapas, handicap de mapas, placar da série, total de rounds, handicap de rounds, primeiro pistol e os dois halves do primeiro mapa |
| Valorant | 8 em BO3/BO5 | Série, primeiro mapa, total de mapas, handicap de mapas, placar da série, total de rounds, handicap de rounds e primeiro pistol |
| League of Legends | 10 em BO3/BO5 | Série, primeiro jogo, total de mapas, handicap de mapas, placar da série, primeiro abate, primeira torre, primeiro dragão, primeiro Barão e total de abates |
| Automobilismo/F1 | 7 com seis pilotos | Vencedor, Top 3, Top 5, confronto entre dois pilotos, volta mais rápida, safety car físico e classificação de um piloto |
| Vôlei | 6 | Vencedor, primeiro set, total de sets, handicap de sets, placar em sets e total de pontos |
| Futebol americano | 8 | Resultado com prorrogação, handicap, total, primeiro tempo, primeiro quarto, total da primeira equipe, margem e total do primeiro tempo |
| Dota 2 | 8 em BO3/BO5 | Série, primeiro jogo, total de mapas, handicap de mapas, placar da série, primeiro abate, primeiro abate do Roshan e total de abates |

BO1 omite os três mercados de extensão/placar da série. Top 5 exige mais de cinco pilotos. Tênis e basquete não oferecem empate no vencedor final. Detalhes e fontes conceituais estão em [market-reference.md](market-reference.md).

## 6. Arquitetura dos mercados

`MarketDefinitionCatalog` concentra as diferenças esportivas em definições com categoria, estratégia, métrica, linha, opções, multiplicadores, modo de disponibilidade, descrição e campos de resultado. `MarketTemplateService` gera mercados sem duplicá-los. A definição é fotografada no registro para que alterações futuras no catálogo não mudem uma regra já publicada.

`MarketAvailabilityService` decide a disponibilidade; `MarketSettlementEngine` valida dados e calcula vencedores; `ArenaPredictionService` coordena débitos, créditos e estados. A migração V8 acrescenta as janelas, modo, categoria, template, definição persistida e dados estruturados do resultado.

Mapa do domínio auditado: evento em `ArenaEvent`; modalidade em `Sport`; campeonato em `Championship`; mercado em `PredictionMarket`; opção em `MarketOption`; palpite em `ArenaPrediction`; saldo em `PointWallet`; transação de pontos em `PointLedgerEntry`. O resultado é armazenado no evento, com placar, classificação dos `EventParticipant` e `resultData`; não foi criada uma entidade `Result` redundante. O estado legado `OPEN_FOR_PREDICTIONS` permanece por compatibilidade, mas não autoriza nenhuma seleção: essa decisão usa os mercados e suas janelas.

A página do evento renderiza somente a categoria selecionada. A listagem carrega mercados, opções e participantes em consultas de lote. O Admin possui geração por modalidade e informações de janela, tipo de uso e liquidação. Resultados usam o schema dos mercados daquele evento, agrupado por categoria, em vez de um formulário universal fixo.

## 7. Liquidação e pontos virtuais

Existem estratégias reutilizáveis de vencedor, dupla possibilidade, total, total da primeira equipe, handicap, ambas marcam, placar exato, margem, seleção estatística, posição e confronto entre pilotos. Uma previsão Top 3 pode ganhar simultaneamente com outras seleções Top 3.

Ao confirmar, o backend persiste o multiplicador e o retorno potencial, arredondado para baixo em pontos inteiros, e debita o saldo na mesma transação. O acerto credita o potencial salvo; o erro não recebe retorno; o cancelamento devolve os pontos utilizados. Igualdade na linha, quando aplicável, também reembolsa.

Registrar resultado e liquidar pode ocorrer na mesma transação. Estatísticas obrigatórias ausentes causam falha e rollback de placar, estados e créditos; não são tratadas como zero. Mercados automáticos não aceitam uma seleção vencedora manual que contradiga o resultado. Chaves de idempotência incluem os dados estruturados; uma repetição não gera novo pagamento. A liquidação bloqueia evento, mercados e carteiras em ordem consistente. Mercados históricos manuais continuam no fluxo legado explícito.

A revisão esportiva acrescentou validação do fechamento de sets no vôlei, rounds regulamentares/prorrogação de CS2 e Valorant e combinação possível entre games, sets e tie-break do tênis. O modelo de tênis é explicitamente de tie-break em 6 × 6; formatos alternativos exigem outro template. Os testes de domínio cobrem esses limites. O cálculo do boletim usa aritmética inteira para reproduzir o arredondamento do backend, inclusive multiplicadores com três casas decimais.

## 8. Bolão e liga

Ambos reutilizam `ArenaPool`, mas `PoolType` agora determina comportamento e autoridade.

| Bolão | Liga |
| --- | --- |
| Grupo social criado por participante | Competição pública criada pelo Admin |
| Público ou privado, convite e criador responsável | Temporada com início e fim obrigatórios |
| Ranking dos palpites associados ao grupo | Ranking automático de palpites elegíveis após inscrição, dentro do período e escopo |
| Selecionável no boletim | Não precisa ser selecionada no boletim |

As páginas filtram o tipo correto, explicam as regras e exibem ações diferentes. Ligas não têm distribuição automática de prêmio de temporada; o demo não anuncia prêmio a pagar. Desafios mantêm suas recompensas virtuais existentes.

## 9. Filtros e tema

Inputs, selects e menus nativos foram alinhados aos tokens de superfície e texto, incluindo `option`, placeholder, hover, foco e disabled. A folha do tema define `color-scheme` claro/escuro. A inspeção visual encontrou também uma regra antiga que escurecia inputs ao receber foco e outra que deixava os blocos da classificação cinza: ambas foram corrigidas. Filtros ativos, mensagens de erro, resumo da seleção, potencial e aviso de pontos virtuais usam cores do tema com contraste legível. O filtro de modalidade perdeu a fonte antiga de 10,5 px. Os dois temas foram exercitados pela interface.

## 10. Dados demo

O seed cobre as dez modalidades cadastradas, com doze eventos ativos nomeados para receber os templates, incluindo futebol e CS2 ao vivo. F1 tem seis pilotos e classificação; tênis usa uma série de sets. Há exemplos de suspensão em escanteios ao vivo e pistol do Valorant, fechamento no tie-break de tênis e cancelamento no primeiro Barão de LoL. O evento cancelado histórico conserva seus reembolsos; um novo evento encerrado aguarda liquidação com nove mercados tipados. Eventos concluídos/liquidados históricos são preservados. O retrospecto de automobilismo agora usa classificação, sem placar de futebol, mantendo os pagamentos históricos do confronto entre pilotos.

A validação criou somente cinco eventos adicionais para o E2E obrigatório. Eles permanecem encerrados e liquidados, permitindo conferir a evidência. O teste complementar cancelou um mercado ao vivo e preservou seu palpite reembolsado. O upgrade de demo não recria palpites nem altera os multiplicadores salvos. A renovação das datas do demo desloca também as janelas dos mercados, preservando cancelamentos e liquidações.

## 11. Testes adicionados e ajustados

- `MultimarketIntegrationTest`: fronteira exata de horário; pré-jogo/live; evento sem mercados disponíveis; suspensão/fechamento/cancelamento sem débito; mercados específicos das dez modalidades; vencedores e perdedores; Top 3 com múltiplos vencedores; cancelamento e devolução; replay de resultado; definição persistida; BO1/BO3/BO5; rollback quando faltam dados.
- `ArenaPredictionIntegrationTest`: continuidade dos contratos de saldo, multiplicador, débito, idempotência, estorno e cancelamento concorrente com os novos estados.
- Contratos administrativos e de criação foram ajustados à geração por templates e à distinção de autoridade entre bolão e liga.
- `market-cards.test.tsx`: mercado ao vivo válido mesmo com prazo global vencido; mercado suspenso; ausência de autorização do backend; card que não anuncia abertura pelo enum legado.
- `prediction-composer.test.tsx`: recibo de confirmação permanece visível até reconhecimento do usuário.
- `MarketSettlementRulesTest`: estratégias puras, limites de placar, empate, devolução, rounds, sets e estatísticas específicas.
- `market-results.test.tsx`: formulários por schema, dados obrigatórios, foco no erro, placar parcial, classificação, confirmação e bloqueio de duplo envio.
- `market-categories.test.tsx`: categorias de mercados e renderização somente do grupo selecionado.
- Testes adicionais: consulta do catálogo limitada a seis queries; liga conta apenas previsões elegíveis após inscrição; cancelamento concorrente; resultado administrativo com dados estruturados na chave; fechamento de resultados já conhecidos ao vivo; regra de cancelamento igual na Visão geral e em Meus palpites; classificação histórica de corrida.

## 12. Testes executados

As execuções finais passaram com **158 testes backend, zero falhas/erros e nenhum teste ignorado**, e **135 testes frontend em 17 arquivos**, sem falhas. O pacote backend terminou com `BUILD SUCCESS` às 18:51 de 9/9/2026. Logs locais: `backend/target/finalization-package.log` e `frontend/finalization-frontend.log`.

## 13. Builds

`mvn -B package` executa os testes e empacota o JAR Spring Boot. `npm run test:run`, `npm run typecheck` e `npm run build` passaram. As imagens Docker de backend e frontend foram construídas e a aplicação iniciou com PostgreSQL e migração V8. A confirmação final usa a mesma versão após a última reconstrução. As dependências Maven e os assets externos foram verificados com acesso normal à rede; bloqueios do sandbox foram separados dos erros da aplicação.

## 14. E2E e navegador

A auditoria inicial percorreu Visão geral, Eventos/detalhes, Ao vivo, Meus palpites, Bolões, Ligas, Rankings, Estatísticas e Desafios, além da operação administrativa de esportes, campeonatos, participantes, eventos, mercados, resultados e pontuação. Essa auditoria identificou os defeitos; não equivale a testar a implementação final.

Os cinco eventos de teste foram preparados pela API administrativa; **as seleções, confirmações de palpite e registros de resultados foram feitos pela interface real**. A verificação de saldo, transações e repetição usou também os endpoints autenticados.

| Palpite | Modalidade / mercado | Pontos | Multiplicador | Resultado | Crédito |
| --- | --- | ---: | ---: | --- | ---: |
| 105 | Futebol / gols acima de 2,5 | 40 | 1,90 | Acerto, placar 3 × 1 | 76 |
| 106 | Futebol / gols abaixo de 2,5 | 30 | 1,85 | Erro | 0 |
| 107 | CS2 / rounds do mapa 1 acima de 21,5 | 40 | 1,90 | Acerto, mapa 13 × 10; série 2 × 1 | 76 |
| 108 | Basquete / pontos do Boston acima de 108,5 | 40 | 1,90 | Acerto, 115 × 101 | 76 |
| 109 | Tênis / games acima de 22,5 | 40 | 1,90 | Acerto, 2 × 1 sets; 17 × 14 games | 76 |
| 110 | F1 / Norris no Top 3 | 40 | 1,65 | Acerto, segundo colocado | 66 |

- Saldo: **7.018 → 6.788 após 230 pontos de débito → 7.158 após 370 pontos de crédito**.
- Os resultados liquidaram **42 mercados**. Cinco previsões venceram e uma perdeu. Cada vencedora teve exatamente um crédito; a perdedora, nenhum.
- Repetir as cinco requisições de resultado manteve o saldo e os créditos. O ranking subiu 370 pontos, cinco acertos e seis previsões finalizadas; o participante chegou à primeira posição.
- Recarregar o navegador e recriar os contêineres preservou previsões, liquidação e saldo no PostgreSQL.
- Palpite 111: resultado ao vivo, 10 pontos, multiplicador 1,80; débito correto, cancelamento administrativo pelo navegador, reembolso de 10 pontos, repetição com zero novos reembolsos.
- Auditoria participante: **85 combinações de página, resolução e tema**, sem overflow, campos sem label, erros de console ou respostas HTTP de erro. Resoluções: 1920 × 1080, 1440 × 900, 1366 × 768, 1024 × 768, 768 × 1024, 430 × 932, 390 × 844 e 360 × 800.
- Auditoria Admin: oito áreas em 1440 e 360 px, sem overflow, campos sem label ou erros de execução/API.
- Teclado: seleção por Enter, foco inicial em pontos, contenção de Tab no boletim, bloqueio de valor inválido; mercado suspenso e resultado já conhecido sem opções habilitadas. Formulário F1 e placar correto com 26 opções verificados em 360 px.
- A última checagem confirmou filtro por modalidade, menu nativo nos dois temas, fonte de 14 px, campo focado com superfície correta, saldo insuficiente e Escape devolvendo o foco ao botão de origem nos dois modais. O reembolso do palpite 111 persistiu após a reconstrução final. Backend, frontend e PostgreSQL ficaram saudáveis nos healthchecks Docker.

Evidências: [fluxo principal](../qa/e2e-evidence.json), [persistência e matriz de navegador](../qa/browser-audit.json), [ao vivo, cancelamento e Administração](../qa/additional-e2e.json), [última checagem visual e de foco](../qa/final-ui-check.json), imagens na pasta `qa/`. O script `qa/browser-audit.cjs` repete a auditoria sobre esta base de validação. Esses testes não equivalem a uma certificação formal de acessibilidade nem cobrem todos os botões de todas as páginas.

## 15. Arquivos alterados

Principais arquivos desta rodada, relativos à raiz do repositório:

- Backend/API: `arena/api/ArenaDtos.java`, `AdminOperationsDtos.java`, `ArenaAdminController.java`.
- Backend/domínio e dados: `arena/domain/ArenaEnums.java`, `ArenaEvent.java`, `PredictionMarket.java`; `db/migration/V8__market_rules_and_structured_results.sql`.
- Backend/serviços: `MarketDefinitionCatalog.java`, `MarketTemplateService.java`, `MarketAvailabilityService.java`, `MarketSettlementEngine.java`, `ArenaPredictionService.java`, `ArenaCatalogService.java`, `AdminEventResultService.java`, `AdminOperationsService.java`, `ArenaDashboardService.java`, `ArenaPoolRankingService.java` e ajustes de carteira conforme revisão de concorrência.
- Backend/repositórios: `PredictionMarketRepository.java`, `ArenaPredictionRepository.java`, `LeaguePredictionRepository.java`.
- Demo: `ArenaDemoInitializer.java`, `ArenaMarketDemoInitializer.java`.
- Frontend: `app/presentation.ts`, `components/EventCard.tsx`, `components/PredictionComposer.tsx`, `pages/EventsPage.tsx`, `pages/AdminPages.tsx`, `pages/DashboardPage.tsx`, `pages/PoolsPage.tsx`, `services/api.ts`, `types/index.ts`, `styles.css`, `markets.css`.
- Testes: `MultimarketIntegrationTest.java`, `ArenaPredictionIntegrationTest.java`, `ArenaCreationContractsIntegrationTest.java`, `AdminOperationsIntegrationTest.java`, `frontend.test.tsx`, `prediction-composer.test.tsx`, `market-cards.test.tsx`, além de testes da revisão final ainda em curso.
- Documentação: `docs/market-reference.md` e este relatório.

As classes Java estão sob `backend/src/main/java/com/bolao/copa/`; testes sob `backend/src/test/java/com/bolao/copa/`; migrações sob `backend/src/main/resources/`; caminhos frontend sob `frontend/src/`. A lista completa de arquivos de implementação/documentação está em [qa/changed-files.txt](../qa/changed-files.txt); inclui também `ArenaRankingDemoInitializer`, `ArenaRankingIntegrationTest`, `MarketSettlementRulesTest` e os novos testes frontend. A pasta `qa/` contém os scripts e evidências desta rodada. Configurações locais e credenciais permanecem ignoradas pelo Git.

## 16. Bugs encontrados

Foram identificados e tratados no código: abertura anunciada sem mercado elegível; bloqueio indiscriminado ao iniciar evento; falta de liquidação derivada de estatísticas; falta de variedade esportiva; repetição de regras genéricas; cards esticados; filtros com fundo incoerente; páginas bolão/liga duplicadas; definição de mercado sem snapshot; idempotência administrativa que ignorava estatísticas; e risco de ordem de bloqueios inconsistente entre eventos com usuários em comum.

A execução encontrou e corrigiu ainda: prévia de retorno com erro de arredondamento binário; leitura concorrente obsoleta no cancelamento; alteração de estrutura de mercados já publicados; ação de cancelamento de mercado apontando para o endpoint genérico; modalidade não exibida na tabela administrativa; fundos escuros em inputs com foco no tema claro; fonte pequena nos selects; contraste fraco de filtros e avisos; lista de eventos começando pelo histórico antigo; classificação de corrida histórica apresentada como placar.

## 17. Pendências reais e classificação

Limites assumidos: provider demonstrativo e resultados estatísticos registrados pelo Admin; multiplicadores simulados persistidos; mercados de segmentos iniciais restritos ao pré-jogo; ausência de próximos mapas dinâmicos e de pagamento automático de prêmio de temporada. Mercados opcionais sem dados confiáveis, como construtores na F1, foram deliberadamente excluídos. O legado manual histórico continua identificado. A suíte backend usa H2 para integração; o E2E real usa PostgreSQL. Não foi realizado teste de carga nem auditoria formal WCAG.

**Classificação: PRONTO PARA PORTFÓLIO.** A aprovação se apoia em palpites confirmados pela interface, débitos e multiplicadores persistidos, resultados registrados pelo Admin, 42 mercados liquidados, ganhos/perdas/reembolso conferidos, repetição sem crédito duplicado, ranking atualizado e persistência após recriar a aplicação. A última matriz de navegador voltou a passar nas 85 combinações, sem overflow, erros de console, respostas HTTP de erro ou campos sem label. Não restou bloqueio conhecido no fluxo central validado. Os limites de demonstração acima continuam explícitos; esta classificação não é uma aprovação para operação com dinheiro real nem uma certificação de produção.
