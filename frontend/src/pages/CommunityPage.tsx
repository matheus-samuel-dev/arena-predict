import { AlertTriangle, BarChart3, Flag, Heart, LoaderCircle, MessageCircle, MoreHorizontal, Radio, Send, ShieldCheck, Sparkles, TrendingUp, Trophy, Users } from "lucide-react";
import { FormEvent, useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { relativeTime } from "../app/format";
import { Button, EmptyState, ErrorState, Modal, PageHeader, PageSkeleton, UserAvatar } from "../components/UI";
import { useAuth } from "../contexts/AuthContext";
import { useToast } from "../contexts/ToastContext";
import { useApiResource } from "../hooks/useApiResource";
import { asList, communityApi } from "../services/api";

interface CommunityPost {
  id: number | string;
  authorName?: string;
  author?: { name?: string; avatarUrl?: string };
  avatarUrl?: string;
  content?: string;
  createdAt?: string;
  likes?: number;
  likeCount?: number;
  comments?: number;
  commentCount?: number;
  likedByCurrentUser?: boolean;
  topic?: string;
}

interface CommunityComment {
  id: number | string;
  author?: { id?: number | string; name?: string; avatarUrl?: string };
  content?: string;
  createdAt?: string;
  ownedByCurrentUser?: boolean;
}

export function CommunityPage() {
  const [content, setContent] = useState("");
  const [posting, setPosting] = useState(false);
  const [reporting, setReporting] = useState(false);
  const [reportTarget, setReportTarget] = useState<CommunityPost | null>(null);
  const [commentTarget, setCommentTarget] = useState<CommunityPost | null>(null);
  const [reportReason, setReportReason] = useState("Conteúdo impróprio");
  const { user } = useAuth();
  const { notify } = useToast();
  const { data, setData, loading, error, reload } = useApiResource(async () => asList(await communityApi.feed()) as unknown as CommunityPost[], []);

  async function publish(event: FormEvent) {
    event.preventDefault();
    if (posting) return;
    if (content.trim().length < 3) { notify("Escreva uma mensagem antes de publicar.", "error"); return; }
    setPosting(true);
    try { await communityApi.createPost(content.trim()); setContent(""); notify("Publicação compartilhada com a comunidade.", "success"); await reload(); }
    catch (reason) { notify(reason instanceof Error ? reason.message : "Não foi possível publicar.", "error"); }
    finally { setPosting(false); }
  }

  async function like(post: CommunityPost) {
    try {
      const updated = await communityApi.like(post.id) as unknown as CommunityPost;
      setData((current) => current?.map((item) => item.id === post.id ? { ...item, ...updated } : item) ?? current);
    } catch (reason) {
      notify(reason instanceof Error ? reason.message : "Não foi possível curtir.", "error");
    }
  }

  async function report() {
    if (!reportTarget || reporting) return;
    setReporting(true);
    try { await communityApi.report(reportTarget.id, reportReason); notify("Denúncia enviada para moderação.", "success"); setReportTarget(null); }
    catch (reason) { notify(reason instanceof Error ? reason.message : "Não foi possível enviar a denúncia.", "error"); }
    finally { setReporting(false); }
  }

  function commentAdded(postId: number | string) {
    setData((current) => current?.map((post) => post.id === postId
      ? { ...post, commentCount: Number(post.commentCount ?? post.comments ?? 0) + 1 }
      : post) ?? current);
    setCommentTarget((current) => current?.id === postId
      ? { ...current, commentCount: Number(current.commentCount ?? current.comments ?? 0) + 1 }
      : current);
  }

  if (loading) return <PageSkeleton cards={3} />;
  const feed = data || [];
  const reactionCount = feed.reduce((total, post) => total + Number(post.likeCount ?? post.likes ?? 0), 0);
  const commentCount = feed.reduce((total, post) => total + Number(post.commentCount ?? post.comments ?? 0), 0);
  const topicCount = new Set(feed.map((post) => post.topic).filter(Boolean)).size;

  return (
    <>
      <PageHeader eyebrow="PRAÇA DA ARENA" title="Comunidade" description="Converse sobre eventos, compartilhe análises e mantenha a disputa respeitosa." />
      <section className="community-overview" aria-label="Resumo da comunidade">
        <article className="surface"><span><Users size={19} /></span><div><small>Publicações em destaque</small><strong>{feed.length}</strong><p>leituras recentes da comunidade</p></div></article>
        <article className="surface"><span><Heart size={19} /></span><div><small>Reações</small><strong>{reactionCount}</strong><p>interações neste mural</p></div></article>
        <article className="surface"><span><MessageCircle size={19} /></span><div><small>Debates ativos</small><strong>{commentCount}</strong><p>comentários publicados</p></div></article>
        <article className="surface"><span><TrendingUp size={19} /></span><div><small>Assuntos em alta</small><strong>{topicCount}</strong><p>modalidades e competições</p></div></article>
      </section>
      <div className="community-layout">
        <div>
          <form className="surface composer" onSubmit={publish}>
            <UserAvatar name={user?.name} avatarUrl={user?.avatarUrl} />
            <textarea value={content} onChange={(event) => setContent(event.target.value)} maxLength={600} placeholder="Qual é sua leitura para os próximos eventos?" aria-label="Nova publicação" />
            <footer><span>{content.length}/600</span><Button size="sm" type="submit" loading={posting}><Send size={15} /> Publicar</Button></footer>
          </form>
          {error && <ErrorState message={error} onRetry={() => reload().catch(() => undefined)} />}
          {!error && data?.length ? (
            <div className="feed">
              {data.map((post) => {
                const author = post.author?.name || post.authorName || "Participante da Arena";
                const commentCount = Number(post.commentCount ?? post.comments ?? 0);
                return (
                  <article className="surface post" key={post.id}>
                    <header>
                      <UserAvatar name={author} avatarUrl={post.author?.avatarUrl || post.avatarUrl} />
                      <div><strong>{author}</strong><span>{post.topic || "Discussão geral"} · {relativeTime(post.createdAt)}</span></div>
                      <button className="icon-button" type="button" onClick={() => setReportTarget(post)} aria-label="Denunciar publicação"><MoreHorizontal size={18} /></button>
                    </header>
                    <p>{post.content}</p>
                    <footer>
                      <button type="button" className={post.likedByCurrentUser ? "active" : ""} onClick={() => like(post)}><Heart size={17} /> {post.likeCount ?? post.likes ?? 0}</button>
                      <button type="button" onClick={() => setCommentTarget(post)} aria-label={`Abrir comentários de ${author}, ${commentCount} ${commentCount === 1 ? "comentário" : "comentários"}`}><MessageCircle size={17} /> {commentCount}</button>
                      <button type="button" onClick={() => setReportTarget(post)}><Flag size={16} /> Denunciar</button>
                    </footer>
                  </article>
                );
              })}
            </div>
          ) : !error && <EmptyState icon={Users} title="A conversa começa com você" description="Compartilhe uma análise e inaugure este mural." />}
        </div>
        <aside className="community-sidebar">
          <section className="surface trending-card">
            <header><span><TrendingUp size={19} /></span><div><small>EM ALTA AGORA</small><h2>Conversas para acompanhar</h2></div></header>
            <Link to="/live"><span><Radio size={17} /></span><div><strong>Palmeiras x Flamengo</strong><small>Evento ao vivo · dados simulados</small></div></Link>
            <Link to="/events"><span><BarChart3 size={17} /></span><div><strong>FURIA x NAVI</strong><small>Análise do segundo mapa</small></div></Link>
            <Link to="/pools"><span><Trophy size={17} /></span><div><strong>Liga Arena 2026</strong><small>Ranking e palpites da rodada</small></div></Link>
          </section>
          <section className="surface conduct-card"><span><ShieldCheck size={22} /></span><h2>Jogo limpo</h2><p>Debata ideias, não pessoas. Não exponha dados privados e sinalize conteúdo inadequado.</p><ul><li>Respeite todas as torcidas.</li><li>Não publique informações sensíveis.</li><li>Sem incentivo a apostas financeiras.</li></ul></section>
          <section className="surface community-topic"><span><Sparkles size={18} /></span><div><small>Tema em destaque</small><strong>Análises multiesportivas</strong><p>Compare como sua estratégia muda entre esportes e eSports.</p></div></section>
        </aside>
      </div>
      <CommentsModal post={commentTarget} onClose={() => setCommentTarget(null)} onCommentAdded={commentAdded} />
      <Modal open={Boolean(reportTarget)} onClose={() => !reporting && setReportTarget(null)} title="Denunciar publicação" size="sm"><div className="report-form"><span><AlertTriangle size={25} /></span><p>A equipe de moderação analisará a publicação sem revelar sua identidade ao autor.</p><label><span>Motivo</span><select value={reportReason} onChange={(event) => setReportReason(event.target.value)} disabled={reporting}><option>Conteúdo impróprio</option><option>Assédio ou discriminação</option><option>Spam</option><option>Exposição de dados pessoais</option><option>Incentivo a jogo financeiro</option></select></label><div className="modal-actions"><Button variant="secondary" onClick={() => setReportTarget(null)} disabled={reporting}>Cancelar</Button><Button variant="danger" onClick={report} loading={reporting}>Enviar denúncia</Button></div></div></Modal>
    </>
  );
}

function CommentsModal({ post, onClose, onCommentAdded }: {
  post: CommunityPost | null;
  onClose: () => void;
  onCommentAdded: (postId: number | string) => void;
}) {
  const [comments, setComments] = useState<CommunityComment[]>([]);
  const [content, setContent] = useState("");
  const [loading, setLoading] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [reloadKey, setReloadKey] = useState(0);
  const { notify } = useToast();
  const postId = post?.id;

  useEffect(() => {
    if (postId == null) {
      setComments([]);
      setContent("");
      setError(null);
      setLoading(false);
      return undefined;
    }

    let active = true;
    setComments([]);
    setContent("");
    setError(null);
    setLoading(true);
    communityApi.comments(postId)
      .then((result) => {
        if (active) setComments(asList(result) as unknown as CommunityComment[]);
      })
      .catch((reason) => {
        if (active) setError(reason instanceof Error ? reason.message : "Não foi possível carregar os comentários.");
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, [postId, reloadKey]);

  async function submit(event: FormEvent) {
    event.preventDefault();
    if (postId == null || submitting) return;
    const message = content.trim();
    if (message.length < 2) {
      notify("Escreva ao menos dois caracteres para comentar.", "error");
      return;
    }

    setSubmitting(true);
    try {
      const created = await communityApi.comment(postId, message) as unknown as CommunityComment;
      setComments((current) => current.some((comment) => comment.id === created.id) ? current : [...current, created]);
      setContent("");
      onCommentAdded(postId);
      notify("Comentário publicado.", "success");
    } catch (reason) {
      notify(reason instanceof Error ? reason.message : "Não foi possível publicar o comentário.", "error");
    } finally {
      setSubmitting(false);
    }
  }

  const author = post?.author?.name || post?.authorName || "Participante da Arena";
  return (
    <Modal open={Boolean(post)} onClose={onClose} title="Comentários" size="md">
      {post && (
        <div className="comments-panel">
          <div className="comments-context">
            <UserAvatar name={author} avatarUrl={post.author?.avatarUrl || post.avatarUrl} size="sm" />
            <div><strong>{author}</strong><p>{post.content}</p></div>
          </div>

          <div className="comments-list" aria-live="polite" aria-busy={loading}>
            {loading ? (
              <div className="comments-loading" role="status"><LoaderCircle className="spin" size={22} /><span>Carregando comentários...</span></div>
            ) : error ? (
              <ErrorState message={error} onRetry={() => setReloadKey((value) => value + 1)} />
            ) : comments.length ? comments.map((comment) => (
              <article className="comment-row" key={comment.id}>
                <UserAvatar name={comment.author?.name} avatarUrl={comment.author?.avatarUrl} size="sm" />
                <div>
                  <header><strong>{comment.author?.name || "Participante da Arena"}</strong><span>{relativeTime(comment.createdAt)}</span></header>
                  <p>{comment.content}</p>
                </div>
              </article>
            )) : (
              <EmptyState icon={MessageCircle} title="Ainda não há comentários" description="Comece uma conversa respeitosa sobre esta análise." />
            )}
          </div>

          <form className="comment-composer" onSubmit={submit}>
            <label htmlFor="community-comment">Seu comentário</label>
            <textarea id="community-comment" value={content} onChange={(event) => setContent(event.target.value)} maxLength={600} disabled={loading} placeholder="Contribua com a discussão..." />
            <footer><span>{content.length}/600</span><Button size="sm" type="submit" loading={submitting} disabled={loading || content.trim().length < 2}><Send size={15} /> Comentar</Button></footer>
          </form>
        </div>
      )}
    </Modal>
  );
}
