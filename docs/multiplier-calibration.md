# Calibração dos multiplicadores demonstrativos — 22/09/2026

## Escopo e causa

Alteração de produção somente em `DemoProbabilityEngine`. Sem alterações de
interface, mercados, modalidades disponíveis, saldos, ranking ou liquidação.

A conversão v1 era `clamp(1 / max(0.000001, p), 1.01, 100)`. Qualquer
probabilidade de 1% ou menos chegava ao mesmo teto de 100x; favoritos com
probabilidade acima de aproximadamente 99% eram arredondados/cortados em 1,01x.
8%, 5% e 2% produziam respectivamente 12,50x, 20x e 50x, não 100x.

No basquete havia ainda uma aproximação numérica: a distribuição dos pontos
restantes era truncada em cinco desvios padrão. Com +10 e 30 segundos restantes,
esse suporte não incluía sequer um empate, gerando probabilidade zero para a
virada. O suporte passou a oito desvios padrão, preservando as caudas raras da
mesma distribuição. Não foram adicionados dados nem novos fatores esportivos.

No CS2, BO3 em 1x0 sem rounds do mapa atual corresponde a 75%/25% no modelo
existente. A extremidade anterior vinha dos rounds próximos de terminar a série,
não apenas do placar dos mapas. Essa distinção foi preservada e testada.

## Curva v2

Para `0 < p < 1`:

```text
L = 1.05; U = 15
s = -ln(p) / ln(2)
w = sqrt(s) * (1 + 3*s) / 4
k = (U - 2) / (2 - L)
M(p) = L + (U - L) * w / (k + w)
```

`s` mede a surpresa do resultado em bits. A raiz mantém resolução entre
favoritos fortes; o termo superlinear separa resultados raros. A fração final
comprime continuamente a recompensa em direção ao teto, sem recortar todas as
probabilidades pequenas no mesmo valor. A constante `k` ancora 50% em 2x.
Não há margem financeira, normalização de preços ou aleatoriedade.

Os extremos são `M(0)=15` e `M(1)=1.05`. Valores finitos ligeiramente fora de
[0,1], por arredondamento numérico, usam o extremo correspondente; NaN e
infinito são rejeitados. Arredondamento final HALF_UP em duas casas decimais.
Esse arredondamento pode igualar probabilidades praticamente certas: não se
promete uma diferença visual para toda diferença infinitesimal de probabilidade.

| Probabilidade | v1 | v2 | Retorno bruto de 100 pontos em v2 |
|---|---:|---:|---:|
| 99,9% | 1,01x | 1,06x | 106 |
| 99% | 1,01x | 1,08x | 108 |
| 95% | 1,05x | 1,13x | 113 |
| 50% | 2,00x | 2,00x | 200 |
| 8% | 12,50x | 5,15x | 515 |
| 5% | 20,00x | 5,88x | 588 |
| 2% | 50,00x | 7,16x | 716 |
| 0,5% | 100,00x | 8,68x | 868 |
| 0,001% | 100,00x | 12,08x | 1.208 |

## Economia de pontos

Leitura da base local antes da alteração: 25 carteiras, saldo médio 5.908,52,
mediana 5.539, faixa 5.000–9.332. Dos 309 palpites existentes, pontos por
palpite: média 105,04, mediana 110, faixa 10–174. A amostra inclui dados demo
e validações anteriores; não representa comportamento de usuários de produção.

Saldo inicial configurado: 5.000. Conquistas concedem 100–500 pontos e desafios
180–300. A progressão usa contagens de palpites, acertos e modalidades; os
rankings usam saldo ou recompensas conforme o recorte. Nada disso foi alterado.

Comparação dos tetos para 100 pontos: 15x retorna 1.500; 20x, 2.000; 25x,
2.500; o antigo 100x retornava 10.000. Foi escolhido o menor teto da faixa
proposta: reduz o máximo em 85% e limita esse retorno a 30% do saldo inicial.
O lucro líquido nesse exemplo cai de 9.900 para 1.400. Com 110 pontos, o máximo
bruto é 1.650, mas probabilidades usuais e até raras ficam bastante abaixo disso.

O formulário sugere 5% do saldo, sujeito ao mínimo e máximo existentes. Com
saldo inicial de 5.000, são 250 pontos: teto bruto de 3.750 (ganho líquido
3.500), contra 25.000 no teto antigo. Portanto um acerto extremo ainda pode
ser relevante; a calibração reduz sua escala, não elimina o efeito de entradas
maiores. A sugestão do formulário foi somente inspecionada, sem alterações.

Limitação deliberadamente preservada: o sistema permite até 20.000 pontos por
palpite, sujeito ao saldo. Portanto ainda é possível um retorno bruto de 300.000
com uma entrada desse tamanho. Apenas a curva não garante que nenhum palpite
isolado afete fortemente o ranking. Alterar o limite de pontos, o ranking ou
recompensas estaria fora desta rodada. Snapshots antigos também mantêm os
retornos originalmente confirmados, mesmo acima do novo teto.

## Exemplos do mesmo estado esportivo

v1 capturada na API local antes da troca; v2 reproduzida no motor e validada
na aplicação. Ordem: mandante / empate / visitante no futebol; mandante /
visitante nas outras modalidades.

| Estado | v1 | v2 |
|---|---|---|
| Palmeiras 3x1 Flamengo, 83:24, acréscimos 5 | 1,01 / 98,67 / 100,00 | 1,08 / 7,96 / 10,33 |
| Celtics 110x100 Mavericks, Q4, 00:30 | 1,01 / 100,00 | 1,05 / 13,91 |
| FURIA 1x0 NAVI, BO3, mapa 2 em 9x7 | 1,15 / 7,88 | 1,23 / 4,38 |
| Alcaraz 1x0 Sinner, BO3, games 5x3 | 1,07 / 16,00 | 1,15 / 5,54 |

Progressão reproduzível em `DemoProbabilityEngineTest`:

| Cenário | Multiplicadores v2 |
|---|---|
| Futebol 3x1, 20 minutos | 1,24 / 4,82 / 6,12 |
| Futebol 3x1, 60 minutos | 1,15 / 5,87 / 8,03 |
| Futebol 3x1, 90+3 | 1,06 / 10,57 / 12,35 |
| Basquete +10, Q1, 08:00 | 1,47 / 2,96 |
| Basquete +10, Q3, 08:00 | 1,33 / 3,59 |
| Basquete +10, Q4, 05:00 | 1,13 / 6,02 |
| CS2 1x0, BO3, sem rounds em andamento | 1,41 / 3,19 |
| CS2/Valorant 1x0, BO3, rounds 12x3 | 1,06 / 10,44 |

Modelos de futebol, tênis, séries de CS2/Valorant/LoL/vôlei permanecem os
existentes. Automobilismo sem dados suficientes continua estático (3,20x nos
pilotos do evento demo). Bases estáticas respeitam os limites de exibição do
motor, sem escrever novos valores nas opções do banco.

## Snapshot, execução e desempenho

A alteração não escreve no banco. Cálculo continua puro, no backend e no mesmo
caminho de cotação já existente. Não foram adicionados requests, polling ou
reprocessamento de palpites. O suporte numérico do basquete é maior, mas
permanece uma distribuição finita em memória.

O palpite grava multiplicador e potencial confirmado. Liquidação usa esse
snapshot. A integração cobre alteração posterior do evento, recarga do banco,
rejeição de cotação desatualizada e pagamento idempotente em seis modalidades.
Novo teste adicional preserva um snapshot legado de 100x e paga 4.000 pontos
para 40 pontos confirmados, exatamente uma vez.

Novas propriedades: monotonicidade em 10.001 probabilidades, distinção das
caudas e favoritos, determinismo, precisão decimal, limites e entradas não
finitas; progressão em quatro momentos de futebol/basquete; empate versus
virada; série BO3/BO5 versus match point; sets/games; séries de LoL e vôlei;
treze exemplos esportivos reproduzíveis. Os testes existentes de dados ausentes
e fallback estático permanecem.

## Evidências de validação

- `mvn -q test package`: **222 testes, zero falhas, zero erros, zero ignorados**;
  JAR gerado em 22/09/2026 às 13:15. Inclui 48 testes do motor e 38 de integração
  multimercado. Na primeira execução, o novo fixture de snapshot legado estava
  incorretamente em pré-jogo para um mercado LIVE_ONLY; o fixture foi corrigido
  e a suíte completa foi executada novamente, com sucesso.
- `npm run test:run`: **142 testes em 18 arquivos**, todos passaram.
- `npm run typecheck` e `npm run build`: passaram, sem alterações no frontend.
- Docker: imagem backend construída com Java 21; testes Maven locais em Java 25.
- `git diff --check`: passou.
- Resumo por suíte: `qa/multiplier-backend-summary.json`.
- Cotações anteriores: `qa/multiplier-before.json`.

- Imagem aplicada ao ambiente local `arenapredict-finalization`, com API, web e
  PostgreSQL saudáveis. Docker precisou ser iniciado novamente após a pausa da
  sessão; nenhum volume foi removido e nenhuma configuração foi alterada.
- Navegador real, sessão participante: futebol `/events/1`, basquete `/events/37`,
  CS2 `/events/2`, tênis `/events/38`, Valorant `/events/4` e corrida `/events/39`.
  Os quatro exemplos v2 da tabela acima apareceram exatamente na interface.
  Valorant estava em pré-jogo, com 1,88x/1,84x estáticos; não havia evento live
  dessa modalidade. Seu cenário live foi validado no domínio e integração.
  Automobilismo manteve 3,20x e a explicação de dados insuficientes.
- Formulário real: selecionar Flamengo 10,33x e informar 100 pontos exibiu
  **1.033 pontos** de potencial. O formulário foi cancelado; não foi criado um
  novo palpite na base de demonstração nesta rodada.
- Console do smoke: **nenhum erro ou aviso**. Logs HTTP confirmam respostas 200
  para os seis detalhes. Houve uma resposta 499 (cliente encerrou a requisição)
  em uma atualização de futebol durante a troca de página; as leituras de
  futebol antes disso retornaram 200. O polling existente de 30 segundos não foi
  alterado. Evidência: `qa/multiplier-browser-network.txt`.
- Comparação antes/depois dos 309 registros persistidos: hash idêntico para
  ID, multiplicador, potencial e recompensa. Evidências:
  `qa/multiplier-snapshot-before.txt` e `qa/multiplier-snapshot-after.txt`.
- Cotações depois da implantação: `qa/multiplier-after.json`.

## Arquivos desta rodada

- `backend/src/main/java/com/bolao/copa/arena/service/DemoProbabilityEngine.java`
- `backend/src/test/java/com/bolao/copa/arena/DemoProbabilityEngineTest.java`
- `backend/src/test/java/com/bolao/copa/arena/MultimarketIntegrationTest.java`
- `docs/multiplier-calibration.md`
- `qa/multiplier-before.json`, `qa/multiplier-after.json`
- `qa/multiplier-backend-summary.json`, `qa/multiplier-browser-network.txt`
- `qa/multiplier-snapshot-before.txt`, `qa/multiplier-snapshot-after.txt`

Somente o primeiro arquivo altera código de produção. Os demais são testes,
documentação ou evidências desta calibração. Nenhuma migração, seed, interface,
mercado, regra de saldo/ranking ou rotina de liquidação foi modificada.
