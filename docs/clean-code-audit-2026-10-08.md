# Auditoria pragmática de código — 08/10/2026

Prioridade inicial: provar cadastro/login/palpite no navegador publicado e a jornada Demo completa. A conta normal própria de QA recebeu 5.000 pontos, registrou 25 pontos no evento PandaScore EAC Extra × THE UNIT e recebeu confirmação/persistência em Meus palpites; a movimentação foi -25 e +100 da conquista existente. Demo foi registrado e liquidado no desktop e mobile, com 20 × 3,50 = 70 pontos e ranking. Não se ampliou a autorização Demo para partidas externas nem se clonaram eventos reais como demonstração.

## Cobertura e método

Inventário estruturado de todos os arquivos rastreados em backend, frontend, testes, migrations e configuração: 290 arquivos no recorte inicial, incluindo 158 classes Java de produção, frontend React/TypeScript/CSS, 15 arquivos Flyway até V16, Docker/Compose/Nginx e workflow CI. Métricas e evidências detalhadas ficam no relatório de execução fora do Git. Foram inspecionados contratos, dependências, consultas, autenticação, permissões, logs, timers, boundaries e testes. Revisão profunda dos fluxos críticos abaixo; isto não é uma certificação formal de ausência de vulnerabilidades em todas as linhas.

| Área | Evidência revisada / decisão |
|---|---|
| Auth | AuthService/AuthContext/Auth API; registro normal sempre PARTICIPANTE, sem demoProfile; BCrypt/JWT e confirmação de identidade persistida; timeout de cadastro reproduzido no navegador |
| Autorização | SecurityConfig, JwtAuthenticationFilter, DemoAccessPolicy e controllers administrativos; roles verificadas no banco; comandos Demo protegidos; correção da listagem pública de usuários |
| Mercados | EsportsMarketFactory, MarketTemplateService, MarketDefinitionCatalog, MarketAvailabilityService; contratos armazenados e gates reaproveitados; correção da precedência de estados terminais |
| Palpites | ArenaPredictionService, controller/DTO e PredictionComposer; opção pertence ao mercado/evento, saldo/prazo/limites no backend, expectedMultiplier e chave idempotente; navegador publicou recibo válido |
| Pontos/settlement | PointWalletService, MarketSettlementEngine, SportsMatchSyncService, ledger e constraints; marcador/chave única e transações; aggregate SQL substitui hidratação de todo extrato administrativo |
| Ranking/estatísticas | ArenaPoolRankingService/ArenaDashboardService e repositórios; projeções dos palpites resolvidos; não alteradas regras de pontos, desempate ou filtros |
| Demo | DemoScenarioService/factory/policy, hooks e DemoPage; cenário separado/compartilhado, histórico arquivado, mesma engine; CTA permite preparar rodada encerrada com confirmação existente |
| Provider/scheduler | registry/adapters, bounded transport, state store, lease e coordinator; sem refazer PandaScore ou chamadas externas por requisição de usuário; limites/backoff preservados |
| Banco | entities/repositories/migrations; identidade externa, ledger/idempotência, locks, índices de status/tempo e opções; nenhum índice criado sem plano/medição |
| Frontend | hooks/contexts/API/shared components e páginas; requests antigos por conta protegidos, polling sem sobreposição preservado, timers limpos e estados de erro mantidos |
| Config/CI | secrets via environment, Compose/volumes, Nginx e três jobs CI; orçamento do proxy alinhado; lint acrescentado à CI |

## Problemas concretos corrigidos

| Gravidade | Problema | Correção verificável |
|---|---|---|
| P1 experiência | Mercado aberto com conta Demo parece bloqueio sem saída evidente | DemoPredictionAccess centraliza explicação e CTA reutilizado; eventos reais continuam protegidos; rodada encerrada oferece preparação explícita |
| P1 privacidade | Admin Demo mascarava email, mas enumerava carteiras/perfis de visitantes normais | Consulta paginada restrita às identidades demonstrativas; admin normal mantém dados completos; teste HTTP garante visitante não enumerado |
| P2 confiabilidade | Registro persistido seguido de timeout leva visitante a repetir cadastro | Budget de auth 30s e uma reconciliação por login com credenciais fornecidas, só em falha de transporte; nunca repete escrita ou converte erro de validação em login |
| P2 privacidade/estado | Refresh antigo de saldo/notificações e leitura de notificações pode completar após troca de conta | Gate por token em todos os callbacks, limpeza antes da pintura da nova identidade, loading zerado no logout; testes de corrida |
| P2 consistência | CLOSED/DRAFT subordinado à qualidade de snapshot LIVE exibia espera em mercado encerrado | Estado persistido definitivo avaliado antes do gate LIVE; não reabre; integração cobre perda de score/staleness |
| P2 infraestrutura | Proxy 15s interrompe comandos cujo client budget é 30s | Proxy read timeout bounded de 35s, mantendo timeout de conexão e controles atuais |
| P2 performance | Dashboard administrativo carregava todos os lançamentos só para somar valores absolutos | Query agregada SUM(ABS(amount)), mesma semântica e nenhum contrato JSON alterado; teste com débito/crédito e carteira |
| P2 pipeline | ESLint rodava localmente, mas não era gate de CI | Step npm run lint acrescentado |
| P3 legibilidade | Bônus e constante diziam Demo mesmo para contas normais | INITIAL_VIRTUAL_POINTS e descrição de novos créditos; valor permanece 5.000, registros históricos não reescritos |

## Redundâncias efetivamente removidas

- Textos/CTAs de restrição Demo divergentes em lista de mercados e modal: componente de apresentação único, sem regra de autorização nova.
- Recargas de carteira/notificações em qualquer alteração do objeto de sessão (ex.: nome/avatar): dependência agora é a identidade do token.
- Resets duplicados de estados privados na troca de sessão: concentrados no commit de layout.
- Query de busca de usuário por nome que ficou sem consumidores após o filtro Demo e dois imports não utilizados no transporte.
- Hidratação/redução de entidades de ledger para total: substituída por agregação SQL.

Não se removeu validação de UX por repetir uma validação do backend: o servidor permanece autoridade e o frontend precisa apresentar o estado corretamente. Não se confundiu dupla leitura após adquirir lock com redundância: ela protege concorrência/idempotência.

## Fonte de verdade do motor

| Regra | Autoridade |
|---|---|
| Identidade/perfil | Usuário persistido + JwtAuthenticationFilter/DemoAccessPolicy |
| Permissão para prever | DemoAccessPolicy no comando; predictionReadOnly somente espelha UX |
| Disponibilidade/prazo | MarketAvailabilityService, sob locks em ArenaPredictionService |
| Coeficiente confirmado | Serviço de pricing do backend; expectedMultiplier confere a escolha |
| Snapshot do palpite | ArenaPrediction persistido com origem/versão/potencial |
| Saldo | PointWalletService e ledger transacional |
| Resultado externo | Snapshot oficial normalizado/importado, identidade externa e ownership |
| Settlement | MarketSettlementEngine + ArenaPredictionService; marcador/chaves de ledger |
| Pontuação/ranking | Projeções de palpites resolvidos e ProgressionService existentes |
| Demo | Cenário demonstrativo e competição dedicada; nenhuma simulação em evento externo |

## Problemas documentados sem reescrita

- **P2 escala:** endpoint legado de eventos sem paginação e algumas projeções de dashboard/bolões/ranking ainda materializam coleções. O catálogo paginado já usa Specifications/EntityGraph e carrega mercados/opções/participantes em lotes. Não se declarou N+1 universal sem medição. Evoluir projeções/SQL com benchmarks e equivalência dos desempates; não remover contratos legados silenciosamente.
- **P2 manutenção:** AdminPages.tsx (~1.500 linhas), ArenaCatalogService (~656), API client (~633), AppShell (~543) e CSS (~7.240) concentram responsabilidades. Extração por recurso é recomendada por demanda; uma reorganização de centenas de linhas agora não comprovaria ganho e arriscaria regressões.
- **P2 disponibilidade:** contratos LIVE sem score seguem só vencedor; séries incompletas/correções oficiais aguardam revisão. Não é motivo para inventar resultado ou estatística. Polling/caps/licenciamento dependem do provider.
- **P2 rede:** useApiResource invalida respostas antigas; nem todos os loaders encaminham AbortSignal. Cancelamento físico completo requer atualizar os consumidores e testes de cada página. Não se alterou o contrato do hook global sem essa cobertura.
- **P3 modelos:** modelos virtual REAL v1 e Demo v2 possuem partes combinatórias semelhantes, mas probabilidades/curvas/contextos distintos. Unificá-los por DRY superficial poderia alterar multiplicadores já validados; não foi feito.
- **P3 formatação:** métodos compactos e módulos grandes permanecem. Não se realizou reformatação em massa ou mudança de arquitetura por estética.
- **Limitação intencional:** Demo é compartilhado; outro visitante pode conduzir a rodada. Aviso e CTA de recuperação são explícitos. Sandbox por visitante/clonagem baseada em evento real exigiria identidade, armazenamento e ranking próprios; a alternativa simples autorizada evita esse risco.

## Preservações

Não foram removidas modalidades, páginas, mercados, comunidade, bolões, ligas, avatars, administração ou adapters ainda aguardando credencial. Não houve migração nova, rewrite de ranking, alteração do intervalo PandaScore, reset de PostgreSQL ou segredo no frontend. Histórico de carteira permaneceu íntegro; apenas novos bônus usam descrição correta. Correção inicial de UX foi entregue separadamente da auditoria.
