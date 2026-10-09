# Identidade e verificação de uma implantação

O checkout da EC2 não comprova qual artefato está ativo. As imagens recebem `org.opencontainers.image.revision`, o backend expõe somente revisão/modelo em `GET /api/version` e o frontend publica `/version.json`. Não há valores privados nesses contratos. Builds locais sem revisão declaram `unknown`.

Antes de publicar: confirmar checkout sem alterações rastreadas, CI da revisão exata, testes, build e `docker compose config --quiet`. Preservar o `.env` privado. Fazer backup PostgreSQL e verificar o arquivo antes de qualquer migration; nunca remover volumes.

Na EC2, com a revisão aprovada já no checkout:

```bash
export APP_BUILD_REVISION=$(git rev-parse HEAD)
docker compose config --quiet
docker compose build backend frontend
docker compose up -d --no-deps backend frontend
```

Recriar somente os serviços alterados. PostgreSQL permanece ativo. Não usar `docker compose down -v`. Não imprimir a configuração expandida, `docker inspect` completo ou variáveis de credenciais.

Verificar os dois endpoints públicos contra `git rev-parse HEAD`, o label das imagens por nome/tag e a saúde de cada container. O ID de configuração associado a um container pode diferir do digest de manifesto/tag no armazenamento Docker; registrar ambos, sem inferir divergência apenas pela diferença desses IDs.

Confirmar migrations e navegar pela URL publicada: login, Ao vivo, seleção/recibo de palpite e histórico. Registrar o SHA, horário e IDs dos eventos reais. Não chamar replay isolado de validação temporal de produção. O modo Demo conserva dados por sessão da aba; um novo acesso rápido é um novo treino.

Backup existente não comprova recuperação: ensaiar restauração em banco descartável isolado, comparar contagens e remover apenas esse ambiente após a verificação. Antes de operação comercial, definir retenção, cópia fora do host, RPO/RTO, alertas, responsável pelo incidente e procedimento de rollback testado.
