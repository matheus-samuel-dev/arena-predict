import { sessionStorage } from "../services/api";

export function TrainingNotice() {
  if(!sessionStorage.read()?.demoTraining) return null;
  return <p className="demo-shared-notice" role="note">Palpite de demonstração — pontos exclusivamente virtuais. Carteira, histórico e progresso exclusivos desta sessão. As partidas seguem o resultado oficial; não há simulação no evento real.</p>;
}
