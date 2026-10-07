# Mercados reais de previsão — PandaScore

Os dados esportivos são da PandaScore. Os contratos de previsão e multiplicadores de pontos são definidos pelo ArenaPredict. Nenhum coeficiente é uma odd oficial do fornecedor ou uma promessa de retorno financeiro. Os pontos não têm valor financeiro.

## Causa e correção

A sincronização já criava contratos para partidas futuras, mas chamava `MarketTemplateService` somente em SCHEDULED. O catálogo ainda forçava todos os contratos externos para PRE_MATCH_ONLY. Partidas descobertas LIVE não recebiam contratos; as descobertas antes do início tinham seus contratos pré-jogo encerrados corretamente. Um snapshot idêntico também retornava antes de reconciliar contratos novos.

`EsportsMarketFactory` seleciona estratégias próprias de CS2, Valorant e League of Legends, reutilizando os mesmos Market, MarketOption, Prediction, wallet, settlement e projeções de ranking existentes. Não altera o provider, sua autenticação ou seu scheduler. A sincronização reconcilia contratos tanto no primeiro snapshot LIVE quanto em snapshots idênticos de eventos existentes. Identidade e resultado esportivo permanecem do provider.

| Modalidade | Pré-jogo BO3/BO5 | LIVE com placar da série | LIVE sem placar |
|---|---|---|---|
| CS2 | Vencedor, placar exato, total de mapas, handicap de mapas e cada equipe vence pelo menos um mapa | Vencedor, placar exato, total, cada equipe vence pelo menos um mapa; somente resultados ainda incertos | Vencedor da série |
| Valorant | Vencedor, placar exato, total, cada equipe vence pelo menos um mapa | Vencedor, placar exato, total, cada equipe vence pelo menos um mapa; somente resultados ainda incertos | Vencedor da série |
| LoL | Vencedor, placar exato e total de jogos | Vencedor, placar exato e total de jogos; somente resultados ainda incertos | Vencedor da série |

BO1 publica somente vencedor. Formato desconhecido ou diferente de BO1/3/5 não publica contratos nesta implementação. Partidas já encerradas não recebem mercados retroativos. Contratos anteriormente publicados são preservados, inclusive seus snapshots e palpites.

Primeiro/atual/próximo mapa, rounds, pistol, kills, torres, dragões, Barão e first blood não são publicados: o modelo normalizado atualmente disponível não fornece resultados suficientes para liquidar esses contratos. Isso não é uma afirmação sobre todos os planos PandaScore; é o limite dos dados efetivamente integrados.

## Ciclo e segurança

- PRE_MATCH_ONLY fecha no início e nunca reabre como LIVE. Contratos LIVE_ONLY têm identificadores próprios com sufixo `_LIVE`.
- Um evento descoberto LIVE recebe apenas contratos LIVE liquidáveis. Ausência de placar não bloqueia o vencedor final da série quando identidade, formato e suporte a resultados estão confirmados.
- Com placar confirmado, `SeriesOutcomeModel` enumera somente finais possíveis. Opções de placar impossíveis ficam inativas; mercados com resultado já determinado fecham. Não há palpites sobre respostas já conhecidas.
- Perda do placar suspende contratos LIVE derivados; o vencedor continua disponível. Snapshot esportivo sem leitura há mais de cinco minutos suspende todos os contratos LIVE até a próxima leitura recente.
- FINISHED bloqueia comandos e liquida pelo resultado oficial. Cancelamento devolve pontos; resultado incompleto/não padrão ou correção posterior seguem o fluxo de revisão existente.
- Repetições do resultado preservam o marcador de processamento e a chave única de ledger. Multiplicador, origem e versão são congelados no palpite.
- Demo usa seu próprio cenário e modelo existente. Conta Demo não ganha permissão para modificar ou registrar palpites em eventos externos; QA utiliza usuários comuns exclusivamente em banco isolado.
- GET do evento publica somente contratos que saíram de DRAFT. Contratos encerrados/liquidados continuam visíveis como histórico, sem aceitar palpites.

## Multiplicadores virtuais

`VirtualMultiplierService`, versão `arena-series-v1`, centraliza a curva dos contratos PandaScore: faixa **1,10× a 8,00×**. Uma referência por mapa/jogo restante de 50/50 produz probabilidades internas dos finais possíveis. É um prior equilibrado, não uma análise de força de equipes e não uma probabilidade oficial. Com placar real, a liderança da série altera a referência; sem placar, o vencedor usa 2,00× para ambos os participantes. A transformação limitada usa surpresa em bits e compressão racional, com 50% ancorado em 2,00×. Não usa odds de bookmaker, aleatoriedade ou fórmulas no frontend. NaN, Infinity e valores inválidos são rejeitados. Opções impossíveis não são aceitas, mesmo que a representação de coeficiente permaneça limitada.

O modelo demonstrativo e os adapters de outras modalidades são preservados. Coeficientes existentes congelados nos palpites não são recalculados.

## Verificação real isolada

`qa/RealPredictionFlowProbe.java` é um runner manual, fora da CI e do artefato de produção. Recebe dois arquivos de snapshots autênticos PandaScore (`running` e `finished`) para o mesmo ID. Cria um contexto H2 isolado, sem provider externo habilitado, registra dois palpites de usuários de teste comuns, importa o resultado final, verifica WON/LOST, crédito único e ranking/estatísticas. Repetição dez vezes deve manter os saldos.

O runner não consulta a PandaScore nem altera usuários/banco de produção. A captura autenticada é uma verificação manual separada na EC2, mantendo a credencial apenas no servidor. Os snapshots e relatórios de execução ficam fora do Git. Um replay histórico é identificado como replay; uma prova temporal registra os horários de captura, registro do palpite e término oficial, sem inventar resultado ou alterar status para preencher a interface.

Com o argumento opcional `--persist`, o runner guarda seu banco H2 e um checkpoint ao lado do relatório, sempre fora do Git. Se o resultado não chegar dentro da janela de 30 minutos, executar novamente com os mesmos arquivos retoma os mesmos palpites e horários, sem recriá-los. A persistência é exclusivamente de QA; o driver permanece H2 local, com providers/scheduler externos desabilitados. Nunca utilizar paths de banco ou usuários de produção para essa verificação.

Depois do deploy, os snapshots normais do scheduler reconciliam automaticamente eventos SCHEDULED/LIVE existentes, sem reset, migration ou alterações nos volumes PostgreSQL.
