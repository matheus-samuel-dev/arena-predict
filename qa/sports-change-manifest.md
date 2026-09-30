# Arquivos da integração esportiva

Inventário do checkout em 27/09/2026. As alterações permanecem locais, sem commit criado por esta execução.

## Criados

- `backend/src/main/java/com/bolao/copa/arena/api/SportsSyncAdminController.java`
- `backend/src/main/java/com/bolao/copa/arena/config/SportsSyncProperties.java`
- `backend/src/main/java/com/bolao/copa/arena/config/SportsSyncScheduler.java`
- `backend/src/main/java/com/bolao/copa/arena/service/EventDataOwnership.java`
- `backend/src/main/java/com/bolao/copa/arena/service/provider/pandascore/PandaScoreClient.java`
- `backend/src/main/java/com/bolao/copa/arena/service/provider/pandascore/PandaScoreDtos.java`
- `backend/src/main/java/com/bolao/copa/arena/service/provider/pandascore/PandaScoreMapper.java`
- `backend/src/main/java/com/bolao/copa/arena/service/provider/pandascore/PandaScoreProperties.java`
- `backend/src/main/java/com/bolao/copa/arena/service/provider/pandascore/PandaScoreSportsDataProvider.java`
- `backend/src/main/java/com/bolao/copa/arena/service/provider/pandascore/PandaScoreStatusMapper.java`
- `backend/src/main/java/com/bolao/copa/arena/service/provider/SportsChampionship.java`
- `backend/src/main/java/com/bolao/copa/arena/service/provider/SportsMatch.java`
- `backend/src/main/java/com/bolao/copa/arena/service/provider/SportsProviderException.java`
- `backend/src/main/java/com/bolao/copa/arena/service/provider/SportsTeam.java`
- `backend/src/main/java/com/bolao/copa/arena/service/sync/SportsCatalogSyncService.java`
- `backend/src/main/java/com/bolao/copa/arena/service/sync/SportsMatchSyncService.java`
- `backend/src/main/java/com/bolao/copa/arena/service/sync/SportsSyncService.java`
- `backend/src/main/java/com/bolao/copa/arena/service/sync/SportsSyncStateStore.java`
- `backend/src/main/resources/db/migration/V11__external_sports_data.sql`
- `backend/src/test/java/com/bolao/copa/arena/ExternalMatchSettlementIntegrationTest.java`
- `backend/src/test/java/com/bolao/copa/arena/service/provider/pandascore/PandaScoreClientTest.java`
- `backend/src/test/java/com/bolao/copa/arena/service/provider/pandascore/PandaScoreMapperTest.java`
- `backend/src/test/java/com/bolao/copa/arena/service/provider/pandascore/PandaScoreSportsDataProviderTest.java`
- `backend/src/test/java/com/bolao/copa/arena/service/provider/pandascore/PandaScoreTransportTest.java`
- `backend/src/test/java/com/bolao/copa/arena/service/sync/SportsSyncServiceTest.java`
- `backend/src/test/java/com/bolao/copa/arena/SportsSyncIntegrationTest.java`
- `backend/src/test/java/com/bolao/copa/arena/SportsSyncReviewIntegrationTest.java`
- `backend/src/test/resources/pandascore/finished-match.json`
- `backend/src/test/resources/pandascore/README.md`
- `backend/src/test/resources/pandascore/running-matches.json`
- `backend/src/test/resources/pandascore/upcoming-matches.json`
- `docs/sports-integration.md`
- `frontend/src/app/sportsData.ts`
- `frontend/src/components/EventDataSource.tsx`
- `frontend/src/components/SportsSyncSummary.tsx`
- `frontend/src/hooks/useVisibleRefresh.ts`
- `frontend/src/test/sports-data.test.tsx`
- `frontend/src/test/visible-refresh.test.tsx`
- `qa/sports-admin-integration.png`
- `qa/sports-change-manifest.md`
- `qa/sports-demo-catalog-desktop.png`
- `qa/sports-runtime-validation.json`
- `qa/sports-validation.md`

## Alterados pela integração

- `.env.example`
- `README.md`
- `backend/src/main/java/com/bolao/copa/CopaApplication.java`
- `backend/src/main/java/com/bolao/copa/arena/api/AdminOperationsDtos.java`
- `backend/src/main/java/com/bolao/copa/arena/api/ArenaAdminController.java`
- `backend/src/main/java/com/bolao/copa/arena/api/ArenaDtos.java`
- `backend/src/main/java/com/bolao/copa/arena/domain/ArenaEvent.java`
- `backend/src/main/java/com/bolao/copa/arena/domain/Championship.java`
- `backend/src/main/java/com/bolao/copa/arena/domain/Competitor.java`
- `backend/src/main/java/com/bolao/copa/arena/repository/ArenaEventRepository.java`
- `backend/src/main/java/com/bolao/copa/arena/repository/ChampionshipRepository.java`
- `backend/src/main/java/com/bolao/copa/arena/repository/CompetitorRepository.java`
- `backend/src/main/java/com/bolao/copa/arena/service/AdminEventResultService.java`
- `backend/src/main/java/com/bolao/copa/arena/service/AdminOperationsService.java`
- `backend/src/main/java/com/bolao/copa/arena/service/ArenaCatalogService.java`
- `backend/src/main/java/com/bolao/copa/arena/service/ArenaPredictionService.java`
- `backend/src/main/java/com/bolao/copa/arena/service/DemoLiveEventService.java`
- `backend/src/main/java/com/bolao/copa/arena/service/DemoProbabilityEngine.java`
- `backend/src/main/java/com/bolao/copa/arena/service/MarketAvailabilityService.java`
- `backend/src/main/java/com/bolao/copa/arena/service/MarketDefinitionCatalog.java`
- `backend/src/main/java/com/bolao/copa/arena/service/MarketSettlementEngine.java`
- `backend/src/main/java/com/bolao/copa/arena/service/provider/SportsDataProvider.java`
- `backend/src/main/resources/application.yml`
- `backend/src/test/resources/application-test.yml`
- `docker-compose.yml`
- `frontend/src/components/AppShell.tsx`
- `frontend/src/components/EventCard.tsx`
- `frontend/src/components/TeamLogo.tsx`
- `frontend/src/pages/AdminPages.tsx`
- `frontend/src/pages/DashboardPage.tsx`
- `frontend/src/pages/EventsPage.tsx`
- `frontend/src/pages/HelpPage.tsx`
- `frontend/src/pages/LoginPage.tsx`
- `frontend/src/pages/PerformancePages.tsx`
- `frontend/src/services/api.ts`
- `frontend/src/styles.css`
- `frontend/src/test/market-cards.test.tsx`
- `frontend/src/test/market-results.test.tsx`
- `frontend/src/types/index.ts`

EventCard.tsx, EventsPage.tsx, styles.css e market-cards.test.tsx já tinham alterações locais anteriores; elas foram preservadas ao incorporar esta integração.

## Alterações preexistentes preservadas

- `frontend/src/pages/CommunityPage.tsx` — fora das mudanças da integração esportiva.
- `frontend/src/test/community.test.tsx` — fora das mudanças da integração esportiva.

## Artefatos locais ignorados

Logs de build/testes (`qa/sports-*.log`), dependências/targets e configuração privada do QA (`.env.sports-qa`, `compose.sports-qa.local`) não integram o commit. Nenhuma credencial real PandaScore foi fornecida ou criada.
