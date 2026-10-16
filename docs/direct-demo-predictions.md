# Palpites diretos do Participante Demo

## Regressão encontrada

A preparação do portfólio mudou o acesso rápido de `demoLogin(PARTICIPANT, true)` para o perfil compartilhado, para recuperar o histórico completo. `predictionReadOnly` continuou proibindo eventos oficiais nesse perfil. O `MarketList` combinava essa restrição com `disabled`, mesmo quando a disponibilidade esportiva retornava “Aberto ao vivo”. Os links desviavam o visitante para `/demo` e exigiam uma troca manual de sessão. Não houve alteração dos estados esportivos, saldos ou permissões do backend pelo seed.

## Correção concentrada

- O perfil principal permanece no cenário preenchido. Nenhum reset ou cópia de saldo/histórico.
- A aba prepara e retém uma capacidade assinada para o serviço de treino existente. Criações concorrentes compartilham a mesma promessa; um novo acesso Demo na mesma aba retoma o treino durante sua validade de 24h.
- O mesmo componente de mercado e formulário é usado nos dois casos. Para partidas reais LIVE de CS2, Valorant e LoL da PandaScore, o Demo usa `/api/training/predictions`; participantes normais seguem em `/api/predictions`. Eventos internos Demo mantêm seu fluxo e suas permissões existentes.
- O formulário lê a carteira correspondente ao evento. Falha ou ausência da carteira privada bloqueia a confirmação; o saldo maior do cenário compartilhado não autoriza um débito privado.
- A seleção continua respeitando a disponibilidade e a atividade de cada opção. O servidor revalida evento, horário, mercado, multiplicador, saldo, limite e idempotência. Nenhuma permissão foi ampliada e nenhum código de produção backend foi modificado.
- Os botões usam “Fazer palpite” e preservam o ID do evento no link. Não direcionam a seleção para uma ativação manual.
- O detalhe prioriza uma categoria com mercado aberto e apresenta os utilizáveis antes dos encerrados dentro dela. Categorias e mercados encerrados continuam consultáveis, sem alteração de estado. A seleção manual de categoria é preservada.

## Dados e isolamento

O token principal nunca é substituído pela capacidade de treino. Esta fica em `sessionStorage` próprio da aba, não em URL ou frontend compilado, e só é utilizada quando a identidade principal é Participante Demo. Uma troca de conta cancela a preparação pendente antes de enviar a operação. Um erro 401 do treino não encerra a identidade principal nem cria outra carteira silenciosamente.

O backend continua derivando a sessão do JWT validado. Não recebe um ID arbitrário de carteira/sessão para selecionar registros. As tabelas `demo_training_sessions`, `demo_training_predictions` e `demo_training_ledger` mantêm a propriedade da sessão; eventos oficiais são somente referenciados. A validação `PredictionSelectionRules` e o motor de liquidação são os existentes. Resultado parcial ou sob revisão não liquida o treino.

“Meus palpites” mantém o histórico de exemplo e acrescenta os registros pessoais identificados como **Demonstração · sua sessão**. Seus IDs de apresentação têm prefixo `training:` para evitar colisões entre tabelas. A falha do histórico privado é apresentada sem esconder o histórico de exemplo. Estatísticas seguem as previsões realmente consultadas; o ranking compartilhado não recebe resultados pessoais de treino.

O saldo do cenário continua ligado ao seu próprio ledger. O formulário e o aviso contextual mostram o saldo privado aplicável ao treino. Em “Seus pontos”, as carteiras e extratos podem ser consultados separadamente. Não há transferência nem soma fictícia entre os saldos.

## Limites preservados

O treino existente atende mercados LIVE_ONLY das três modalidades suportadas. Outros estados/modalidades continuam disponíveis para consulta, com motivo explícito quando não elegíveis. Não se liberaram mercados encerrados, suspensos ou sem dados suficientes.

A capacidade privada é da aba, com validade de 24h. Não é uma conta permanente, não transfere o histórico entre dispositivos e não renova saldo silenciosamente ao expirar. Logout da identidade principal não apaga os registros do servidor; um novo acesso Demo na mesma aba pode retomá-los durante a validade. Nenhum visitante de outra sessão obtém a capacidade pela conta Demo compartilhada.

## Regressão e publicação

Testes frontend cobrem seleção/confirmacão no mesmo formulário, contrato correto, saldo privado, preservação do histórico, IDs distintos, retomada, falha de rede/idempotência, expiração e troca de identidade. O teste backend adicional prova que o login compartilhado não autoriza uma previsão oficial, enquanto a capacidade assinada registra somente no treino e mantém a carteira compartilhada intacta. As suítes existentes cobrem saldo insuficiente, mercados bloqueados, correção de multiplicador, concorrência, resultado oficial, reembolso e liquidação exatamente uma vez. O mesmo teste integra o job PostgreSQL da CI.

Não há migration ou seed nesta correção. O serviço que precisa ser atualizado é o frontend. A produção backend permanece com os mesmos contratos e código de domínio aprovados; a revisão de imagem backend pode permanecer anterior, já que seu código de produção não mudou.
