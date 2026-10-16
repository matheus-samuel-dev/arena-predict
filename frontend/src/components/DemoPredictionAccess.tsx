import type { ArenaEvent } from "../types";

/** Explain account permissions without presenting an open market as unavailable. */
export function DemoPredictionAccess({ event }: { event: ArenaEvent; onContinue?: () => void }) {
  return <div className="demo-guidance" role="note" aria-label="Como experimentar os palpites">
    <p>{event.demo
      ? "Esta rodada permite palpites com Participante Demo. Outros perfis têm acesso somente de leitura."
      : "Nesta sessão, o treino está disponível em mercados ao vivo de CS2, Valorant e League of Legends da PandaScore. Outros eventos permanecem disponíveis para acompanhamento."}</p>
  </div>;
}
