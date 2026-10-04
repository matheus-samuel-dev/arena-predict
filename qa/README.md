# Evidências de finalização do ArenaPredict

A rodada atual está no [relatório de consolidação de 04/10/2026](../docs/consolidation-report-2026-10-04.md).
As evidências atuais são `consolidation-browser.json` (produto completo, oito larguras),
`consolidation-product-flow.json` (palpite pelo detalhe normal, resultado, recompensa e ranking),
`consolidation-admin-layout.json` (revalidação do aviso administrativo em mobile),
`consolidation-postgresql-suites.json` (cinco suítes no PostgreSQL),
`consolidation-validation.json` (builds e testes) e `demo-api-e2e.json` (idempotência e reset).
As capturas `consolidation-*.png` pertencem a esta rodada e foram inspecionadas visualmente.

Com o Compose local de QA saudável em `http://localhost:5176`, Playwright no `NODE_PATH`
e Chrome instalado (ou `CHROME_PATH`), execute em sequência:

```powershell
node qa/consolidation-product-flow.cjs
node qa/consolidation-browser.cjs
pwsh -File qa/demo-api-e2e.ps1
```

Para repetir somente o navegador administrativo: defina `ARENA_QA_PROFILE=admin`.
Remova essa variável para a execução completa dos dois perfis.

Os scripts de fluxo alteram somente a rodada Demo compartilhada do ambiente local.
Não salvam JWTs. As demais evidências abaixo são históricas e mantêm a data de sua execução.

O [relatório da demonstração controlada de 30/09/2026](demo-validation.md) reúne
o inventário, isolamento de dados, reset, resultados de testes e builds desta evolução.
A [evidência E2E da API](demo-api-e2e.json) registra o fluxo executado no Compose de QA;
o [script](demo-api-e2e.ps1) permite reproduzi-lo. A [evidência do navegador](demo-browser-validation.json)
registra a jornada, teclado, console e medidas DOM em cinco larguras. A captura
de screenshots da rodada final teve timeout; esse limite fica explícito no relatório.

A [validação da integração esportiva](sports-validation.md) documenta a etapa anterior.
Os arquivos listados abaixo pertencem à revalidação local de **12/09/2026**, com
pontos exclusivamente virtuais, e não comprovam a nova jornada Demo controlada.

- `e2e-evidence.json`: cinco eventos preparados para o teste, seis palpites confirmados pelo navegador, resultados registrados pela interface administrativa, transações, ranking e repetição idempotente.
- `browser-audit.json`: persistência em PostgreSQL, 128 combinações de páginas/resoluções/temas, medidas de overflow, labels, cores de selects e respostas de rede. A execução final encontrou zero overflow, campos sem label, falhas de página, erros de console ou respostas HTTP de erro.
- `additional-e2e.json`: palpite ao vivo, cancelamento administrativo, reembolso único, teclado, mercados bloqueados e páginas administrativas.
- `final-ui-check.json`: última conferência após reconstruir os contêineres; estados demo, reembolso persistido, filtros nativos, cores de foco, saldo insuficiente e devolução de foco ao fechar os modais. O script correspondente é `final-ui-check.cjs`.
- Imagens PNG: capturas reais de Chromium, para revisão visual.
- `changed-files.txt`: arquivos de implementação e documentação alterados nesta rodada.

`browser-audit.cjs` requer Playwright disponível no `NODE_PATH` e Google Chrome instalado (ou `CHROME_PATH`). Use `ARENA_QA_URL` para mudar a origem; o padrão é `http://localhost:5174`. O script entra pelo acesso demo, lê a aplicação real, navega e muda o tema pela interface. Não grava tokens nem depende de IDs ou saldos fixos: confirma a presença de eventos ao vivo, mercados abertos, histórico de palpites e 24 posições nos rankings semanal, mensal e geral. Ele audita um ambiente previamente inicializado; não prepara automaticamente uma base vazia.

Com os contêineres locais saudáveis:

```powershell
$env:NODE_PATH = 'C:/Users/LENOVO/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules'
node qa/browser-audit.cjs
```

Os testes de domínio reproduzíveis sem estes fixtures E2E estão nas suítes backend e frontend. Para recriar manualmente o fluxo: criar eventos das cinco modalidades, gerar mercados pelo catálogo administrativo, confirmar os mercados da tabela do relatório, registrar placar e métricas compatíveis, marcar encerramento e liquidação, conferir Meus palpites, Pontos e Rankings, repetir o resultado e recarregar a aplicação.
