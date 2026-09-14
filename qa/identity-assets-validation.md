# Validação exclusiva de avatares e logos — 14/09/2026

## Resultado
- 25 usuários demo com avatar persistido; 25 paths e conteúdos distintos.
- 16 novos avatares WEBP; nove retratos existentes preservados.
- 13 novos logos locais; pantera oficial de FURIA preservada (14 equipes reais).
- Quatro badges determinísticos mantidos: Lewis Hamilton, George Russell,
  Aurora Falcons e Horizonte Bears.
- Seis identidades individuais ilustradas existentes preservadas:
  Carlos Alcaraz, Jannik Sinner, Max Verstappen, Lando Norris,
  Charles Leclerc e Oscar Piastri.
- 48 arquivos de imagem servidos com HTTP 200 e Content-Type de imagem;
  nenhum 404.
- Catálogo administrativo: 24 logos/identidades carregados, zero imagens
  quebradas, todos com object-fit: contain.
- Ranking: 24 avatares distintos, zero iniciais.
- Usuários no Admin: 25 avatares, zero iniciais/imagens quebradas.
- Avatares reutilizados no header, visão geral, comunidade, comentários,
  perfil e rankings de bolões/ligas (24 avatares em cada ranking).
- Mesmos brasões em Eventos, detalhes e Ao vivo.
- Console da validação administrativa sem warnings/erros.
- Fallback visual demo testado com falha do retrato e do avatar padrão.
  Cadastro normal com nome igual ao demo mantém iniciais quando sem avatar.

## Comandos e resultado
- npm run build: passou (incluindo build Docker do frontend).
- npm run test:run: 18 arquivos, 141 testes passaram.
- npm run typecheck: passou.
- npm run lint: passou.
- mvn -B -Dtest=DemoParticipantAvatarTest,DemoAuthServiceTest,AuthServiceTest,DataInitializerTest,SecurityConfigTest test:
  17 testes passaram.
- Build Docker da API: passou.

## Escopo
Nenhuma página, CSS, layout, tipografia, tema, sidebar, regra de palpite,
mercado, ranking ou fluxo de bolão/liga foi alterado. DTOs de sessão e da lista
administrativa receberam somente o campo avatarUrl do perfil existente.
Seeds receberam somente paths de identidade e resolução de avatar do catálogo.
Fontes dos assets estão nos READMEs de avatars e teams.

## Arquivos adicionados ou alterados
- backend/src/main/java/com/bolao/copa/arena/api/AdminOperationsDtos.java
- backend/src/main/java/com/bolao/copa/arena/config/ArenaDemoInitializer.java
- backend/src/main/java/com/bolao/copa/arena/config/ArenaExperienceDemoInitializer.java
- backend/src/main/java/com/bolao/copa/arena/service/AdminOperationsService.java
- backend/src/main/java/com/bolao/copa/config/DemoParticipantCatalog.java
- backend/src/main/java/com/bolao/copa/dto/AuthDtos.java
- backend/src/main/java/com/bolao/copa/service/AuthService.java
- backend/src/main/java/com/bolao/copa/service/DemoAuthService.java
- backend/src/test/java/com/bolao/copa/service/DemoAuthServiceTest.java
- frontend/public/assets/teams/README.md
- frontend/src/components/TeamLogo.tsx
- frontend/src/components/UserAvatar.tsx
- frontend/src/test/team-logo.test.tsx
- frontend/src/test/user-avatar.test.tsx
- backend/src/test/java/com/bolao/copa/config/DemoParticipantAvatarTest.java
- frontend/public/assets/avatars/README.md
- frontend/public/assets/avatars/alice-teixeira.webp
- frontend/public/assets/avatars/bruno-carvalho.webp
- frontend/public/assets/avatars/caio-nogueira.webp
- frontend/public/assets/avatars/enzo-correia.webp
- frontend/public/assets/avatars/gabriel-souza.webp
- frontend/public/assets/avatars/helena-barros.webp
- frontend/public/assets/avatars/isabela-moraes.webp
- frontend/public/assets/avatars/julia-azevedo.webp
- frontend/public/assets/avatars/larissa-freitas.webp
- frontend/public/assets/avatars/manuela-dias.webp
- frontend/public/assets/avatars/matheus-rocha.webp
- frontend/public/assets/avatars/pedro-henrique.webp
- frontend/public/assets/avatars/renata-alves.webp
- frontend/public/assets/avatars/sofia-martins.webp
- frontend/public/assets/avatars/thiago-monteiro.webp
- frontend/public/assets/avatars/vitor-mendes.webp
- frontend/public/assets/teams/boston-celtics.svg
- frontend/public/assets/teams/dallas-mavericks.svg
- frontend/public/assets/teams/flamengo.svg
- frontend/public/assets/teams/geng.svg
- frontend/public/assets/teams/leviatan.png
- frontend/public/assets/teams/loud.svg
- frontend/public/assets/teams/minas.svg
- frontend/public/assets/teams/navi.svg
- frontend/public/assets/teams/palmeiras.svg
- frontend/public/assets/teams/sada-cruzeiro.svg
- frontend/public/assets/teams/t1.png
- frontend/public/assets/teams/team-liquid.svg
- frontend/public/assets/teams/team-spirit.svg
- qa/identity-assets-validation.md (este relatório)

