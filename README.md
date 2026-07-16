# ArenaPredict

Plataforma full-stack de previsões esportivas, bolões e ligas com pontos exclusivamente virtuais. A aplicação cobre esportes tradicionais e eSports por meio de um domínio configurável, sem apostas, depósitos, saques ou conversão financeira.

> **Aviso:** todos os pontos, multiplicadores e recompensas são fictícios e destinados somente a entretenimento e demonstração de portfólio. Eles não possuem valor financeiro.

`ArenaPredict` é uma marca provisória. O nome visível fica centralizado em variáveis de ambiente e em `frontend/src/app/branding.ts`, permitindo uma troca futura sem refatorar as regras de negócio.

## Início rápido

Pré-requisito: Docker Desktop com Docker Compose v2.

```bash
docker compose up -d --build
```

Depois da inicialização:

- aplicação: <http://localhost:5173>
- API: <http://localhost:8080>
- Swagger: <http://localhost:8080/swagger-ui.html>
- healthcheck da API: <http://localhost:8080/actuator/health>
- healthcheck da aplicação web: <http://localhost:5173/health>

Para acompanhar a stack:

```bash
docker compose ps
docker compose logs -f backend frontend postgres
```

Para encerrar sem apagar os dados:

```bash
docker compose down
```

O volume PostgreSQL é persistente. Use `docker compose down -v` somente quando quiser apagar deliberadamente todos os dados locais.

## Credenciais de demonstração

| Perfil | E-mail | Senha |
|---|---|---|
| Administrador | `admin@arenapredict.com` | `Admin@123` |
| Participante | `jogador@arenapredict.com` | `Jogador@123` |

Aliases antigos são mantidos apenas para compatibilidade de dados:

- `admin@bolao.com` / `123456`
- `user@bolao.com` / `123456`

O seed é idempotente e só é ativado quando `APP_DEMO_ENABLED=true`.

## Arquitetura

```text
Browser
  └─ Nginx / React + TypeScript (porta 5173)
       └─ /api/* → Spring Boot 3 / Java 21 (porta 8080)
                         └─ JPA + Flyway → PostgreSQL 16
```

### Frontend

- React 18, TypeScript, Vite e React Router;
- `AuthProvider` com restauração de sessão por `/api/auth/me`;
- cliente HTTP com timeout, cancelamento, mensagens seguras e distinção de 401/403;
- guards de rota e navegação por perfil;
- design system responsivo, tema escuro, acessibilidade por teclado, skeletons, toasts e estados vazios;
- marca e textos institucionais centralizados em `frontend/src/app/branding.ts`.

### Backend

- Spring Boot, Spring Security, JWT, BCrypt e RBAC;
- DTOs e validação de entrada; entidades JPA não são expostas;
- carteira de pontos com lock pessimista e extrato imutável;
- transações para débito, recompensa e reembolso;
- idempotência em palpites e processamento de resultados;
- providers `SportsDataProvider` e `EsportsDataProvider` com implementação demo explícita;
- Flyway com schema validado por Hibernate;
- Actuator e OpenAPI.

O namespace Java `com.bolao.copa` foi preservado como fronteira técnica de migração. O domínio novo fica isolado em `com.bolao.copa.arena` e não depende de conceitos de Copa ou pagamento.

## Domínio principal

- `Sport` e `Championship` para modalidades e competições configuráveis;
- `Competitor` para equipes ou participantes;
- `ArenaEvent` para eventos tradicionais e eSports, inclusive BO1/BO3/BO5;
- `PredictionMarket` e `MarketOption` para mercados e multiplicadores simulados;
- `ArenaPrediction` para palpites e seu ciclo de vida;
- `PointWallet` e `PointLedgerEntry` para saldo e extrato virtual;
- `ArenaPool` e `ArenaPoolMember` para bolões e ligas;
- `ArenaNotification` para notificações;
- perfil, preferências, conquistas, desafios e comunidade em módulos separados do núcleo transacional.

As tabelas legadas permanecem na migration inicial para preservar bancos existentes, mas seus controllers e serviços não fazem parte do runtime. A integração Mercado Pago/PIX foi removida.

## Regras de pontos e palpites

- um palpite só pode ser criado em evento e mercado abertos;
- o prazo de fechamento é validado no backend;
- o valor mínimo vem da configuração do mercado;
- o saldo é bloqueado em transação antes do débito;
- saldo insuficiente não cria palpite nem movimentação parcial;
- uma chave de idempotência impede duplicação;
- o vencedor recebe a recompensa em pontos calculada pelo multiplicador;
- o mesmo resultado não pode creditar recompensa duas vezes;
- cancelamento antes do limite devolve os pontos;
- cancelamento de evento reembolsa todos os palpites ativos;
- toda movimentação registra saldo final, origem, descrição e data.

## Autenticação e permissões

Rotas públicas:

- `POST /api/auth/login`
- `POST /api/auth/register`
- `GET /actuator/health`
- `/v3/api-docs/**`
- `/swagger-ui/**`

Comportamento esperado:

- credencial inválida ou usuário inexistente: `401` sem enumeração de conta;
- token ausente, inválido ou expirado: `401`;
- usuário autenticado sem o perfil necessário: `403`;
- `USER`, `PLAYER` e formas com prefixo `ROLE_` são normalizados para `PARTICIPANTE`;
- o claim de perfil do JWT é comparado com as authorities atuais do usuário;
- logout é stateless: a API responde `204` e o frontend remove o token local.

## API principal

### Participante

- `GET /api/dashboard`
- `GET /api/sports`
- `GET /api/championships`
- `GET /api/events`, `/api/events/live`, `/api/events/{id}`
- `GET|POST /api/predictions`
- `POST /api/predictions/{id}/cancel`
- `GET /api/wallet`, `/api/wallet/transactions`
- `GET|POST /api/pools`
- `GET /api/pools/{id}`
- `POST /api/pools/join`, `/api/pools/{id}/leave`
- `GET /api/pools/{id}/ranking`, `/api/rankings`
- `GET /api/notifications`
- `PATCH /api/notifications/{id}/read`, `/api/notifications/read-all`
- `GET|PATCH /api/profile`
- `PATCH /api/profile/password`, `/api/profile/preferences`
- `GET /api/achievements`, `/api/challenges`
- `GET|POST /api/community/posts`
- `POST /api/community/posts/{id}/like`, `/comments`, `/reports`
- `GET /api/community/posts/{id}/comments`

### Administração

- `GET /api/admin/dashboard`, `/users`, `/pools`, `/scoring-rules`;
- `GET /api/admin/reports`, `/audit`, `/settings`, `/moderation`;
- `GET|POST|PUT /api/admin/sports`, `/championships`, `/competitors`, `/events`, `/markets`;
- `PUT /api/admin/events/{id}/result`, `POST /api/admin/events/{id}/cancel`;
- `PATCH /api/admin/markets/{id}/status`, `POST /api/admin/markets/{id}/settle`;
- `GET|PATCH /api/admin/community/reports/**`, `PATCH /api/admin/community/posts/{id}`;
- `POST /api/admin/demo/live/refresh`.

Consulte o Swagger para payloads, enums e respostas atuais.

## Dados demo

Com `APP_DEMO_ENABLED=true`, o backend cria sem duplicar:

- futebol, basquete, vôlei, tênis, automobilismo, futebol americano, CS2, Valorant, League of Legends e Dota 2;
- campeonatos e competidores;
- eventos futuros, ao vivo e encerrados;
- mercados abertos, suspensos e processados;
- palpites ativos, vencedores e perdedores;
- carteiras, bônus inicial, débitos e recompensas;
- bolão público com ranking;
- notificações e conteúdo de demonstração.

Eventos ao vivo simulados mostram uma identificação visível de demonstração.

## Configuração

Copie `.env.example` para `.env` somente quando precisar alterar os padrões locais.

Variáveis mais importantes:

- `APP_BRAND_NAME`, `VITE_APP_NAME`, `VITE_APP_SHORT_NAME`, `VITE_APP_TAGLINE`;
- `APP_DEMO_ENABLED`, `APP_DEMO_LIVE_PROVIDER_ENABLED`, `VITE_DEMO_MODE`;
- `VITE_API_URL` (incorporada no build do frontend; requer `--build` após alteração);
- `JWT_SECRET`, `JWT_EXPIRATION_MINUTES`;
- `CORS_ALLOWED_ORIGINS`;
- `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD`, `POSTGRES_VOLUME_NAME`;
- `FRONTEND_PORT`, `BACKEND_PORT`.

O PostgreSQL permanece restrito à rede interna do Compose. O nome padrão do banco e do volume mantém a nomenclatura legada para preservar instalações existentes. Em uma instalação limpa, `POSTGRES_VOLUME_NAME` pode ser definido como `arenapredict_postgres_data`. Para executar o backend fora do Docker, configure `DB_URL`, `DB_USERNAME` e `DB_PASSWORD` explicitamente (o padrão de desenvolvimento local usa a porta `5433`).

Nunca use o segredo ou a senha de demonstração em ambiente publicado.

## Migrations

- `V1__baseline_and_arena_predict.sql`: preserva as tabelas legadas e cria o núcleo ArenaPredict com seus índices iniciais;
- `V2__profile_progression_and_community.sql`: adiciona perfil, preferências, conquistas, desafios e comunidade;
- `V3__harden_arena_core_constraints.sql`: adiciona 20 chaves estrangeiras, 12 validações `CHECK` e 10 índices de relacionamento;
- `V4__pool_types_and_challenge_windows.sql`: adiciona tipos de bolão/liga, recorrência e janelas repetíveis para desafios.

`spring.jpa.hibernate.ddl-auto=validate` garante que a aplicação não altere o schema silenciosamente.

## Desenvolvimento e testes

Backend:

```bash
cd backend
mvn test
mvn clean package
```

Frontend:

```bash
cd frontend
npm install
npm run typecheck
npm run test:run
npm run build
```

Infraestrutura:

```bash
docker compose config
docker compose up -d --build
docker compose ps
```

Os testes cobrem autenticação, claims JWT, 401/403, seed, saldo insuficiente, débito, idempotência e liquidação sem recompensa duplicada. O frontend cobre sessão, guards, loading/erro e fluxos principais.

## Estrutura

```text
backend/
  src/main/java/com/bolao/copa/
    arena/          # domínio e APIs novos
    security/       # JWT e handlers 401/403
    config/         # segurança, CORS e seed controlado
  src/main/resources/db/migration/
frontend/
  src/
    app/            # marca e formatadores
    components/     # shell e componentes do design system
    contexts/       # autenticação, toasts e dados globais
    pages/          # participante e administração
    services/       # cliente HTTP tipado
    types/          # contratos da interface
```

## Limites atuais

- O provider incluído é demonstrativo; integrações externas devem implementar as interfaces de provider e ser configuradas explicitamente.
- Rankings suportam períodos semanal, mensal e geral, com filtros globais, entre amigos e por modalidade; “entre amigos” considera participantes que compartilham bolões ou ligas.
- A migration preserva tabelas legadas, mas não converte automaticamente palpites antigos no novo formato de mercados.
- Antes de produção, adicione rotação de segredo, rate limiting distribuído, observação centralizada e uma política real de backup.
