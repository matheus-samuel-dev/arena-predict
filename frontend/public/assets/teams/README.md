# Logos de equipes e identidades individuais

Arquivos locais usados pelo seed e pelo resolvedor compartilhado `TeamLogo`.
Não há hotlinks em runtime. O `object-fit: contain` existente preserva proporções.

## Fontes — consulta em 14/09/2026

| Asset | Entidade e referência |
| --- | --- |
| palmeiras.svg | Palmeiras — [vetor do manual oficial](https://commons.wikimedia.org/wiki/File:Palmeiras_logo.svg) |
| flamengo.svg | Flamengo — [brasão baseado na marca do clube](https://commons.wikimedia.org/wiki/File:Clube_de_Regatas_do_Flamengo_logo.svg) |
| boston-celtics.svg | NBA — [CDN oficial, ID 1610612738](https://cdn.nba.com/logos/nba/1610612738/primary/L/logo.svg) |
| dallas-mavericks.svg | NBA — [CDN oficial, ID 1610612742](https://cdn.nba.com/logos/nba/1610612742/primary/L/logo.svg) |
| furia.svg | FURIA — pantera oficial já existente, preservada; [organização](https://www.furia.gg/) |
| navi.svg | Natus Vincere — [vetor com origem no site oficial](https://commons.wikimedia.org/wiki/File:NAVI-Logo.svg) |
| leviatan.png | Leviatán — [perfil VLR e links oficiais](https://www.vlr.gg/team/2359/leviat-n), [PNG original](https://owcdn.net/img/61b8888cc3860.png) |
| loud.svg | LOUD — [vetor com origem em loud.gg](https://commons.wikimedia.org/wiki/File:LOUD_logo.svg) |
| t1.png | T1 — [site oficial](https://www.t1.gg/), [PNG original](https://images.squarespace-cdn.com/content/v1/62d09f54a49d6f1c78455cce/970025c6-e0f5-43bc-b4eb-73d6c1903c8c/T1+red.png?format=500w) |
| geng.svg | Gen.G — [vetor do CDN oficial da organização](https://commons.wikimedia.org/wiki/File:Gen.G_Logo.svg) |
| minas.svg | Minas Tênis Clube — [brasão SVG oficial](https://minastenisclube.com.br/wp-content/themes/minas-tenis-clube/assets/img/favicon/favicon.svg) |
| sada-cruzeiro.svg | Sada Cruzeiro — [SVG oficial](https://www.sadacruzeiro.com.br/wp-content/themes/sada/assets/images/sadacruzeiro.svg) |
| team-spirit.svg | Team Spirit — [atribuição e origem](https://commons.wikimedia.org/wiki/File:Team_Spirit_new_em.svg), autor Team Spirit, [CC BY-SA 4.0](https://creativecommons.org/licenses/by-sa/4.0/); fundo branco adicionado |
| team-liquid.svg | Team Liquid — [brand kit oficial](https://teamliquid.com/brand-kit), [Crest on light](https://cdn.prod.website-files.com/64bf6e8cda9043babe7ca006/65f44cd149e66e386d8d3da7_Crest-on-light.svg) |

As marcas identificam equipes reais em eventos simulados, sem vínculo,
patrocínio ou endosso. Direitos e marcas pertencem aos respectivos titulares.
A disponibilidade em sites/brand kits não concede licença comercial irrestrita;
consulte as condições das fontes. Palmeiras, Flamengo, NAVI, LOUD e Gen.G
estão classificados como PD-textlogo no Commons, com restrições de marca.

Carlos Alcaraz, Jannik Sinner, Max Verstappen, Lando Norris, Charles Leclerc e
Oscar Piastri mantêm identidades ilustradas originais, não retratos ou brasões
oficiais. Lewis Hamilton e George Russell usam badges individuais
determinísticos. Aurora Falcons e Horizonte Bears são fictícios e mantêm badges
distintos. O antigo `leviatan.svg` não é mais utilizado pelo catálogo/resolvedor.

Se uma imagem falhar, `TeamLogo` tenta o asset local e depois o badge
determinístico da entidade, seguido do placeholder local.

Team Spirit e Team Liquid receberam apenas fundo branco dentro do próprio
asset para preservar a leitura das marcas escuras nos dois temas. Não foram
alterados desenho, cores da marca, proporções ou estilos da aplicação.
A adaptação de Team Spirit mantém CC BY-SA 4.0.
