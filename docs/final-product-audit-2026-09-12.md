# ArenaPredict — auditoria final de produto

Revalidação concluída em 12 de setembro de 2026 sobre a aplicação real em
Chromium, Spring Boot e PostgreSQL 16, no ambiente Docker local isolado. Todo o
produto continua usando exclusivamente pontos virtuais, sem depósito, saque ou
qualquer valor financeiro.

## 1. Problemas encontrados

- datas absolutas faziam os fixtures envelhecerem enquanto o processo ficava no ar;
- o ranking tinha apenas oito perfis e pouco histórico temporal;
- logos remotos podiam quebrar e usuários novos repetiam o mesmo retrato padrão;
- um palpite podia ser persistido pelo backend, mas o frontend desistia após 12 s
  sob carga e mostrava uma falha enganosa;
- tarefas de histórico executavam em contextos de teste sem todos os fixtures;
- documentação e auditoria de navegador ainda descreviam números fixos antigos.

## 2. Causa dos palpites indisponíveis

A autorização antiga combinava datas absolutas vencidas, status legado do evento
e prazo global, enquanto frontend e backend inferiam disponibilidade de formas
diferentes. A regra agora é centralizada por mercado no
`MarketAvailabilityService`, considera relógio, estado, janela e modalidade de
uso (`PRE_MATCH_ONLY` ou `LIVE_ENABLED`) e devolve motivo específico. A manutenção
temporal do demo reposiciona os fixtures estruturais sem reabrir mercado
cancelado/liquidado nem apagar ações do visitante.

## 3. Arquivos alterados

As mudanças finais abrangem bootstrap e manutenção demo, catálogo de
participantes, ranking, configurações, contratos de teste, fallback de logos e
avatares, timeout idempotente do palpite, Docker, README e auditoria Chromium. A
lista versionável completa está em [`qa/changed-files.txt`](../qa/changed-files.txt).

## 4. Arquitetura adotada para demo data

`DemoParticipantCatalog` é a fonte determinística de pessoas. O seed continua
idempotente; `ArenaDemoScheduleMaintainer` ancora doze fixtures estruturais em
janelas relativas ao instante atual; `ArenaRankingDemoInitializer` cria e
renova históricos coerentes para os três períodos. As rotinas só atuam quando o
modo demo está explícito e preservam estados administrativos, palpites,
liquidações, cancelamentos e dados criados pelo visitante.

## 5. Número de usuários demo

O painel administrativo confirma **25 usuários**: **24 participantes** do
ranking e **1 administrador**. Os participantes possuem nomes, saldos,
resultados, precisão, sequência e evolução diferentes. O Jogador Demo ocupa uma
posição compatível com seu histórico persistido.

## 6. Número de eventos demo

A base validada contém **29 eventos persistidos**: 5 agendados, 5 abertos para
palpites, 2 ao vivo, 16 encerrados e 1 cancelado. Doze são fixtures estruturais
mantidos no eixo passado/ao vivo/futuro; os demais preservam histórico e cenários
E2E liquidados.

## 7. Mercados pré-jogo disponíveis

Existem **47 mercados `PRE_MATCH_ONLY` disponíveis agora**. Eles fecham quando o
evento começa e exibem “Encerrado após o início” em vez de uma indisponibilidade
genérica. Os testes cobrem as fronteiras T−1 h, T−1 min e T+1 s.

## 8. Mercados ao vivo disponíveis

Existem **35 mercados `LIVE_ENABLED` elegíveis e abertos** no conjunto atual;
**4 estão interativos agora** nos dois eventos em andamento. Mercados suspensos,
já decididos, encerrados, em processamento, liquidados e cancelados usam estados
e explicações próprias.

## 9. Alterações visuais

Foram refinados grids, densidade e espaços mortos; agrupamentos de ações do ao
vivo e de mercados; legibilidade de tabelas, filtros, badges e foco; consistência
do nome Counter-Strike 2; microcopy de exploração de opções; e fallbacks locais.
Times sem asset recebem escudo determinístico. Pessoas sem retrato recebem
iniciais e gradiente único, inclusive após falha de uma URL antiga.

Na rodada final de UX, os cards de mercado passaram a priorizar título, resumo,
estado, opções e multiplicadores. Regras de liquidação, fechamento, empate e
cancelamento permanecem disponíveis em “Entenda o mercado”. A seleção ganhou
estado com check e `aria-pressed`, e a confirmação passou a apresentar um recibo
com evento, escolha, pontos, multiplicador, potencial e horário. A sidebar do
Admin agora separa visualmente Experiência e Administração, preserva seções e
adapta a navegação inferior ao contexto atual. Tabelas usam truncamento com
ajuda contextual e filtros sem resultado exibem um empty state sem paginação
contraditória.

## 10. Telas corrigidas

Foram revisitadas as áreas públicas de login, visão geral, eventos/detalhes, ao
vivo, palpites, bolões, ligas, rankings, estatísticas, desafios, conquistas,
comunidade, pontos e conta. No Admin: painel, modalidades, campeonatos,
competidores, eventos, mercados, resultados, usuários, bolões, pontuação,
engajamento, moderação, relatórios, auditoria e configurações. O participante e
o administrador consomem os mesmos eventos e mercados, com permissões distintas.

## 11. Testes executados

- backend: **160 testes**, zero falhas, erros ou ignorados;
- frontend: **138 testes em 17 arquivos**, todos aprovados;
- testes focados de avatar/conta: **10/10**;
- auditoria Chromium: **128 combinações**, incluindo participante, Admin,
  detalhes esportivos e temas claro/escuro;
- fluxo real confirmado: seleção, revisão, persistência, débito, recibo,
  Meus palpites, carteira e ranking.

## 12. Resultado dos builds

`npm run lint`, `npm run typecheck` e `npm run build` passaram sem warnings; o
Vite processou 1.602 módulos. O empacotamento Spring Boot e as imagens Docker de
backend/frontend passaram. A versão final do frontend foi reconstruída
isoladamente e os três serviços PostgreSQL, API e web ficaram saudáveis.

## 13. Breakpoints verificados

Foram inspecionados **1920, 1536, 1440, 1366, 1280, 1024, 768, 430, 390 e 360
px**, incluindo cards, filtros, rankings, tabelas, detalhes de eventos, sidebar e
rotas administrativas. A execução final registrou zero overflow horizontal.

## 14. Console e rede

A matriz final registrou **zero erros de console**, **zero falhas de página**,
**zero campos visíveis sem label** e nenhuma resposta inesperada 401, 403, 404
ou 500. O falso erro de timeout ao confirmar palpite sob carga foi corrigido com
uma janela de 30 s, mantendo a idempotência do backend contra duplo débito.

## 15. Limitações restantes

O provider ao vivo e os multiplicadores são simulados; resultados estruturados
continuam sendo registrados pelo Admin; não há criação dinâmica de próximos
mapas/segmentos nem prêmio automático de temporada. A suíte de integração usa
H2, complementada por esta validação real em PostgreSQL. Não houve teste formal
de carga, auditoria WCAG certificada, observabilidade externa nem execução do
Chromium no CI. A instância pública ainda exige segredos fortes e configuração
de produção; não deve publicar as credenciais do `.env` local.

## Veredito

**PRONTO PARA PUBLICAÇÃO**

O veredito considera o escopo demonstrativo de portfólio: produto populado,
coerente, navegável, responsivo e funcional de ponta a ponta com pontos virtuais.
Não constitui aprovação para operação financeira ou promessa de integração com
um provedor esportivo real.
