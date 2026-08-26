import { Component, ErrorInfo, ReactNode } from "react";
import { Brand } from "./Brand";

type Props = { children: ReactNode };
type State = { failed: boolean };

/** Last-resort recovery screen. Error details are intentionally not exposed. */
export class AppErrorBoundary extends Component<Props, State> {
  state: State = { failed: false };

  static getDerivedStateFromError(): State {
    return { failed: true };
  }

  componentDidCatch(_error: Error, _info: ErrorInfo) {
    // This boundary is the integration point for a future observability provider.
    // The demo does not transmit stack traces or user data to external services.
  }

  render() {
    if (!this.state.failed) return this.props.children;

    return (
      <main className="standalone-state" role="alert" aria-live="assertive">
        <Brand />
        <span className="standalone-state__code" aria-hidden="true">!</span>
        <h1>Não foi possível exibir esta tela</h1>
        <p>Seus dados permanecem seguros. Recarregue a plataforma para tentar novamente.</p>
        <button className="button button--primary button--md" type="button" onClick={() => window.location.reload()}>
          Recarregar a Arena
        </button>
      </main>
    );
  }
}
