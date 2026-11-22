export class ApiError extends Error {
  constructor(public status: number, public code: string, message: string, public errors?: { field: string; message: string }[]) {
    super(message);
  }
}

const BASE = '/api/v1';
let access: string | null = null;
let onSession: ((s: Session | null) => void) | null = null;
let refreshing: Promise<boolean> | null = null;

export interface SessionUser { id: string; email: string; roles: string[]; firstName: string }
export interface Session { accessToken: string; refreshToken: string; user: SessionUser }

export const setSessionListener = (f: (s: Session | null) => void) => { onSession = f; };
export const setAccess = (t: string | null) => { access = t; };

function applySession(s: Session | null) {
  access = s ? s.accessToken : null;
  if (s) localStorage.setItem('tf_refresh', s.refreshToken); else localStorage.removeItem('tf_refresh');
  onSession?.(s);
}

async function raw(path: string, method: string, body?: unknown, query?: Record<string, unknown>): Promise<Response> {
  const qs = query
    ? '?' + Object.entries(query).filter(([, v]) => v !== undefined && v !== null && v !== '').map(([k, v]) => `${encodeURIComponent(k)}=${encodeURIComponent(String(v))}`).join('&')
    : '';
  const headers: Record<string, string> = {};
  if (body !== undefined) headers['Content-Type'] = 'application/json';
  if (access) headers['Authorization'] = `Bearer ${access}`;
  return fetch(BASE + path + (qs === '?' ? '' : qs), { method, headers, body: body !== undefined ? JSON.stringify(body) : undefined });
}

export async function refreshSession(): Promise<boolean> {
  const rt = localStorage.getItem('tf_refresh');
  if (!rt) return false;
  if (!refreshing) {
    refreshing = (async () => {
      try {
        const res = await raw('/auth/refresh', 'POST', { refreshToken: rt });
        if (!res.ok) { applySession(null); return false; }
        applySession((await res.json()) as Session);
        return true;
      } catch { return false; } finally { refreshing = null; }
    })();
  }
  return refreshing;
}

export async function api<T = any>(path: string, opts: { method?: string; body?: unknown; query?: Record<string, unknown> } = {}): Promise<T> {
  const method = opts.method ?? 'GET';
  let res: Response;
  try { res = await raw(path, method, opts.body, opts.query); }
  catch { throw new ApiError(0, 'NETWORK', 'Нет связи с сервером. Проверьте подключение и повторите.'); }
  if (res.status === 401 && !path.startsWith('/auth/') && (await refreshSession())) res = await raw(path, method, opts.body, opts.query);
  if (res.status === 204) return undefined as T;
  const text = await res.text();
  let data: any = null;
  try { data = text ? JSON.parse(text) : null; } catch { /* не JSON */ }
  if (!res.ok) {
    const msg = res.status === 429 ? 'Слишком много запросов, подождите немного.' : data?.detail ?? data?.message ?? 'Ошибка запроса';
    throw new ApiError(res.status, data?.code ?? 'ERROR', msg, data?.errors);
  }
  return data as T;
}

// Удобные алиасы на случай вызова api.get / api.post
api.get = <T = any>(path: string, query?: Record<string, unknown>) => api<T>(path, { method: 'GET', query });
api.post = <T = any>(path: string, body?: unknown, query?: Record<string, unknown>) => api<T>(path, { method: 'POST', body, query });
api.put = <T = any>(path: string, body?: unknown, query?: Record<string, unknown>) => api<T>(path, { method: 'PUT', body, query });
api.delete = <T = any>(path: string, query?: Record<string, unknown>) => api<T>(path, { method: 'DELETE', query });

export async function login(email: string, password: string) {
  const res = await raw('/auth/login', 'POST', { email, password });
  const data = await res.json().catch(() => null);
  if (!res.ok) throw new ApiError(res.status, data?.code ?? 'ERROR', res.status === 429 ? 'Слишком много попыток, подождите.' : data?.detail ?? 'Ошибка входа', data?.errors);
  applySession(data as Session);
  return data as Session;
}

export async function logout() {
  const rt = localStorage.getItem('tf_refresh');
  if (rt) { try { await raw('/auth/logout', 'POST', { refreshToken: rt }); } catch { /* ignore */ } }
  applySession(null);
}

export const fieldError = (e: unknown, field: string): string | undefined =>
  e instanceof ApiError ? e.errors?.find((x) => x.field === field)?.message : undefined;
