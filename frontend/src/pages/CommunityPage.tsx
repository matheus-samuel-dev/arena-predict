import { AlertTriangle, BarChart3, Flag, Heart, LoaderCircle, MessageCircle, MoreHorizontal, Radio, RefreshCcw, Send, ShieldCheck, TrendingUp, Trophy, Users } from "lucide-react";
import { FormEvent, useEffect, useRef, useState } from "react";
import { Link } from "react-router-dom";
import { dateTime, relativeTime } from "../app/format";
import { Button, EmptyState, ErrorState, Modal, PageHeader, PageSkeleton, UserAvatar } from "../components/UI";
import { useAuth } from "../contexts/AuthContext";
import { useToast } from "../contexts/ToastContext";
import { useApiResource } from "../hooks/useApiResource";
import { asList, communityApi } from "../services/api";
import type { CommunityComment, CommunityPost } from "../types";

export function CommunityPage() {
  const [content, setContent] = useState("");
  const [topic, setTopic] = useState("");
  const [posting, setPosting] = useState(false);
  const [likingPostIds, setLikingPostIds] = useState<Set<string>>(() => new Set());
  const postingRequestRef = useRef(false);
  const likingRequestIdsRef = useRef<Set<string>>(new Set());
  const [reporting, setReporting] = useState(false);
  const [reportTarget, setReportTarget] = useState<CommunityPost | null>(null);
  const [commentTarget, setCommentTarget] = useState<CommunityPost | null>(null);
  const [reportReason, setReportReason] = useState("Conteúdo impróprio");
  const { user } = useAuth();
  const { notify } = useToast();
  const { data, setData, loading, error, reload, refresh } = useApiResource(async () => asList(await communityApi.feed()), []);

  async function publish(event: FormEvent) {
    event.preventDefault();
    if (postingRequestRef.current) return;
    if (content.trim().length < 3) { notify("Escreva uma mensagem antes de publicar.", "error"); return; }
    postingRequestRef.current = true;
    setPosting(true);
    try {
      await communityApi.createPost(content.trim(), topic.trim() || undefined);
      setContent("");
      setTopic("");
      notify("Publicação compartilhada com a comunidade.", "success");
      await refresh();
    }
    catch (reason) { notify(reason instanceof Error ? reason.message : "Não foi possível publicar.", "error"); }
    finally { postingRequestRef.current = false; setPosting(false); }
  }

  async function like(post: CommunityPost) {
    const postKey = String(post.id);
    if (post.likedByCurrentUser || likingRequestIdsRef.current.has(postKey)) return;
    likingRequestIdsRef.current.add(postKey);
    setLikingPostIds((current) => new Set(current).add(postKey));
    try {
      const updated = await communityApi.like(post.id);
      setData((current) => current?.map((item) => item.id === post.id ? { ...item, ...updated } : item) ?? current);
      notify("Reação registrada.", "success");
    } catch (reason) {
      notify(reason instanceof Error ? reason.message : "Não foi possível curtir.", "error");
    } finally {
      setLikingPostIds((current) => {
        const next = new Set(current);
        next.delete(postKey);
        return next;
      });
      likingRequestIdsRef.current.delete(postKey);
    }
  }

  async function refreshFeed() {
    try {
      await refresh();
      notify("Feed atualizado.", "success");
    } catch (reason) {
      notify(reason instanceof Error ? reason.message : "Não foi possível atualizar o feed.", "error");
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
      ? { ...post, commentCount: Number(post.commentCount ?? 0) + 1 }
      : post) ?? current);
    setCommentTarget((current) => current?.id === postId
      ? { ...current, commentCount: Number(current.commentCount ?? 0) + 1 }
      : current);
  }

  if (loading) return <PageSkeleton cards={3} />;
  return (
    <>
      <PageHeader
        eyebrow="PRAÇA DA ARENA"
        title="Comunidade"
        description="Converse sobre eventos, compartilhe análises e mantenha a disputa respeitosa."
        actions={<Button variant="secondary" size="sm" type="button" onClick={() => void refreshFeed()}><RefreshCcw size={16} /> Atualizar feed</Button>}
      />
      <div className="community-layout">
        <div>
          <form className="surface composer" onSubmit={publish}>
            <UserAvatar name={user?.name} avatarUrl={user?.avatarUrl} />
            <input
              className="composer__topic"
              value={topic}
              onChange={(event) => setTopic(event.target.value)}
              maxLength={80}
              placeholder="Assunto opcional · ex.: Palmeiras x Flamengo"
              aria-label="Assunto da publicação (opcional)"
              disabled={posting}
            />
            <textarea value={content} onChange={(event) => setContent(event.target.value)} maxLength={600} placeholder="Qual é sua leitura para os próximos eventos?" aria-label="Nova publicação" disabled={posting} />
            <footer><span>{content.length}/600</span><Button size="sm" type="submit" loading={posting}><Send size={15} /> Publicar</Button></footer>
          </form>
          {error && <ErrorState message={error} onRetry={() => reload().catch(() => undefined)} />}
          {!error && data?.length ? (
            <div className="feed">
              {data.map((post) => {
                const author = post.author?.name || post.authorName || "Participante da Arena";
                const commentCount = Number(post.commentCount ?? 0);
                return (
                  <article className="surface post" key={post.id}>
                    <header>
                      <UserAvatar name={author} avatarUrl={post.author?.avatarUrl || post.avatarUrl} />
                      <div><strong>{author}</strong><span>{post.topic || "Discussão geral"} · <time dateTime={post.createdAt} title={dateTime(post.createdAt, true)}>{relativeTime(post.createdAt)}</time></span></div>
                      <button className="icon-button" type="button" onClick={() => setReportTarget(post)} aria-label="Denunciar publicação"><MoreHorizontal size={18} /></button>
                    </header>
                    <p>{post.content}</p>
                    <footer>
                      <button
                        type="button"
                        className={post.likedByCurrentUser ? "active" : ""}
                        onClick={() => void like(post)}
                        aria-label={`${post.likedByCurrentUser ? "Publicação curtida de" : "Curtir publicação de"} ${author}, ${post.likeCount ?? 0} ${(post.likeCount ?? 0) === 1 ? "curtida" : "curtidas"}`}
                        aria-pressed={Boolean(post.likedByCurrentUser)}
                        aria-busy={likingPostIds.has(String(post.id)) || undefined}
                        disabled={Boolean(post.likedByCurrentUser) || likingPostIds.has(String(post.id))}
                      >
                        {likingPostIds.has(String(post.id)) ? <LoaderCircle className="spin" size={17} aria-hidden="true" /> : <Heart size={17} aria-hidden="true" />} {post.likeCount ?? 0}
                      </button>
                      <button type="button" onClick={() => setCommentTarget(post)} aria-label={`Abrir comentários de ${author}, ${commentCount} ${commentCount === 1 ? "comentário" : "comentários"}`}><MessageCircle size={17} aria-hidden="true" /> {commentCount}</button>
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
            <Link to="/live"><span><Radio size={17} /></span><div><strong>Palmeiras x Flamengo</strong><small>Evento ao vivo · acompanhe agora</small></div></Link>
            <Link to="/events"><span><BarChart3 size={17} /></span><div><strong>FURIA x NAVI</strong><small>Análise do segundo mapa</small></div></Link>
            <Link to="/pools"><span><Trophy size={17} /></span><div><strong>Liga Arena 2026</strong><small>Ranking e palpites da rodada</small></div></Link>
          </section>
          <section className="surface conduct-card"><span><ShieldCheck size={22} /></span><h2>Jogo limpo</h2><p>Debata ideias, não pessoas. Não exponha dados privados e sinalize conteúdo inadequado.</p><ul><li>Respeite todas as torcidas.</li><li>Não publique informações sensíveis.</li><li>Sem incentivo a apostas financeiras.</li></ul></section>
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
  const submittingRequestRef = useRef(false);
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
        if (active) setComments(asList(result));
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
    if (postId == null || submittingRequestRef.current) return;
    const message = content.trim();
    if (message.length < 2) {
      notify("Escreva ao menos dois caracteres para comentar.", "error");
      return;
    }

    submittingRequestRef.current = true;
    setSubmitting(true);
    try {
      const created = await communityApi.comment(postId, message);
      setComments((current) => current.some((comment) => comment.id === created.id) ? current : [...current, created]);
      setContent("");
      onCommentAdded(postId);
      notify("Comentário publicado.", "success");
    } catch (reason) {
      notify(reason instanceof Error ? reason.message : "Não foi possível publicar o comentário.", "error");
    } finally {
      submittingRequestRef.current = false;
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
                  <header><strong>{comment.author?.name || "Participante da Arena"}</strong><span><time dateTime={comment.createdAt} title={dateTime(comment.createdAt, true)}>{relativeTime(comment.createdAt)}</time></span></header>
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
