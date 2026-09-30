# Entrega e validação da integração CS2

Data: 27/09/2026. Alterações implementadas no checkout local; nenhum deploy na EC2 foi executado.

## Estado da entrega

A implementação e as verificações locais estão concluídas. **A aceitação com dados reais recebidos da PandaScore permanece pendente de uma credencial válida.** Não havia token disponível neste ambiente. As fixtures são mocks de teste, não evidência de jogos reais consultados. O QA deixa isso explícito no painel administrativo e continua servindo o Demo.

## Resumo solicitado

| Item | Entrega |
|---|---|
| 1. Arquitetura encontrada | Spring Boot 3.3.5/Java 21, Spring Security/JWT, JPA, PostgreSQL 16, Flyway, React 18/TypeScript/Vite, Docker Compose/Nginx. Entidades, provider Demo, liquidação, carteira/ledger e ranking existentes foram reaproveitados. |
| 2. Arquitetura implementada | Cliente HTTP → DTOs/mapper PandaScore → `SportsDataProvider` → sincronização de catálogo/partidas → banco → endpoints existentes. Scheduler e estado operacional separados das regras de pontuação. |
| 3. Provedor | PandaScore REST para CS2, usando o prefixo oficial legado `/csgo/`; substituível pela interface do provider. |
| 4. Arquivos criados | Inventário completo em [sports-change-manifest.md](sports-change-manifest.md), incluindo adapter, sincronização, migration, testes/fixtures, componentes e documentação. |
| 5. Arquivos alterados | Mesmo inventário; mudanças incrementais em entidades/repos/serviços/DTOs/configuração e UI. Alterações prévias da área de comunidade foram preservadas. |
| 6. Migration | `V11__external_sports_data.sql`, aditiva. Identidade externa única em eventos/times/campeonatos, dados opcionais, processamento/revisão, índice de acompanhamento e estado/lease do scheduler. |
| 7. Ambiente | `PANDASCORE_API_TOKEN`, `SPORTS_SYNC_ENABLED` e controles opcionais de frequência/quota/timeout/cache. Lista completa e defaults em [sports-integration.md](../docs/sports-integration.md) e `.env.example`. Token somente no backend. |
| 8. Endpoints | Novo `GET /api/admin/sports-sync/status` protegido por ADMIN. Contratos existentes de eventos, campeonatos e administração ampliados com metadados; sem endpoint esportivo paralelo. |
| 9. Scheduler | Spring Scheduler, timestamps por feed e lease persistida; rede fora das transações e persistência individual por partida. |
| 10. Frequência | Tick local 30 s; próximas 15 min; ao vivo 120 s; próximas de começar/acompanhadas 120 s em lotes de 20; finais recentes 5 min; janela de correção 72 h. Configurável. |
| 11. Rate limit | Orçamento local 600 req/h, reserva de 20 pelo header do provedor, `Retry-After`, pausa em 429/401/403, no máximo 1 retry transitório, backoff e paginação limitada. |
| 12. Ao vivo | Status e placar de série quando efetivamente fornecido e habilitado. Ausência não vira zero. Flag live desligada por padrão; não implementa frames de rounds/WebSocket. |
| 13. Resultado | Final coerente com BO/placar/vencedor chama `ArenaPredictionService.settleDerived`, reaproveitando engine, carteira/progressão e ranking calculado dos palpites liquidados. |
| 14. Idempotência | UPSERT por provedor/ID externo, constraints, locks, mercados terminais, palpites ACTIVE, chaves únicas de ledger e `resultProcessedAt`/fingerprint na mesma transação. Correção posterior entra em revisão, sem novo crédito. |
| 15. Demo | Participante/Admin Demo e simulação mantidos; origem visível e bloqueios no backend impedem simulação/edição manual de eventos externos. |
| 16. Testes | Suite backend completa: 305 aprovados; após ajuste final de transporte, 59 testes de provider aprovados; PostgreSQL real: 21 aprovados. Frontend: 169 aprovados em 20 arquivos. Sem API real. |
| 17. Build frontend | Lint, TypeScript e Vite aprovados; imagem Docker final reconstruída. |
| 18. Build backend | Maven verify/JAR aprovados; imagem Docker Java 21 final reconstruída. |
| 19. Docker | Compose config aprovado; ambiente QA isolado com PostgreSQL 16, backend e Nginx. V1–V11 executadas; testes de integração no PostgreSQL e smoke test de autenticação/proxy. |
| 20. Limitações | Plano/cobertura reais não validados sem token. Live depende do payload/plano; sem placar fictício. Correções após liquidação são colocadas em revisão, sem compensação automática de XP/carteira. Correções além da janela de 72 h exigem investigação operacional. |
| 21. EC2 | Variáveis e comandos exatos de backup, pull, configuração, build e atualização em [sports-integration.md](../docs/sports-integration.md#atualização-da-ec2-depois-de-publicar-o-commit). Nenhum volume de produção foi alterado. |

## Evidências executadas

| Verificação | Resultado | Evidência local |
|---|---|---|
| `mvn -B verify` | 305 testes, 0 falhas/erros/ignorados; JAR gerado | `qa/sports-backend-final.log` |
| `mvn -B -Dtest=PandaScore*Test verify` após ajuste do transporte | 59 testes, 0 falhas/erros/ignorados; inclui corpo HTTP travado após headers e redirects | `qa/sports-provider-final.log` |
| Integração no PostgreSQL 16 | 21 testes, 0 falhas/erros/ignorados; Flyway e validação JPA | `qa/sports-postgres-tests.log` |
| `npm run test:run` | 169 testes em 20 arquivos | `qa/sports-frontend-tests.log` |
| `npm run lint` / `npm run typecheck` | Ambos aprovados | `qa/sports-frontend-lint.log`, `qa/sports-frontend-typecheck.log` |
| `npm run build` | Vite aprovado | `qa/sports-frontend-build.log` |
| Build Docker backend/frontend | Aprovado com Dockerfiles existentes; rebuild dos ajustes finais | `qa/sports-docker-build.log`, `qa/sports-docker-final.log`, `qa/sports-docker-frontend-final.log` |
| Runtime QA | Login dos dois perfis Demo, proxy Nginx e 403 para participante no status administrativo | [sports-runtime-validation.json](sports-runtime-validation.json) |
| Visual | Catálogo CS2 com origem Demo; painel compacto informa ausência de token | [Catálogo](sports-demo-catalog-desktop.png), [Painel](sports-admin-integration.png) |

Os 59 testes focados se sobrepõem à suite completa; esses números não devem ser somados como testes distintos. O último ajuste adicionou seis casos de transporte e foi revalidado no conjunto específico do provider. Os logs `.log` ficam ignorados pelo Git; este relatório e os JSON/screenshots preservam o resumo compartilhável.

## Cobertura funcional obrigatória

- `PandaScoreMapperTest`: fixtures upcoming/running/finished, status, scores por ID, dados ausentes/inconsistentes e BO/vencedor.
- `PandaScoreClientTest`, `PandaScoreTransportTest`, `PandaScoreSportsDataProviderTest`: indisponibilidade, timeout, 429/quota/backoff, paginação, autenticação server-side e payloads reais de contrato mockados.
- `SportsSyncIntegrationTest`: criação, atualização, prevenção de duplicatas/concorrência e transições.
- `ExternalMatchSettlementIntegrationTest`: finalização, liquidação, ranking, replay sem crédito duplicado, cancelamento/reembolso e proteção de eventos reais contra operações manuais/Demo.
- `SportsSyncReviewIntegrationTest`: alterações oficiais e finais incompletos expirados em revisão.
- `SportsSyncServiceTest`: orquestração, frequências/estado/falhas.
- Testes existentes de Demo permanecem ativos; frontend cobre origem, placares nulos/reais, formatos legados, logos e atualização visível.

Para concluir a validação externa, configure o token no `.env` da EC2, habilite o sync e confira uma sincronização bem-sucedida no painel, eventos com `externalProvider=PANDASCORE`, horários/equipes/campeonatos correspondentes ao provedor e cobertura real do plano. Só habilite placares live após confirmar essa cobertura.
