import { FormEvent, useEffect, useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { api, fieldError } from '../api';
import { useAuth } from '../auth';
import { Alert, ErrorBox, Field, Page } from '../components/ui';

export function Login() {
  const { login } = useAuth();
  const nav = useNavigate();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<unknown>(null);
  const [busy, setBusy] = useState(false);

  async function submit(e: FormEvent) {
    e.preventDefault();
    setBusy(true); setError(null);
    try { await login(email, password); nav('/'); } catch (err) { setError(err); } finally { setBusy(false); }
  }
  return (
    <Page title="Вход">
      <form className="card stack" style={{ maxWidth: 420 }} onSubmit={submit}>
        <ErrorBox error={error} />
        <Field label="Email"><input type="email" required value={email} onChange={(e) => setEmail(e.target.value)} autoComplete="username" /></Field>
        <Field label="Пароль"><input type="password" required value={password} onChange={(e) => setPassword(e.target.value)} autoComplete="current-password" /></Field>
        <button disabled={busy}>Войти</button>
        <p className="muted">Нет аккаунта? <Link to="/register">Зарегистрируйтесь</Link></p>
      </form>
    </Page>
  );
}

export function Register() {
  const [f, setF] = useState({ email: '', password: '', firstName: '', lastName: '' });
  const [error, setError] = useState<unknown>(null);
  const [done, setDone] = useState(false);
  const [busy, setBusy] = useState(false);
  const set = (k: keyof typeof f) => (e: React.ChangeEvent<HTMLInputElement>) => setF({ ...f, [k]: e.target.value });

  async function submit(e: FormEvent) {
    e.preventDefault();
    setBusy(true); setError(null);
    try { await api('/auth/register', { method: 'POST', body: f }); setDone(true); } catch (err) { setError(err); } finally { setBusy(false); }
  }
  if (done) return <Page title="Проверьте почту"><Alert kind="ok">Мы отправили письмо со ссылкой подтверждения на {f.email}. В демо-окружении письма видны в MailHog (порт 8025).</Alert></Page>;
  return (
    <Page title="Регистрация туриста">
      <form className="card stack" style={{ maxWidth: 460 }} onSubmit={submit}>
        <ErrorBox error={error} />
        <div className="form-grid">
          <Field label="Имя" error={fieldError(error, 'firstName')}><input value={f.firstName} onChange={set('firstName')} maxLength={100} /></Field>
          <Field label="Фамилия" error={fieldError(error, 'lastName')}><input value={f.lastName} onChange={set('lastName')} maxLength={100} /></Field>
        </div>
        <Field label="Email" error={fieldError(error, 'email')}><input type="email" required value={f.email} onChange={set('email')} /></Field>
        <Field label="Пароль (от 8 символов, буква и цифра)" error={fieldError(error, 'password')}><input type="password" required minLength={8} value={f.password} onChange={set('password')} autoComplete="new-password" /></Field>
        <button disabled={busy}>Зарегистрироваться</button>
      </form>
    </Page>
  );
}

export function Confirm() {
  const [q] = useSearchParams();
  const [state, setState] = useState<'wait' | 'ok' | 'fail'>('wait');
  const [error, setError] = useState<unknown>(null);
  useEffect(() => {
    const t = q.get('token');
    if (!t) { setState('fail'); return; }
    api('/auth/confirm', { method: 'POST', body: { token: t } }).then(() => setState('ok')).catch((e) => { setError(e); setState('fail'); });
  }, [q]);
  return (
    <Page title="Подтверждение email">
      {state === 'wait' && <p className="muted">Проверяем ссылку…</p>}
      {state === 'ok' && <Alert kind="ok">Email подтверждён. <Link to="/login">Войти</Link></Alert>}
      {state === 'fail' && (error ? <ErrorBox error={error} /> : <Alert kind="error">Ссылка недействительна.</Alert>)}
    </Page>
  );
}
