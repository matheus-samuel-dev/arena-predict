import { Link } from "react-router-dom";
import type { ArenaEvent } from "../types";

/** Explain account permissions without presenting an open market as unavailable. */
export function DemoPredictionAccess({ event, onContinue }: { event: ArenaEvent; onContinue?: () => void }) {
  return <div className="demo-guidance" role="note" aria-label="Como experimentar os palpites">
    <p>{event.demo
      ? "Esta rodada permite palpites com Participante Demo. Você pode trocar de perfil na demonstração."
      : "O Jogador Demo pode experimentar partidas reais no treino ao vivo, com carteira e histórico isolados. Abra a demonstração e escolha Entrar no treino ao vivo. Contas cadastradas possuem histórico permanente."}</p>
    <Link className="button button--primary button--sm" to="/demo" onClick={onContinue}>Fazer um palpite Demo</Link>
  </div>;
}
