import {
  Activity,
  ArrowRight,
  BarChart3,
  Check,
  Eye,
  EyeOff,
  Gamepad2,
  LockKeyhole,
  ShieldCheck,
  Sparkles,
  Trophy,
  Users,
  Zap,
} from "lucide-react";
import { FormEvent, KeyboardEvent, useEffect, useRef, useState } from "react";
import { Link, useLocation, useNavigate } from "react-router-dom";
import { brand, isExplicitDemoMode } from "../app/branding";
import { Brand } from "../components/Brand";
import { Button } from "../components/UI";
import { useAuth } from "../contexts/AuthContext";
import { useToast } from "../contexts/ToastContext";

export function postLoginDestination(role: string, requestedPath: string) {
  if (role === "ADMIN") return "/admin";
  if (!requestedPath.startsWith("/") || requestedPath.startsWith("/admin") || requestedPath === "/login") return "/app";
  return requestedPath;
}

export function LoginPage() {
  const [mode, setMode] = useState<"login" | "register">("login");
  const [name, setName] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [showPassword, setShowPassword] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);
  const [errorField, setErrorField] = useState<"name" | "email" | "password" | null>(null);
  const nameRef = useRef<HTMLInputElement>(null);
  const emailRef = useRef<HTMLInputElement>(null);
  const passwordRef = useRef<HTMLInputElement>(null);
  const { session, login, demoLogin, register, authenticating } = useAuth();
  const { notify } = useToast();
  const navigate = useNavigate();
  const location = useLocation();
  const redirectTo = (location.state as { from?: { pathname?: string } } | null)?.from?.pathname || "/app";

  useEffect(() => {
    document.title = `${brand.name} — ${brand.tagline}`;
    if (session) navigate(postLoginDestination(session.role, redirectTo), { replace: true });
  }, [session, navigate, redirectTo]);

  function showValidationError(field: "name" | "email" | "password", message: string) {
    setErrorField(field);
    setFormError(message);
    window.requestAnimationFrame(() => ({ name: nameRef, email: emailRef, password: passwordRef })[field].current?.focus());
  }

  function changeMode(nextMode: "login" | "register") {
    if (authenticating) return;
    setMode(nextMode);
    setFormError(null);
    setErrorField(null);
  }

  function navigateTabs(event: KeyboardEvent<HTMLButtonElement>) {
    if (event.key !== "ArrowLeft" && event.key !== "ArrowRight") return;
    event.preventDefault();
    const nextMode = mode === "login" ? "register" : "login";
    changeMode(nextMode);
    const target = event.currentTarget.parentElement?.querySelector<HTMLButtonElement>(`#auth-tab-${nextMode}`);
    window.requestAnimationFrame(() => target?.focus());
  }

  async function submit(event: FormEvent) {
    event.preventDefault();
    if (authenticating) return;
    setFormError(null);
    setErrorField(null);
    if (mode === "register" && name.trim().length < 3) {
      showValidationError("name", "Informe seu nome completo com pelo menos 3 caracteres.");
      return;
    }
    if (!email.trim()) {
      showValidationError("email", "Informe seu e-mail.");
      return;
    }
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email.trim())) {
      showValidationError("email", "Informe um e-mail válido, como voce@exemplo.com.");
      return;
    }
    if (!password) {
      showValidationError("password", "Informe sua senha.");
      return;
    }
    if (mode === "register" && password.length < 8) {
      showValidationError("password", "Crie uma senha com pelo menos 8 caracteres.");
      return;
    }
    try {
      const authenticated = mode === "login"
        ? await login(email, password)
        : await register({ name, email, password });
      notify(mode === "login" ? `Bem-vindo de volta, ${authenticated.name.split(" ")[0]}!` : "Conta criada. Sua arena já está pronta.", "success");
      navigate(postLoginDestination(authenticated.role, redirectTo), { replace: true });
    } catch (error) {
      setFormError(error instanceof Error ? error.message : "Não foi possível concluir o acesso. Tente novamente.");
    }
  }

  async function quickLogin(profile: "admin" | "participant") {
    if (authenticating || !isExplicitDemoMode) return;
    setMode("login");
    setFormError(null);
    setErrorField(null);
    try {
      const authenticated = await demoLogin(profile === "admin" ? "ADMIN" : "PARTICIPANT");
      notify(`Acesso demonstrativo como ${profile === "admin" ? "administrador" : "participante"} iniciado.`, "success");
      navigate(postLoginDestination(authenticated.role, "/app"), { replace: true });
    } catch (error) {
      setFormError(error instanceof Error ? error.message : "Não foi possível iniciar o acesso demonstrativo.");
    }
  }

  return (
    <main className="login-page">
      <section className="login-showcase">
        <div className="login-showcase__noise" aria-hidden="true" />
        <div className="login-showcase__glow login-showcase__glow--one" aria-hidden="true" />
        <div className="login-showcase__glow login-showcase__glow--two" aria-hidden="true" />
        <Brand />
        <div className="showcase-copy">
          <span className="eyebrow"><Sparkles size={14} /> Previsões esportivas reimaginadas</span>
          <h1>Leia o jogo.<br /><em>Domine a arena.</em></h1>
          <p>Transforme sua análise em pontos, desafie amigos e acompanhe sua evolução em esportes e eSports.</p>
          <div className="showcase-features">
            <div><span><Activity size={19} /></span><p><strong>Eventos ao vivo</strong><small>Acompanhamento demonstrativo transparente</small></p></div>
            <div><span><BarChart3 size={19} /></span><p><strong>Análise de desempenho</strong><small>Dados para evoluir a cada palpite</small></p></div>
            <div><span><Trophy size={19} /></span><p><strong>Bolões e ligas</strong><small>Competição saudável entre amigos</small></p></div>
          </div>
        </div>
        <div className="showcase-ticker" aria-hidden="true">
          <div><Gamepad2 size={15} /> CS2 <b>•</b> Futebol <b>•</b> NBA <b>•</b> Valorant <b>•</b> Tênis <b>•</b> LoL</div>
        </div>
      </section>

      <section className="login-form-side">
        <div className="login-mobile-brand"><Brand /></div>
        <div className="login-card">
          <div className="login-card__intro">
            <span className="mini-symbol"><Zap size={19} /></span>
            <h2>{mode === "login" ? "Entre na sua arena" : "Crie seu perfil"}</h2>
            <p>{mode === "login" ? "Continue de onde parou e acompanhe seus palpites." : "Comece com pontos virtuais e dispute seu primeiro ranking."}</p>
          </div>

          <div className="auth-tabs" role="tablist" aria-label="Tipo de acesso">
            <button id="auth-tab-login" type="button" role="tab" aria-selected={mode === "login"} aria-controls="auth-panel" tabIndex={mode === "login" ? 0 : -1} disabled={authenticating} className={mode === "login" ? "active" : ""} onKeyDown={navigateTabs} onClick={() => changeMode("login")}>Entrar</button>
            <button id="auth-tab-register" type="button" role="tab" aria-selected={mode === "register"} aria-controls="auth-panel" tabIndex={mode === "register" ? 0 : -1} disabled={authenticating} className={mode === "register" ? "active" : ""} onKeyDown={navigateTabs} onClick={() => changeMode("register")}>Criar conta</button>
          </div>

          <form className="auth-form" id="auth-panel" role="tabpanel" aria-labelledby={`auth-tab-${mode}`} aria-busy={authenticating} onSubmit={submit} noValidate>
            {mode === "register" && (
              <label htmlFor="auth-name">
                <span>Nome completo</span>
                <div className="field"><Users size={17} aria-hidden="true" /><input ref={nameRef} id="auth-name" required minLength={3} value={name} onChange={(event) => { setName(event.target.value); if (errorField === "name") { setErrorField(null); setFormError(null); } }} autoComplete="name" placeholder="Como você quer ser chamado?" aria-invalid={errorField === "name"} aria-describedby={errorField === "name" ? "auth-error" : undefined} /></div>
              </label>
            )}
            <label htmlFor="auth-email">
              <span>E-mail</span>
              <div className="field"><span className="field-at" aria-hidden="true">@</span><input ref={emailRef} id="auth-email" required type="email" value={email} onChange={(event) => { setEmail(event.target.value); if (errorField === "email") { setErrorField(null); setFormError(null); } }} autoComplete="email" inputMode="email" placeholder="voce@exemplo.com" aria-invalid={errorField === "email"} aria-describedby={errorField === "email" ? "auth-error" : undefined} /></div>
            </label>
            <label htmlFor="auth-password">
              <span>Senha</span>
              <div className="field"><LockKeyhole size={17} aria-hidden="true" /><input ref={passwordRef} id="auth-password" required minLength={mode === "register" ? 8 : undefined} type={showPassword ? "text" : "password"} value={password} onChange={(event) => { setPassword(event.target.value); if (errorField === "password") { setErrorField(null); setFormError(null); } }} autoComplete={mode === "login" ? "current-password" : "new-password"} placeholder={mode === "login" ? "Sua senha" : "Mínimo de 8 caracteres"} aria-invalid={errorField === "password"} aria-describedby={errorField === "password" ? "auth-error" : undefined} /><button type="button" onClick={() => setShowPassword((value) => !value)} aria-label={showPassword ? "Ocultar senha" : "Mostrar senha"} aria-controls="auth-password" aria-pressed={showPassword}>{showPassword ? <EyeOff size={17} aria-hidden="true" /> : <Eye size={17} aria-hidden="true" />}</button></div>
            </label>

            {formError && <div className="auth-error" id="auth-error" role="alert">{formError}</div>}

            <Button size="lg" type="submit" loading={authenticating}>
              {authenticating ? (mode === "login" ? "Entrando…" : "Criando conta…") : mode === "login" ? "Entrar na Arena" : "Criar minha conta"}
              {!authenticating && <ArrowRight size={18} />}
            </Button>
          </form>

          {mode === "login" && isExplicitDemoMode && (
            <div className="demo-access">
              <div className="divider"><span>Acesso rápido de demonstração</span></div>
              <div className="demo-access__buttons">
                <Button variant="secondary" onClick={() => quickLogin("participant")} disabled={authenticating} aria-label="Entrar na demonstração como participante"><Users size={17} /> Participante</Button>
                <Button variant="secondary" onClick={() => quickLogin("admin")} disabled={authenticating} aria-label="Entrar na demonstração como administrador"><ShieldCheck size={17} /> Administrador</Button>
              </div>
              <p><Check size={14} /> Acesso controlado pelo ambiente demonstrativo, sem exibir credenciais</p>
            </div>
          )}

          <div className="virtual-points-note">
            <ShieldCheck size={18} />
            <p><strong>Entretenimento responsável</strong><span>Os pontos são exclusivamente virtuais, sem depósito, saque ou valor financeiro.</span></p>
          </div>
        </div>
        <footer className="login-footer">
          <span>© {new Date().getFullYear()} {brand.name}</span>
          <Link to="/terms">Termos</Link>
          <Link to="/privacy">Privacidade</Link>
        </footer>
      </section>
    </main>
  );
}
