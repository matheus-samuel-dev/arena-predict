# Arena Predict — consolidação de 04/10/2026

**Classificação: A — PRODUTO DE PORTFÓLIO FORTE E COMPLETO**, considerando o escopo
implementado e as limitações explícitas abaixo. O produto volta a ser explorável
inteiramente por contas Demo. A jornada rápida continua disponível e reutiliza o
domínio existente. Não foram introduzidos pagamentos, odds financeiras ou novos
módulos artificiais. Esta classificação não equivale a certificação de produção.

## 1. Estado encontrado antes da rodada

A árvore estava limpa no commit `3a12ff3`. A auditoria comparou `03284dc`,
`404a40e`, `06c6360` e o estado atual antes de editar código. A integração esportiva,
o motor de mercados, o sandbox controlado, os services e as páginas estavam
preservados. A regressão estava na exposição do produto: menu Demo reduzido,
redirecionamento de qualquer Admin Demo para `/demo` e bloqueio de todas as APIs
administrativas para essa conta. Avisos repetidos reforçavam a percepção de demo.

Evidências e decisões por classe A–F: [auditoria anterior à implementação](consolidation-audit-2026-10-04.md).
Nenhum checkout antigo, reset de Git ou exclusão de dados foi realizado.

## 2. Páginas existentes

| Participante | Rota | Valor preservado |
|---|---|---|
| Visão geral | `/app` | Dashboard, saldo, atividade, eventos e resumo de desempenho |
| Eventos / detalhe | `/events`, `/events/:id` | Busca, estados, modalidade, origem, categorias de mercados |
| Ao vivo | `/live` | Eventos em andamento e disponibilidade atual |
| Meus palpites | `/predictions` | Escolha, pontos, snapshot, potencial, status, recompensa |
| Bolões | `/pools` | Grupos sociais, convite, membros e ranking interno |
| Ligas | `/leagues` | Temporadas organizadas e regras de elegibilidade |
| Rankings | `/rankings` | Período, modalidade, escopo, origem e busca de participante |
| Estatísticas | `/statistics` | Precisão, volume, uso de pontos e desempenho por mercado |
| Desafios / Conquistas | `/challenges`, `/achievements` | Progressão persistida e critérios de domínio |
| Comunidade | `/community` | Feed, posts, curtidas, comentários e denúncias |
| Minha conta | `/account` | Perfil, avatares Arena e preferências |
| Pontos / Notificações / Ajuda | `/points`, `/notifications`, `/help` | Ledger virtual, avisos próprios e orientação |
| Jornada Demo | `/demo` | Palpite, início, resultado e nova rodada controlada |

Admin: painel `/admin` e 16 páginas de recursos, listadas no item 12. Login,
registro e recuperação de acesso também foram preservados. `/profile` continua
como alias compatível de Minha conta.

## 3. Páginas removidas anteriormente

**Nenhuma no conjunto de commits e fontes comparado.** Não houve exclusão dos
módulos citados no pedido nem perda das rotas correspondentes. O efeito observado
era de páginas escondidas/bloqueadas para os perfis usados pelo recrutador.
Essa conclusão se limita ao histórico inspecionado; não inventa versões anteriores.

## 4. Páginas e acessos restaurados

Restaurada a navegação completa do participante Demo em três grupos recolhíveis:
Principal, Competições, Comunidade e conta. Restaurado o backoffice do Admin Demo
em cinco grupos: Operação, Catálogo, Engajamento, Gestão e Governança. O drawer
mobile apresenta os mesmos links. O login Demo abre `/app` ou `/admin`, conforme
o perfil; a jornada `/demo` passa a ser uma ação contextual do produto.

O dashboard continua sendo resumo. Não substitui Eventos, Rankings, Estatísticas,
Bolões, Comunidade ou outras páginas específicas. Nenhuma tela foi recriada para
aumentar a quantidade de links.

## 5. Não restaurado deliberadamente

- Fluxos antigos de Copa/pagamentos, dinheiro real, PIX, depósito, saque e cashout:
  obsoletos para este produto (D), fora do escopo e bloqueados na superfície atual.
- Navegação paralela Demo e banners globais repetidos: redundantes (C).
- Uma segunda página de perfil: alias consolidado em Minha conta (C/F).
- Engines antigos substituídos pelo catálogo e liquidação atuais: preservada a
  implementação nova (F), sem reverter regras ou calibração.
- Switches fictícios, gráficos artificiais e CRUD sem contrato: não criados.

Nenhum módulo útil do participante ou backoffice foi deliberadamente removido.

## 6. Funcionalidades consolidadas

Menus usam papel autenticado, inclusive em Demo. Cards e detalhe do evento usam
a mesma política de disponibilidade. Participante Demo pode enviar palpite pelo
detalhe normal de eventos demonstrativos internos, incluindo a rodada controlada.
Admin Demo navega em consulta; ações da rodada ficam na jornada autorizada.

Origem, ID externo, sincronização e processamento aparecem nos recursos relevantes.
Palpites distinguem perdas de reembolsos e resultado pendente de palpite aberto.
Rankings recebem filtro explícito Real/Demo. Precisão por mercado considera somente
palpites encerrados, acompanhada do volume total. Recursos administrativos
desconhecidos exibem estado de rota inválida em vez de uma tabela de eventos indevida.

## 7. Arquitetura REAL

```text
PandaScore → SportsDataProvider → SportsSyncService → PostgreSQL
                                                   ↓
                                      catálogo / mercados / eventos
                                                   ↓
                              resultado → liquidação → ledger / progressão
                                                   ↓
                                     API autenticada → React → rankings
```

Identidade composta `externalProvider + externalId`, upsert e reconciliação
continuam existentes. O browser consulta a API Arena, não o provedor diretamente.
Atualizações operam sobre dados persistidos. Estado, horários e resultados
ausentes não são preenchidos com informação esportiva inventada.

## 8. Arquitetura DEMO

Autenticação rápida produz JWT de uma conta compartilhada com papel real.
`DemoAccessPolicy` identifica essas contas no servidor. A jornada administra uma
competição própria e a geração corrente de seu evento. Seus comandos passam pelos
mesmos services de mercados, palpites, liquidação, carteira, ranking e progressão.
Não foi criado motor paralelo de pontos nem cálculo de resultado no frontend.

Além da rodada, as entidades demonstrativas internas existentes continuam
identificadas. Participante Demo usa perfil/preferências, notificações próprias,
posts/curtidas/comentários/denúncias Demo e bolões do sandbox. A senha da conta
compartilhada permanece protegida.

## 9. Separação Real/Demo e reset

Evento externo é protegido mesmo que alguém forneça flags incoerentes. Demo não
pode enviar palpite em evento real/externo, ingressar por ID ou convite em bolão
real, vincular palpite a um bolão real ou interagir com post real por ID direto.
Criar bolão Demo para campeonato externo também é negado pelo service.

O feed Demo retorna somente posts Demo; posts de apresentação existentes foram
marcados na migration. Ações sociais persistem no banco. Comentários Demo não
enviam notificações para autores reais dos fixtures de apresentação.

Reset aceita geração observada, é idempotente, arquiva a rodada controlada,
reembolsa palpites abertos desse escopo e prepara nova rodada. Preserva o ledger,
histórico geral da conta, eventos externos, times e campeonatos sincronizados e
usuários reais. Não é uma limpeza global de todos os posts ou bolões Demo criados.

## 10. Provider esportivo

Mantido o adapter PandaScore de CS2, scheduler, lease no banco, timestamps,
timeouts, retry limitado, orçamento de requests, backoff de falha/quota e fallback
para último snapshot persistido. Não houve substituição por fixtures no código de
produção. O painel exibe status seguro via `/api/admin/sports-sync/status` também
para Admin Demo, sem token ou configurações sensíveis.

**Não foi fornecida credencial ativa nesta execução local.** A integração foi
validada por mocks do provedor em testes com PostgreSQL, incluindo upsert,
deduplicação, status, resultado único e correções. Não foi afirmado recebimento
de partidas de uma conta externa ao vivo. Configuração e cobertura:
[integração esportiva](sports-integration.md).

## 11. Módulos do participante

Todos os módulos do item 2 possuem componentes e contratos existentes; as rotas
principais foram exercitadas com o App real nos testes e no navegador conectado
ao PostgreSQL. As consultas carregam, os links levam às páginas específicas e
os filtros de eventos/ranking permanecem operacionais.

As permissões de Demo protegem dados reais. O conteúdo informa a origem na conta
ou entidade, sem transformar cada página em uma apresentação do modo Demo.
O detalhe reúne mercados por categoria e informações disponíveis do evento.

## 12. Módulos do administrador

| Grupo | Páginas e contratos |
|---|---|
| Operação | Painel, Eventos, Mercados, Resultados |
| Catálogo | Modalidades, Campeonatos, Equipes e participantes |
| Engajamento | Bolões e ligas, Pontuação, Desafios, Conquistas |
| Gestão | Usuários, Notificações, Moderação |
| Governança | Relatórios, Auditoria, Configurações |

Painel usa contagens persistidas e fila operacional, status de integração e
ações auditadas recentes. Catálogos mostram origem e identidade externa quando
presentes. Mercados mostram opções/multiplicadores, janelas e motivos de bloqueio.
Resultados mostram placar, processamento ou revisão pendente.

Admin real preserva comandos existentes de catálogo interno, mercados, resultados,
gamificação e moderação. Usuários, relatórios, pontuação e configurações mantêm
consultas úteis; não se promete CRUD de usuários ou edição de toda configuração.
Admin Demo recebe uma lista explícita de GET/HEAD; POST/PUT/PATCH/DELETE genéricos
continuam negados pelo backend e não aparecem como botões disponíveis. A operação
Demo autorizada é iniciar, simular resultado e resetar a rodada controlada.

## 13. Regras de mercados e multiplicadores

Preservados `MarketDefinitionCatalog`, regras por modalidade, disponibilidade,
validação de resultados e snapshots das definições. Catálogo interno suporta
famílias coerentes de futebol, basquete, tênis, vôlei, CS2, Valorant, LoL, Dota2,
futebol americano e automobilismo, conforme os dados exigidos por cada regra.

Para eventos externos de CS2 são publicados somente mercados liquidáveis com
placar da série e BO conhecido. Não foram liberados rounds, mapas ou pistol
sem dados. Eventos internos podem registrar métricas compatíveis pelo domínio.
Suspensão, encerramento, linha empatada e cancelamento conservam suas regras.

Mantida a calibração determinística específica por estado/modalidade de
`DemoProbabilityEngine`: multiplicadores virtuais entre 1,05 e 15, arredondamento
definido e sem odds externas. O valor confirmado é salvo no palpite, não
recalculado retroativamente. Detalhes: [calibração](multiplier-calibration.md) e
[regras de mercado](market-business-validation.md).

## 14. Pontuação

Ao registrar: validação de mercado/evento/opção, saldo e multiplicador esperado;
débito dos pontos com chave idempotente; snapshot de multiplicador e potencial.
`potencial = floor(pontos × multiplicador confirmado)`.

Ao vencer: crédito exatamente do potencial salvo. Ao perder: sem recompensa.
Ao cancelar/anular conforme a regra: devolução da utilização, sem segundo crédito.
Transações persistidas, locks e chaves únicas protegem concorrência e repetição.
Resultado repetido não duplica saldo, ledger ou recompensa. Notificações e
progressão continuam ligadas ao processamento do domínio.

## 15. Ranking

Derivado de palpites persistidos WON/LOST e recompensas, com acertos, precisão e
sequência. Arquivados não entram no ranking geral corrente. Ordem: pontos,
acertos e nome. Semanal/mensal usam janelas móveis de 7/30 dias; Geral usa histórico.
Modalidade, amigos, busca e ranking interno foram preservados. O novo parâmetro
`source=ALL|REAL|DEMO` filtra a origem dos eventos; valores inválidos retornam 400.
ALL é explicitamente apresentado como “Real e demonstração”.

## 16. Bolões

`PoolType.POOL`: grupo social criado pelo usuário, regras, propriedade, membros,
limite, convite e classificação dos palpites vinculados. Convite privado só é
retornado ao proprietário/membro autorizado. Demo pode criar grupo próprio e
operar bolões demonstrativos, sem alterar a participação de grupos reais.

## 17. Ligas

`PoolType.LEAGUE`: competição pública organizada pelo administrador, período
obrigatório e possibilidade de recorrência. Classificação considera palpites
elegíveis para modalidade/campeonato e janela da liga, sem exigir vínculo manual
de cada palpite. Usuário participante não cria liga. O componente compartilhado
filtra tipos e apresenta ações e regras distintas; não são dois nomes para o
mesmo grupo. Recorrência não implica promessa de automação nova de temporadas.

## 18. Gamificação

Preservado `ProgressionService`: primeiro palpite, primeira vitória, contagens,
vitórias, variedade de modalidades e participação em bolões conforme a definição.
Desafios usam janela e alvo; conquistas usam critérios persistidos. Recompensas
possuem chaves próprias para não repetir concessões. GET de consulta não concede
recompensas; a progressão é avaliada pelos comandos de atividade.

## 19. Comunidade

Mantidos feed, avatares Arena, autoria, publicação, likes persistidos, comentários,
denúncias e moderação. O campo server-side `demo` separa conteúdo demonstrativo.
Like repetido conserva uma única relação. Admin Demo lê conteúdo e fila de
moderação, sem agir sobre publicações reais. Não se ampliou a comunidade para
uma rede social ou infraestrutura nova sem necessidade.

## 20. Segurança

JWT, roles, verificações HTTP e de service continuam ativos. Participante recebe
403 nos endpoints administrativos; Admin Demo não recebe comandos genéricos.
Projeção de usuários mascara e-mail para Demo e não permite buscar por e-mail
privado. Projeção administrativa de notificações protege destinatário, mensagem
privada e destino; metadados úteis permanecem consultáveis.

Senha compartilhada não pode ser alterada. Preferências e notificações são
escopadas ao usuário autenticado. Segredo do provider permanece somente no
servidor. Não foram afrouxados CORS, proteção de origem externa ou regras de
resultado para acomodar a interface. Dados sensíveis não entram nas evidências QA.

## 21. Testes

Resultados finais estão em [resumo de validação](../qa/consolidation-validation.json).
O frontend passou em **221 testes / 23 arquivos**, incluindo rotas reais, menus,
AdminRoute, login, restrições Demo e palpite pelo detalhe normal.

Em PostgreSQL 16.15, **65 testes** passaram em cinco suítes: Demo (10), liquidação
externa (10), sincronização (9), revisão (4) e contratos/isolamento da consolidação
(32). [Contagens por suíte](../qa/consolidation-postgresql-suites.json).
Também passou a suíte completa do backend pelo `mvn verify`: **388 testes em
37 suítes, sem falhas, erros ou skips**.

O fluxo pelo Chrome passou: login Admin → reset local → Participante → dashboard
→ Eventos → detalhe → confirmação de palpite → Meus palpites → Admin inicia/simula
→ Participante recebe recompensa igual ao potencial salvo e ranking atualizado
→ Rankings/Estatísticas/Desafios/Conquistas/Bolões/Ligas/Comunidade/Conta.
[Evidência do fluxo](../qa/consolidation-product-flow.json).

O E2E HTTP de 31 requests valida resultado inválido sem mutação, fechamento,
persistência, liquidação única, conflito ao alterar resultado, reset idempotente
e reembolso. [Evidência HTTP](../qa/demo-api-e2e.json). O Compose local não tinha
eventos externos nessa execução (`officialEventsCompared=0`); a proteção de
entidades externas com fixtures existentes é comprovada pelas suítes PostgreSQL.

O navegador valida produto, detalhe, jornada e login em **1920, 1440, 1366, 1024,
768, 430, 390 e 360 px**, além dos menus agrupados, drawer e busca de eventos.
Foram **296 combinações de página/largura, 1.300 respostas de API, zero overflow,
zero erros de console, página ou API**. Medidas DOM e erros estão em
[evidência de navegação](../qa/consolidation-browser.json).
As capturas locais foram inspecionadas, preservando a identidade visual.
Após ajustar a quebra de texto do aviso administrativo em mobile, os módulos
afetados foram revalidados nas oito larguras:
[conferência adicional do admin](../qa/consolidation-admin-layout.json).

## 22. Builds e execução

TypeScript (`npm run lint`), Vite (`npm run build`), Maven (`mvn verify`), build
Docker do backend JDK 21 e frontend foram executados. Compose de QA validado com
`config --quiet`; contêineres API/web reconstruídos com PostgreSQL preservado.
Aplicação local disponível em `http://localhost:5176`. O projeto Compose usado
foi `arena-sports-qa`; não foi realizado deploy em EC2 nem publicação externa.

## 23. Migrations

Nova **V14__community_demo_isolation.sql**: coluna booleana `demo` em posts,
marcação dos fixtures existentes de apresentação e índice por origem/status/data.
Sem remoção de tabela, limpeza de saldo ou alteração de IDs esportivos.
Flyway V1–V14 aplicado no banco isolado e validado nas reexecuções PostgreSQL;
V14 também aplicada ao volume preservado do Compose de QA.

## 24. Arquivos alterados

Inventário completo: [manifesto da consolidação](../qa/consolidation-files.txt).
Principais grupos:

- Shell, autenticação/entrada, disponibilidade de palpites e páginas existentes.
- DTOs/API de origem e ranking; consultas administrativas seguras.
- Políticas de Demo, fronteiras de bolões/palpites e comunidade.
- Migration V14 e regressões de segurança, contratos, rotas e componentes.
- Scripts/evidências QA e documentação atualizada. Relatórios históricos mantidos.

Não foi alterado o adapter PandaScore, a sincronização ou o motor de multiplicadores.
Nenhum commit, push ou publicação foi executado nesta rodada.

## 25. Limitações reais restantes

1. Provider real integrado somente para CS2/PandaScore. Sem token/plano ativo local,
   não houve teste contra uma conta externa; cobertura contratual usa mocks.
2. Placar ao vivo depende de flag/cobertura REST; WebSocket de rounds/mapas não
   foi implementado. Informação ausente permanece ausente na UI.
3. Correção externa posterior à liquidação gera revisão e auditoria; não existe
   reversão automática completa de carteira, XP e conquistas. Não se sobrescreve
   resultado aplicado nem se anuncia correção concluída.
4. Contas e rodada Demo são compartilhadas. Outro visitante pode alterar perfil
   próprio/atividade/rodada; a jornada informa essa característica. Não há sandbox
   separado por visitante nem limpeza global de comunidade no reset.
5. Admin Demo consulta todo o backoffice, mas opera apenas a rodada autorizada.
   CRUD genérico de entidades Demo pelo visitante não foi aberto.
6. Alguns recursos administrativos são consultas reais, não CRUD completo.
   Não existem switches sem efeito nem ações de gerenciamento inventadas.
7. Estatísticas são agregações de palpites existentes; não foi criado warehouse,
   gráfico histórico artificial ou serviço de analytics externo.
8. Não houve implantação/ensaio de carga de produção nesta rodada. Docker local,
   builds, migrations, regressões e navegador não substituem validação do ambiente
   EC2 com secrets, TLS, backups e provider configurados.

O conjunto não está simplificado em torno da Demo: participante, competições,
engajamento, catálogo, operação e governança seguem acessíveis com propósito e
persistência. A demonstração expõe essa profundidade mantendo as fronteiras reais.
