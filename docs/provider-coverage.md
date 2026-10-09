# Cobertura dos providers reais

Estado publicado auditado em 09/10/2026: **PandaScore autenticada e operacional para CS2, LoL e Valorant**, com placar da série REST quando disponível. Os quatro adapters tradicionais continuam sem credenciais. Fixtures automatizadas não são prova de acesso real. A visão administrativa é a autoridade sobre a saúde atual.

| Modalidade | Adapter | Configuração privada | Capabilities implementadas | Contratos publicados | Situação |
|---|---|---|---|---|---|
| Futebol | API-FOOTBALL v3 | `API_FOOTBALL_KEY`, `API_FOOTBALL_ENABLED=true` | Agenda, LIVE, placar, equipes, campeonatos, resultado de 90 min | Resultado, dupla possibilidade, gols, ambas marcam, placar exato; somente pré-jogo | READY_FOR_CREDENTIAL |
| Basquete | API-BASKETBALL v1 | `API_BASKETBALL_KEY`, `API_BASKETBALL_ENABLED=true` | Agenda, LIVE, placar com prorrogação, quartos, equipes, campeonatos | Vencedor, handicap, totais, margem; somente pré-jogo | READY_FOR_CREDENTIAL |
| Tênis | API-Tennis | `API_TENNIS_KEY`, `API_TENNIS_ENABLED=true` | Singles, agenda, LIVE, sets, games completos, atletas e torneios | Vencedor; formato desconhecido bloqueia placar exato/total/handicap de sets | READY_FOR_CREDENTIAL |
| CS2 | PandaScore Fixtures | `PANDASCORE_API_TOKEN`, `SPORTS_SYNC_ENABLED=true` | Agenda, LIVE, séries, equipes, torneios, resultado | Contratos da série quando BO1/3/5 confirmado | OPERATIONAL na auditoria |
| Valorant | PandaScore Fixtures | Mesma configuração PandaScore | Igual CS2; estatísticas de rounds não anunciadas | Contratos da série confirmada | OPERATIONAL na auditoria |
| League of Legends | PandaScore Fixtures | Mesma configuração PandaScore | Agenda, LIVE, resultado da série; código canônico `LEAGUE_OF_LEGENDS` | Contratos da série confirmada | OPERATIONAL na auditoria |
| Automobilismo | API-FORMULA-1 v1 | `API_FORMULA1_KEY`, `API_FORMULA1_ENABLED=true` | Corridas F1, calendário, LIVE, voltas quando disponíveis, classificação final e DNF | Nenhum: classificação parcial/DNF e grid ainda não permitem prometer liquidação dos contratos atuais | READY_FOR_CREDENTIAL para importação; mercados bloqueados |
| Vôlei | Sem adapter nesta entrega | Dependente de outro provider | Dataset Demo existente preservado | Contratos Demo existentes | DEMO_TEMPORARILY |
| Futebol americano | Sem adapter nesta entrega | Dependente de outro provider | Dataset Demo existente preservado | Contratos Demo existentes | DEMO_TEMPORARILY |
| Dota 2 | Sem adapter nesta entrega | Precisa validar cobertura/plano antes de habilitar | Dataset Demo existente preservado | Contratos Demo existentes | DEMO_TEMPORARILY |

Não há odds externas. O motor atual está documentado em [probability-model-v2.md](probability-model-v2.md): eSports usam histórico oficial suficiente ou prior equilibrado, condicionados ao placar confirmado. Modalidades tradicionais têm regras próprias, ainda sem validação com providers reais. Limites de recompensa real 1,10–8,00; modelo Demo legado preservado. Probabilidade, confiança, origem e multiplicador aceito são auditáveis. Nenhum coeficiente é uma odd oficial da PandaScore.

## Contratos e limites

- Futebol: `NS/TBD → SCHEDULED`; `1H/HT/2H/ET/BT/P/LIVE → LIVE`; `FT/AET/PEN → FINISHED`; `PST/INT/SUSP → POSTPONED`; `CANC/ABD/AWD/WO → CANCELLED`. LIVE exibe `goals`, inclusive durante prorrogação. Na finalização, contratos de 90 minutos usam `score.fulltime`, nunca pênaltis; a UI identifica esse escopo e preserva placar do evento e pênaltis como informações distintas. Resultados incompletos aguardam dados/revisão.
- Basquete: `NS → SCHEDULED`; `Q1/Q2/Q3/Q4/OT/BT/HT → LIVE`; `FT/AOT → FINISHED`; `POST/SUSP → POSTPONED`; `CANC/AWD/ABD → CANCELLED`. `scores.*.total` inclui prorrogação. Não assume duração de quarto que o payload não fornece.
- Tênis: `event_live=1`/`Set N → LIVE`, `Finished → FINISHED`; cancelamento/retirada/walkover anulam; adiamento/interrupção suspendem. `event_final_result` representa sets. Games são somados somente quando todos os sets finais estão presentes. Não infere BO3/BO5 por gênero ou torneio. Doubles ficam fora do adapter: identidade estável da dupla ainda não validada. Status não reconhecido falha explicitamente.
- F1: `Scheduled/Live/Completed/Cancelled/Postponed` mapeiam os estados correspondentes. Não interpreta calendário como corrida ao vivo. `position=0`/DNF permanece sem posição inventada. Só importa sessões `Race`, não quali/prática como corrida.
- PandaScore: mantém mapping documentado e score ao vivo opt-in. Fixtures não garante rounds/objetivos; esses mercados não são publicados. Alias antigo `LOL` é normalizado na entrada.
- API-Sports futebol/basquete: horizonte conservador de hoje+amanhã, último dia encerrado, além do acompanhamento de eventos importados por ID. Basquete consulta também ontem para eventos que cruzam meia-noite UTC. F1 consulta próximas duas corridas e última corrida, com chamada de classificação final. Cobertura de campeonatos/temporadas depende do plano; não significa cobertura universal.
- Cache curto de respostas por parâmetros, janela local de minuto/hora/dia, limite de tamanho, timeout, retry limitado para falha transitória, `Retry-After`, pausa persistida no estado da sincronização e limite de IDs acompanhados. Sem chamadas ao provider nas requisições dos usuários. Budget local de referência: 10/h, 100/dia; ajustar explicitamente ao plano. Polling inicial dos novos adapters: 15 minutos, portanto é um snapshot e não uma transmissão de baixa latência.
- Limites horários/diários em memória são por processo; a pausa gravada no banco e a lease protegem réplicas, mas um orçamento distribuído durável de consumo ainda não existe. Headers do fornecedor continuam sendo a autoridade. Não anunciar alta disponibilidade multirréplica com quota compartilhada sem essa extensão.
- Paginação remota inesperada/mensagem de erro HTTP 200 não vira feed vazio saudável. Falha preserva eventos persistidos. Snapshot idêntico é NO-OP; só a marca de leitura é renovada. Correção após liquidação exige revisão e nunca refaz pagamentos automaticamente.
- Não há fallback automático para fornecedor com identidade diferente: preservar externalProvider/externalId impede duplicar/transferir o dono de um evento. Registry seleciona candidato por modalidade e prioridade, mas esta entrega tem um fornecedor por modalidade. Último snapshot persistido é o fallback de disponibilidade.

## Licenciamento e direitos

Documentação técnica consultada:

- [API-FOOTBALL: autenticação, fixtures e status](https://www.api-football.com/news/post/how-to-get-started-with-api-football-the-complete-beginners-guide).
- [API-BASKETBALL: contrato de games](https://api-sports.io/documentation/basketball/v1).
- [API-FORMULA-1: races e rankings/races](https://api-sports.io/documentation/formula-1/v1).
- [API-Tennis: fixtures e livescore, GET/POST](https://api-tennis.com/documentation).
- [PandaScore: documentação](https://developers.pandascore.co/docs).
- [Termos API-FOOTBALL](https://www.api-football.com/terms).

**Autorização comercial, redistribuição, atribuição obrigatória e licença dos logos não estão confirmadas para os planos que o usuário ainda não contratou.** A documentação API-Sports informa que marcas/imagens podem exigir autorização adicional dos titulares. Guardar uma URL não concede direitos sobre o asset. O adapter preserva logos locais e aceita apenas URLs HTTPS sem credenciais. Cache/persistência obedecerão às condições do plano efetivamente contratado; validar retenção e redistribuição antes de uso comercial. Para API-Tennis, não foi confirmado contrato público suficiente para afirmar licença comercial. Esta documentação não promete direitos comerciais nem substitui os termos contratados.

## Ativação exata

Configurar o par chave/flag correspondente no `.env` privado de `/home/ubuntu/arena-predict` na EC2 `18.231.73.103`, sem Git, e recriar somente o backend com `docker compose up -d --no-deps backend`. No Compose deste projeto o serviço se chama `backend`; o container publicado é `arenapredict-api`. Ajustar quotas/intervalo ao plano antes de habilitar. Localmente: `.env` privado usado pelo Compose ou ambiente do processo Java; reiniciar o backend. Nunca colocar estas chaves no Vite/frontend.

Após ativar: consultar a visão administrativa `/api/admin/sports-sync/providers`, confirmar tentativa/HTTP/sucesso e comparar amostras reais com banco, `/api/events?page=0&source=REAL`, `/api/events/live` e dashboard. HTTP 200 sem parsing/importação não prova a cadeia completa. Zero LIVE é válido somente após consulta bem-sucedida. Enquanto as chaves faltam, a página mantém o empty state e o admin vê a falta de configuração.
