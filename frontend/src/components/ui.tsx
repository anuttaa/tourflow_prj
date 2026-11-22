import { ReactNode, useCallback, useEffect, useState } from 'react';
import { ApiError } from '../api';
import { STATUS_LABEL } from '../format';

export const Badge = ({ s }: { s: string }) => <span className={`badge ${s}`}>{STATUS_LABEL[s] ?? s}</span>;

export function Alert({ kind, children }: { kind: 'error' | 'ok' | 'warn'; children: ReactNode }) {
  return <div className={`alert ${kind}`} role={kind === 'error' ? 'alert' : 'status'}>{children}</div>;
}

export function ErrorBox({ error }: { error: unknown }) {
  if (!error) return null;
  const e = error as ApiError;
  return (
    <Alert kind="error">
      {e.message ?? 'Ошибка'}
      {e.errors && e.errors.length > 0 && <ul style={{ margin: '.3rem 0 0', paddingLeft: '1.1rem' }}>{e.errors.map((x, i) => <li key={i}>{x.field}: {x.message}</li>)}</ul>}
    </Alert>
  );
}

export function Field({ label, error, children }: { label: string; error?: string; children: ReactNode }) {
  return <label className="field">{label}{children}{error && <span className="field-error">{error}</span>}</label>;
}

/** Загрузка данных с обработкой ошибок и повторной загрузкой. */
export function useLoad<T>(fn: () => Promise<T>, deps: unknown[]) {
  const [data, setData] = useState<T | null>(null);
  const [error, setError] = useState<unknown>(null);
  const [loading, setLoading] = useState(true);
  // eslint-disable-next-line react-hooks/exhaustive-deps
  const run = useCallback(() => { setLoading(true); fn().then((d) => { setData(d); setError(null); }).catch(setError).finally(() => setLoading(false)); }, deps);
  useEffect(run, [run]);
  return { data, error, loading, reload: run, setData };
}

export function Pager({ page, total, size, onPage }: { page: number; total: number; size: number; onPage: (p: number) => void }) {
  const pages = Math.max(1, Math.ceil(total / size));
  if (pages <= 1) return null;
  return (
    <div className="pager">
      <button className="ghost small" disabled={page <= 0} onClick={() => onPage(page - 1)}>← Назад</button>
      <span className="muted">Страница {page + 1} из {pages}</span>
      <button className="ghost small" disabled={page + 1 >= pages} onClick={() => onPage(page + 1)}>Вперёд →</button>
    </div>
  );
}

export function Modal({ title, onClose, children }: { title: string; onClose: () => void; children: ReactNode }) {
  useEffect(() => { const h = (e: KeyboardEvent) => e.key === 'Escape' && onClose(); window.addEventListener('keydown', h); return () => window.removeEventListener('keydown', h); }, [onClose]);
  return (
    <div className="modal-back" onMouseDown={(e) => e.target === e.currentTarget && onClose()}>
      <div className="modal" role="dialog" aria-label={title}>
        <div className="row between"><h2>{title}</h2><button className="ghost small" onClick={onClose} aria-label="Закрыть">✕</button></div>
        {children}
      </div>
    </div>
  );
}

export function Page({ title, lead, children, actions }: { title: string; lead?: string; children: ReactNode; actions?: ReactNode }) {
  return (
    <>
      <div className="hero row between">
        <div><h1>{title}</h1>{lead && <p>{lead}</p>}</div>
        {actions}
      </div>
      {children}
    </>
  );
}
