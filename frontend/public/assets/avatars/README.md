# Avatares demo locais

Os nove retratos existentes foram preservados: Administrador Demo, Jogador
Demo, Ana Ribeiro, Beatriz Nunes, Camila Rocha, Diego Ferreira, Lucas Almeida,
Marina Costa e Rafael Lima.

Os 16 novos WEBP (256 × 256) usam
[Notionists Neutral, de Zoish](https://www.dicebear.com/styles/notionists-neutral/),
sob [CC0 1.0](https://creativecommons.org/publicdomain/zero/1.0/).
Obtidos da API oficial DiceBear 10.x em 14/09/2026, com conteúdo e seeds únicos.
São ilustrações, não retratos reais. Não existem requisições externas em runtime.

Seed de cada arquivo: `arenapredict-<nome-do-arquivo-sem-extensão>`.
URL de reprodução:
`https://api.dicebear.com/10.x/notionists-neutral/webp?seed=arenapredict-<slug>&size=256&backgroundColor=e8e5f5`.

Perfis novos: Sofia Martins, Gabriel Souza, Larissa Freitas, Pedro Henrique,
Isabela Moraes, Bruno Carvalho, Júlia Azevedo, Matheus Rocha, Helena Barros,
Caio Nogueira, Alice Teixeira, Vitor Mendes, Renata Alves, Thiago Monteiro,
Manuela Dias e Enzo Correia.

O catálogo atribui o avatar ao perfil pelo e-mail exato do seed, não pelo nome.
Cadastros normais sem avatar continuam usando iniciais, inclusive quando possuem
o mesmo nome de um demo. Se um retrato local falhar, `UserAvatar` tenta
`avatar-default.webp` e depois um rosto SVG embutido: nunca letras.
