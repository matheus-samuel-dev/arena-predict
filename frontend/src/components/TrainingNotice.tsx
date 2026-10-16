import { sessionStorage } from "../services/api";
import { useAppData } from "../contexts/AppDataContext";
import { getWalletBalance, points } from "../app/format";

export function TrainingNotice() {
  const session = sessionStorage.read();
  if (session?.demoProfile === "PARTICIPANT" && !session.demoTraining) return <PrivateTrainingNotice />;
  if(!session?.demoTraining) return null;
  return <p className="demo-shared-notice" role="note">Palpite de demonstração — pontos exclusivamente virtuais. Carteira, histórico e progresso exclusivos desta sessão. As partidas seguem o resultado oficial; não há simulação no evento real.</p>;
}

function PrivateTrainingNotice() {
  const { trainingWallet, trainingWalletError } = useAppData();
  return <p className="demo-shared-notice" role="note">Ambiente de demonstração · pontos virtuais. Histórico de exemplo preservado. Palpites em partidas reais usam sua carteira privada de treino{trainingWallet ? `: ${points(getWalletBalance(trainingWallet))} pts disponíveis.` : "."}{trainingWalletError && <span role="alert"> {trainingWalletError}</span>}</p>;
}
