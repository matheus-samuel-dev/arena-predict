# Cenário demonstrativo para portfólio

O acesso rápido **Participante Demo** abre `/app` usando a identidade demonstrativa persistida, com carteira, histórico, ranking, competições e progressão. Esse cenário é compartilhado e não representa uma conta privada. O botão **Entrar no treino ao vivo**, já disponível em `/demo`, continua criando uma sessão privada com carteira/histórico separados e referências somente de leitura às partidas reais.

## Execução controlada

O enriquecimento exige, simultaneamente:

```dotenv
APP_DEMO_ENABLED=true
APP_DEMO_CONTROLLED_ENABLED=true
APP_DEMO_PORTFOLIO_SEED_ENABLED=true
```

A última flag é **false por padrão**, tanto no Spring quanto no Compose. Ao iniciar o backend com ela habilitada, o lifecycle executa `DemoPortfolioSeedService.seed()` depois da inicialização dos catálogos e da competição controlada. Não é exposto um endpoint de seed público ou um comando administrativo genérico com privilégios ampliados.

A transação usa o mutex persistido da demonstração e um recibo versionado `DEMO_PORTFOLIO_SEED / demo-portfolio-v1`. Reexecutar ou reiniciar o backend não repete eventos, previsões, débitos ou recompensas. Uma falha reverte o lote inteiro. Não há exclusão, TRUNCATE, reset de volume ou alteração das identidades reais.

O reset existente da rodada guiada permanece restrito àquela competição: cancela/reembolsa a rodada anterior e cria outra. O histórico de portfólio, as carteiras reconciliadas e os eventos oficiais permanecem preservados.

## Conteúdo

- Reutiliza os **24 participantes**, avatares, campeonatos internos, bolões e liga existentes.
- Seis retrospectos explicitamente Demo: futebol (29 dias), basquete (20), tênis (13), LoL (8), Valorant (4) e CS2 (2).
- Usa **10 templates existentes**, com 17 combinações evento/mercado: vencedor, totais, escanteios e resultados exatos aplicáveis à modalidade. Nenhum contrato novo.
- Cada perfil recebe 17 previsões históricas e duas pendentes: **456 novas previsões**, sendo 408 liquidadas e 48 ativas. Os resultados e seleções são fixtures determinísticas.
- Um bolão próprio do Jogador Demo reúne o novo histórico multiesportivo, usando criação e adesão do domínio. Os bolões legados mantêm seus proprietários; a liga existente agrega os palpites elegíveis conforme as datas de inscrição e temporada, sem ignorar as regras de elegibilidade.
- Pontos são debitados por `ArenaPredictionService` / `PointWalletService`; resultados passam por `AdminEventResultService` e `MarketSettlementEngine`; conquistas/desafios por `ProgressionService`.

## Datas e honestidade dos dados

Previsões históricas recebem `placedAt` antes de avaliar a progressão. Isso impede que uma importação de partidas antigas conte como três palpites de hoje. As definições e metas dos desafios permanecem as existentes. A quantidade de desafios concluídos depende das atividades e da janela vigente; o seed não escreve `completed` ou `unlocked` diretamente.

`resolvedAt` representa o instante fictício do resultado demonstrativo. O ledger e a auditoria registram quando o backend realmente processou a importação. Portanto, recompensas históricas importadas podem aparecer no extrato no dia do seed: não se fabrica uma data passada de processamento da carteira. As curvas existentes continuam derivadas de previsões persistidas.

O perfil principal já havia desbloqueado as cinco conquistas disponíveis antes desta preparação. Não se removem conquistas ou recompensas válidas para produzir artificialmente um estado bloqueado. Metas adicionais exigem definições explicitamente aprovadas e isolamento do cenário Demo.

As datas dos novos retrospectos são fixadas na primeira execução e não são reescritas a cada reinício. Os desafios mantêm suas janelas reais; após a virada do dia, atividade de ontem não deve aparecer como atividade de hoje. Não se recriam palpites diariamente para fingir usuários ativos.

## Validação

`DemoPortfolioSeedIntegrationTest` cobre os totais de fixtures, repetição do seed, liquidação, precisão/ranking, desempenho temporal, ledger, restrição a eventos internos, data histórica, preservação de conta normal, papel administrativo e reset. A mesma suíte integra o job PostgreSQL da CI. Os testes usam transações revertidas e não deixam participantes ou previsões extras no banco de outras suítes.

Para conferir a integridade de uma carteira, some todos os lançamentos do ledger e compare ao saldo. Para cada previsão vencedora, a recompensa deve coincidir com `potentialPoints` calculado a partir do multiplicador aceito; perdedoras não recebem pontos. Repetir seed ou resultado não pode criar novo lançamento.
