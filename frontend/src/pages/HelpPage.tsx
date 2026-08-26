import { BookOpen, ChevronDown, LifeBuoy, Mail, MessageCircleQuestion, ShieldCheck } from "lucide-react";
import { useState } from "react";
import { brand } from "../app/branding";
import { Button, PageHeader } from "../components/UI";
import { useToast } from "../contexts/ToastContext";

const questions = [
  ["Os pontos possuem valor em dinheiro?", "Não. Todos os pontos são virtuais e servem apenas para entretenimento, progressão, rankings e recompensas internas."],
  ["Como um palpite é calculado?", "Você escolhe um mercado, uma opção e uma quantidade de pontos. O coeficiente simulado mostra o potencial de recompensa, processado após o resultado oficial."],
  ["Posso cancelar um palpite?", "Somente quando a regra do mercado permitir e antes do limite do evento. O servidor valida o prazo e processa o reembolso elegível."],
  ["Como entro em um bolão privado?", "Use o código enviado pelo criador na página Bolões. O código identifica o grupo e respeita o limite de participantes."],
  ["Os eventos ao vivo são reais?", "Quando dados reais não estão configurados, o serviço interno usa simulação e a interface exibe a identificação “demonstração”."],
  ["Como funcionam conquistas e desafios?", "As regras são avaliadas pela plataforma após ações e resultados; conquistas não são concedidas apenas visualmente."],
];

export function HelpPage() {
  const [open, setOpen] = useState(0); const { notify } = useToast();
  async function copyEmail() { await navigator.clipboard.writeText(brand.supportEmail); notify("E-mail de suporte copiado.", "success"); }
  return <><PageHeader eyebrow="SUPORTE" title="Central de ajuda" description="Respostas claras sobre pontos, palpites, bolões e segurança da plataforma." /><section className="help-grid"><div className="surface faq-panel"><h2>Perguntas frequentes</h2>{questions.map(([question, answer], index) => <article className={open === index ? "open" : ""} key={question}><button type="button" onClick={() => setOpen(open === index ? -1 : index)} aria-expanded={open === index}><span>{question}</span><ChevronDown size={17} /></button>{open === index && <p>{answer}</p>}</article>)}</div><aside><section className="surface support-card"><span><LifeBuoy size={24} /></span><h2>Precisa falar com alguém?</h2><p>Nossa equipe recebe dúvidas sobre a experiência e relatos de segurança.</p><Button onClick={copyEmail}><Mail size={16} /> Copiar e-mail de suporte</Button><small>{brand.supportEmail}</small></section><section className="surface responsible-card"><ShieldCheck size={22} /><div><h2>Entretenimento responsável</h2><p>Sem dinheiro real, depósitos, saques ou promessa de lucro.</p></div></section></aside></section></>;
}

export function LegalPage({ type }: { type: "terms" | "privacy" }) {
  return <main className="legal-page"><a href="/" className="legal-brand">{brand.name}</a><article><span>{type === "terms" ? "TERMOS DE USO" : "PRIVACIDADE"}</span><h1>{type === "terms" ? "Regras para uma Arena justa" : "Seus dados, com respeito"}</h1><p>Esta é uma plataforma de entretenimento baseada exclusivamente em pontos virtuais. Não oferecemos apostas com dinheiro real, depósitos, saques, custódia ou conversão financeira.</p><h2>{type === "terms" ? "Conduta" : "Dados utilizados"}</h2><p>{type === "terms" ? "Participe com respeito, não manipule resultados, não tente acessar áreas sem permissão e não publique dados pessoais de terceiros." : "Utilizamos dados de conta para autenticação, personalização, rankings, auditoria e segurança. Senhas nunca devem ser armazenadas em texto simples."}</p><h2>Segurança e transparência</h2><p>Eventos simulados são identificados como demonstração. Resultados e regras de pontuação ficam sujeitos às configurações publicadas na plataforma.</p><a className="button button--primary button--md" href="/">Voltar para entrar</a></article></main>;
}
