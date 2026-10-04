# Auditoria de consolidação — 04/10/2026

Auditoria realizada antes da implementação. Árvore limpa em `3a12ff3`; comparação
com `03284dc` (antes da integração/sandbox), `404a40e` e `06c6360`.
Comandos: status, log, diff, show, inventário de rotas, menus, controllers e services.
Nenhum checkout/reset realizado. O histórico consultado não apresenta exclusões de
arquivos em `frontend/src` ou `backend/src/main/java`.

## Evidência e classificação

| Área | Estado encontrado | Classe | Decisão |
|---|---|---|---|
| Visão geral, Eventos, detalhe, Ao vivo, Meus palpites | Implementadas; Demo só tinha Eventos/Palpites no menu | A/B | Restaurar navegação completa |
| Bolões, Ligas, Estatísticas, Desafios, Conquistas, Comunidade, Minha conta | Rotas e backend preservados; escondidos das contas Demo | A/B | Recuperar acesso; permitir operações próprias com isolamento |
| Rankings | Preservado, baseado em palpites persistidos | A/B | Manter filtros e pontuação |
| Carteira, Notificações, Ajuda | Rotas mantidas; acessos secundários | A | Manter |
| Painel, 16 páginas de recursos e status da integração | Código/API/menu completos; AdminRoute redirecionava Demo para /demo; política HTTP negava todo /api/admin | A/B | Liberar consultas explicitamente autorizadas; negar comandos genéricos Demo |
| Jornada /demo | Nova página com fluxo real de liquidação e reset isolado | B | Manter como ferramenta contextual, sem substituir produto |
| Perfil /profile e /account | Dois aliases da mesma página | C/F | Preservar compatibilidade; menu aponta /account |
| Ligas/Bolões | Mesmo componente, filtros distintos e regras diferentes no domínio | F | Preservar: liga organizada por admin, período obrigatório; bolão social por usuário |
| Resultados/Mercados | Implementações recentes de catálogo, disponibilidade e liquidação | F | Preservar motor atual |
| Provider/sync | Abstração, PandaScore CS2, scheduler, upsert, revisão de correção | A/F | Preservar |
| Banner global e aviso de catálogo Demo em toda página | Repetição visual que domina produto | C | Substituir por identificação de conta e entidade |
| Pagamentos e rotas Copa antigas | Não são a API suportada; segurança as nega | D | Não restaurar dinheiro/PIX/fluxo financeiro |
| Configurações/Pontuação/Relatórios | Consultas reais de configuração, regras e contagens; não CRUD completo | A | Manter consulta, não inventar switches |

Não existem módulos apagados ou sem rota no conjunto comparado. O problema foi
**ocultação por perfil e substituição da navegação**, não perda de arquivos. Nenhuma
página será reconstruída apenas para aumentar a sidebar.

## Plano executado nesta rodada

1. Restaurar grupos Principal, Competições e Comunidade/conta para participante Demo.
2. Restaurar grupos Operação, Catálogo, Engajamento, Gestão e Governança para admin Demo.
3. Entrada Demo no dashboard correspondente; /demo continua acessível como jornada.
4. Admin Demo consulta projeções seguras, sem e-mail real, segredos ou comandos genéricos.
5. Exibir origem/provider, identificadores externos e estado do processamento no catálogo.
6. Participante usa perfil, notificações e engajamento próprios; dados sociais Demo separados.
7. Palpites Demo apenas em eventos demonstrativos sem origem externa; liquidação compartilhada.
8. Regressões de menus/rotas, contratos, segurança, provider e ciclo Demo; builds e navegador.
9. Registrar resultado e limitações no relatório final, sem declarar validações não executadas.
