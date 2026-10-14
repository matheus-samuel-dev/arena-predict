# Visão geral: limites de leitura e recuperação

## Defeito observado em produção

Em 14/10/2026, `/api/training/dashboard` levou 15,565 s, `/api/dashboard` 24,730 s e `/api/admin/dashboard` 18,185 s. O frontend interrompe leituras após 12 s. Carteira/notificações responderam em menos de 100 ms. Um thread dump capturou `LiveTrainingDashboardService → ArenaCatalogService.listEvents → HistoricalTeamStrengthService.revision`, com CPU acima de 100% de um núcleo e sem bloqueios PostgreSQL.

O treino convertia todos os eventos agendados em DTOs completos antes de selecionar oito. O dashboard normal fazia isso com todo o catálogo. A contagem administrativa também montava todos os DTOs para descartar seu conteúdo. A expansão do histórico tornou o trabalho repetido mais caro; não havia chamada PandaScore por visitante.

## Correção concentrada

- Consultas de dashboard aplicam ordenação/filtro/limite no banco antes de carregar mercados: oito próximos/agendados, seis destaques. O treino preserva SCHEDULED; a conta normal preserva SCHEDULED/OPEN_FOR_PREDICTIONS; destaques priorizam LIVE. Eventos arquivados/terminais são excluídos conforme a seleção existente.
- Ao vivo mantém a fonte completa usada pela página Ao vivo.
- A contagem administrativa carrega somente mercados OPEN de eventos candidatos e aplica as regras existentes de disponibilidade/opções. Não equivale a contar cegamente status OPEN. Bolões ativos usam COUNT SQL.
- Evidência histórica imutável é reaproveitada por provider/evento/modalidade/equipes/formato/horário confirmado. O cache tem limite de 512 entradas e é invalidado quando o reader muda. Não há reaproveitamento para snapshots sem horário confirmado. Falha no reader conserva o fallback seguro.
- Hash de revisão é calculado uma vez por conjunto escolhido, reutilizado no cache de modelos. Fórmulas, amostra mínima, janela, recompensa e multiplicadores aceitos não mudam. Validação de placar/idade continua no motor antes da confirmação.
- Atualizações silenciosas do recurso reutilizam uma solicitação pendente. Sucesso limpa erro anterior; respostas antigas não sobrescrevem retries. O dashboard repassa AbortSignal até fetch e cancela ao sair. Nenhum timeout ou intervalo foi aumentado.

O dashboard participante usa um único endpoint agregado, não um Promise.all de endpoints secundários. Não houve separação artificial do contrato nem preenchimento de indicadores ausentes. Após uma atualização em segundo plano falhar, dados já carregados são preservados; após recuperar, o erro inicial é removido.

## Verificação e limites

Fixtures transacionais verificam a seleção SQL antes do carregamento de mercados, arquivo Demo, ordem de destaques e disponibilidade da contagem. Testes de cache cobrem identidade/formato/tempo/correção/falha e ausência de dados posteriores ao snapshot. Frontend testa retry, recuperação silenciosa, cancelamento, respostas antigas e polling sem sobreposição.

O índice `idx_arena_events_status_starts` já atende à consulta de oito agendados (EXPLAIN ANALYZE: 1,466 ms na inspeção). Não foi criada migration ou índice sem evidência. A V19 permanece intacta. Não há mudanças em autenticação, carteiras, ranking ou liquidação.

A revisão encontrou oportunidades futuras de separar responsabilidades em ArenaCatalogService (685 linhas) e o arquivo AdminPages.tsx (1.500 linhas, vários componentes). Tamanho isolado não comprova defeito; esses arquivos não foram reescritos. Permanecem a necessidade de observabilidade de latência sustentada, limites de recursos na EC2 compartilhada e acompanhamento de volume por usuário. Os resultados desta correção não certificam carga comercial nem toda a arquitetura como livre de dívida técnica.

Medições finais, SHA, CI e navegação pública estão no relatório da rodada. Metas operacionais devem ser verificadas com a mesma infraestrutura e volume, não por limiares frágeis em testes unitários.
