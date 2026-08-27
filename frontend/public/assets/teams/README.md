# Logos de participantes

O catálogo visual usa `TeamLogo` para resolver aliases conhecidos e sempre
oferecer fallback por iniciais. Os assets oficiais abaixo são carregados de
URLs estáveis enquanto o projeto não possui um pipeline de mídia local. A
estrutura está pronta para substituir cada URL por um arquivo versionado em
`frontend/public/assets/teams/` quando houver uma política de distribuição
aprovada para as marcas:

- Palmeiras: [Wikimedia Commons – Palmeiras logo.svg](https://commons.wikimedia.org/wiki/File:Palmeiras_logo.svg)
- Flamengo: [Wikimedia Commons – Clube de Regatas do Flamengo logo.svg](https://commons.wikimedia.org/wiki/File:Clube_de_Regatas_do_Flamengo_logo.svg)
- Team Vitality: [Wikipedia – Team Vitality](https://en.wikipedia.org/wiki/Team_Vitality), com o arquivo publicado no Wikimedia
- G2 Esports: [diretrizes oficiais de marca](https://g2esports.com/pages/brand-guidelines) e arquivo referenciado no Wikimedia Commons

As marcas pertencem aos respectivos clubes/equipes. O componente trata
indisponibilidade de rede, URL inválida e participantes sem imagem sem exibir
ícone de imagem quebrada.
