# Placar REST e treino ao vivo — decisão técnica, 08/10/2026

Base auditada: c90a68a; relatórios de mercados de 07/10 e auditoria/execução de 08/10 lidos antes das mudanças.

## Evidência de placar

Consulta autenticada REST em 08/10 13:38 SP: Lynn Vision × Chinggis Warriors, id 1731116, running, results 1–1 pelos IDs 126439/134264. games identifica mapa 3 running; não informa rounds nem nome do mapa. Todos os campeonatos da amostra têm live_supported=false. /lives HTTP200 retornou zero feeds. A flag PANDASCORE_LIVE_SCORES_ENABLED=false descarta resultados parciais que o REST já entrega. Usuário confirmou plano Free; não há comprovação de frames contratados.

Usar results como placar **da série**, games.position/status como mapa/jogo em andamento, nunca como rounds. Ausência permanece null; placar LIVE velho não é apresentado como atual. Resultado parcial não liquida mercados. Manter polling/backend, sem requests externos por visitante. Não criar consumidor WebSocket sem acesso/cobertura: frames CS2/LoL requerem Basic/Pro Live; Valorant não possui plano live equivalente.

## Isolamento do treino

Novo contexto opcional de treino no acesso rápido Participante, com sessão aleatória assinada pelo JWT e persistida. Conta normal/auth existente permanece intacta. Sessão, saldo, lançamentos e previsões usam tabelas próprias e FKs de leitura para evento/mercado/opção reais. Nenhum treino entra em arena_predictions, point_wallets, point_ledger ou projeções normais de ranking.

Token e sessão de treino duram 24h; o navegador conserva o token somente na aba. Novo acesso rápido inicia outro treino. Fechar a aba/logout encerra o acesso local; os registros anteriores ficam persistidos e a liquidação oficial pode ocorrer depois, sem expor o histórico a outro visitante. Para histórico permanente, usar uma conta normal. Não há exclusão automática de registros. Capacidade serializada no banco: 30 novas sessões/minuto, 1.000 sessões ativas, 100.000 sessões retidas e 100 palpites/sessão. Saturação de criação retorna 429/Retry-After; saldo insuficiente e regra inválida usam 422; chave conflitante usa 409.

Endpoints /api/training usam a sessão verificada do token; não aceitam ID de sessão do cliente. Token de treino não autoriza escrita normal, administração ou simulação. GETs privados de saldo/palpites do perfil compartilhado não ficam disponíveis nesse contexto. O frontend reutiliza o modal e páginas, identificando treino e encaminhando somente seus comandos/dados ao novo contrato.

Validar disponibilidade, suspensão, prazo, mínimo/máximo e multiplicador com as mesmas regras do palpite normal; ordem de locks evento→mercado→sessão. Idempotência única por sessão/chave, débito/crédito transacional e recompensa congelada. Liquidação usa engine existente, exclusivamente depois de resultado oficial completo/validado; cancelamento oficial reembolsa. Resultado em revisão não é liquidado. Treino nunca escreve a partida/mercado real.

Histórico/carteira/ranking do treino são próprios da sessão; o ranking não compara visitantes e não revela outros registros. A demonstração guiada compartilhada continua acessível por troca explícita de modo/perfil existente, sem misturar seu saldo com o treino.

## Publicação e validação

Migration aditiva, backup prévio, sem apagar PostgreSQL/volumes ou substituir dados reais. Testes sem token: HTTP de isolamento entre duas sessões/conta normal, mercados bloqueados, repetição/concorrência, liquidação/rollback e placar ausente/desatualizado. QA de navegador com evento sincronizado real e reprodução isolada autenticada para casos de término difíceis. Publicar somente após verify, PostgreSQL, frontend/Compose e CI.

Usuário confirmou autorização específica da PandaScore para o uso do produto com pontos virtuais. Credencial continua exclusivamente server-side; nenhum serviço contratado/contactado nesta rodada.

Fontes: [planos](https://developers.pandascore.co/docs/plan-reference), [WebSockets](https://developers.pandascore.co/docs/websockets-overview), [frames CS2](https://developers.pandascore.co/docs/data-sample-csgo), [/lives](https://developers.pandascore.co/reference/get_lives), [preços](https://www.pandascore.co/pricing), [termos](https://www.pandascore.co/terms-and-condition). Dados stream-synced podem ter atraso; feeds Low Latency possuem restrições distintas e não serão usados.
