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
import { FormEvent, useEffect, useState } from "react";
import { Link, useLocation, useNavigate } from "react-router-dom";
import { brand, demoCredentials } from "../app/branding";
import { Brand } from "../components/Brand";
import { Button } from "../components/UI";
import { useAuth } from "../contexts/AuthContext";
import { useToast } from "../contexts/ToastContext";

export function LoginPage() {
  const [mode, setMode] = useState<"login" | "register">("login");
  const [name, setName] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [showPassword, setShowPassword] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);
  const { session, login, register, authenticating } = useAuth();
  const { notify } = useToast();
  const navigate = useNavigate();
  const location = useLocation();
  const redirectTo = (location.state as { from?: { pathname?: string } } | null)?.from?.pathname || "/app";

  useEffect(() => {
    document.title = `${brand.name} — ${brand.tagline}`;
    if (session) navigate(session.role === "ADMIN" ? "/admin" : redirectTo, { replace: true });
  }, [session, navigate, redirectTo]);

  async function submit(event: FormEvent) {
    event.preventDefault();
    setFormError(null);
    if (!email.trim() || !password) {
      setFormError("Informe seu e-mail e sua senha.");
      return;
    }
    if (mode === "register" && name.trim().length < 3) {
      setFormError("Informe seu nome completo.");
      return;
    }
    try {
      const authenticated = mode === "login"
        ? await login(email, password)
        : await register({ name, email, password });
      notify(mode === "login" ? `Bem-vindo de volta, ${authenticated.name.split(" ")[0]}!` : "Conta criada. Sua arena já está pronta.", "success");
      navigate(authenticated.role === "ADMIN" ? "/admin" : redirectTo, { replace: true });
    } catch (error) {
      setFormError(error instanceof Error ? error.message : "Não foi possível entrar.");
    }
  }

  async function quickLogin(profile: "admin" | "participant") {
    const credential = demoCredentials[profile];
    setMode("login");
    setEmail(credential.email);
    setPassword(credential.password);
    setFormError(null);
    try {
      const authenticated = await login(credential.email, credential.password);
      notify(`Acesso demo como ${profile === "admin" ? "administrador" : "participante"} iniciado.`, "success");
      navigate(authenticated.role === "ADMIN" ? "/admin" : "/app", { replace: true });
    } catch (error) {
      setFormError(error instanceof Error ? error.message : "Não foi possível entrar.");
    }
  }

  return (
    <main className="login-page">
      <section className="login-showcase">
        <div className="login-showcase__noise" />
        <div className="login-showcase__glow login-showcase__glow--one" />
        <div className="login-showcase__glow login-showcase__glow--two" />
        <Brand />
        <div className="showcase-copy">
          <span className="eyebrow"><Sparkles size={14} /> Previsões esportivas reimaginadas</span>
          <h1>Leia o jogo.<br /><em>Domine a arena.</em></h1>
          <p>Transforme sua análise em pontos, desafie amigos e acompanhe sua evolução em esportes e eSports.</p>
          <div className="showcase-features">
            <div><span><Activity size={19} /></span><p><strong>Eventos ao vivo</strong><small>Acompanhamento demo transparente</small></p></div>
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
            <button type="button" className={mode === "login" ? "active" : ""} onClick={() => { setMode("login"); setFormError(null); }}>Entrar</button>
            <button type="button" className={mode === "register" ? "active" : ""} onClick={() => { setMode("register"); setFormError(null); }}>Criar conta</button>
          </div>

          <form className="auth-form" onSubmit={submit} noValidate>
            {mode === "register" && (
              <label>
                <span>Nome completo</span>
                <div className="field"><Users size={17} /><input value={name} onChange={(event) => setName(event.target.value)} autoComplete="name" placeholder="Como você quer ser chamado?" /></div>
              </label>
            )}
            <label>
              <span>E-mail</span>
              <div className="field"><span className="field-at">@</span><input type="email" value={email} onChange={(event) => setEmail(event.target.value)} autoComplete="email" placeholder="voce@exemplo.com" /></div>
            </label>
            <label>
              <span>Senha</span>
              <div className="field"><LockKeyhole size={17} /><input type={showPassword ? "text" : "password"} value={password} onChange={(event) => setPassword(event.target.value)} autoComplete={mode === "login" ? "current-password" : "new-password"} placeholder="Sua senha" /><button type="button" onClick={() => setShowPassword((value) => !value)} aria-label={showPassword ? "Ocultar senha" : "Mostrar senha"}>{showPassword ? <EyeOff size={17} /> : <Eye size={17} />}</button></div>
            </label>

            {formError && <div className="auth-error" role="alert">{formError}</div>}

            <Button size="lg" type="submit" loading={authenticating}>
              {authenticating ? "Entrando..." : mode === "login" ? "Entrar na Arena" : "Criar minha conta"}
              {!authenticating && <ArrowRight size={18} />}
            </Button>
          </form>

          {mode === "login" && (
            <div className="demo-access">
              <div className="divider"><span>Acesso rápido de demonstração</span></div>
              <div className="demo-access__buttons">
                <Button variant="secondary" onClick={() => quickLogin("participant")} disabled={authenticating}><Users size={17} /> Participante</Button>
                <Button variant="secondary" onClick={() => quickLogin("admin")} disabled={authenticating}><ShieldCheck size={17} /> Administrador</Button>
              </div>
              <p><Check size={14} /> Credenciais demo preenchidas e enviadas ao backend</p>
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
