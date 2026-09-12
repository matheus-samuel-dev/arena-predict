# Evidências de finalização do ArenaPredict

Revalidação local concluída em 12/9/2026, com pontos exclusivamente virtuais.

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
