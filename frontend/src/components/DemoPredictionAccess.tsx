import { Link } from "react-router-dom";
import type { ArenaEvent } from "../types";

/** Explain account permissions without presenting an open market as unavailable. */
export function DemoPredictionAccess({ event, onContinue }: { event: ArenaEvent; onContinue?: () => void }) {
  return <div className="demo-guidance" role="note" aria-label="Como experimentar os palpites">
    <p>{event.demo
      ? "Esta rodada permite palpites com Participante Demo. Você pode trocar de perfil na demonstração."
      : "Para participar de eventos reais, use uma conta cadastrada. O Jogador Demo experimenta os mesmos palpites e pontos em uma rodada demonstrativa separada."}</p>
    <Link className="button button--primary button--sm" to="/demo" onClick={onContinue}>Fazer um palpite Demo</Link>
  </div>;
}
