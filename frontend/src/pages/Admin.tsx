import { useState } from 'react';
import { api } from '../api';
import { fmtDateTime, STATUS_LABEL } from '../format';
import { Alert, Badge, ErrorBox, Pager, Page, useLoad } from '../components/ui';

interface U { id: string; email: string; status: string; roles: string[]; firstName?: string; lastName?: string }
interface Rule { id: number; code: string; paramName: string; value: number; severity: string; description?: string; active: boolean }
const ROLES = ['CLIENT', 'TOUR_MANAGER', 'ADMIN'];
const ROLE_LABEL: Record<string, string> = { CLIENT: 'Турист', TOUR_MANAGER: 'Менеджер', ADMIN: 'Администратор' };

export default function Admin() {
  const [tab, setTab] = useState<'users' | 'rules' | 'audit'>('users');
  return (
    <Page title="Администрирование">
      <div className="tabs" role="tablist">{([['users', 'Пользователи'], ['rules', 'Правила валидации'], ['audit', 'Журнал аудита']] as const).map(([k, l]) => <button key={k} role="tab" className={tab === k ? 'on' : ''} onClick={() => setTab(k)}>{l}</button>)}</div>
      {tab === 'users' && <Users />}{tab === 'rules' && <Rules />}{tab === 'audit' && <Audit />}
    </Page>
  );
}

function Users() {
  const [q, setQ] = useState('');
  const [applied, setApplied] = useState('');
  const [page, setPage] = useState(0);
  const { data, error, reload } = useLoad(() => api<{ content: U[]; totalElements: number; size: number }>('/users', { query: { q: applied, page, size: 15 } }), [applied, page]);
  const [err, setErr] = useState<unknown>(null);

  async function block(u: U) { setErr(null); try { await api(`/users/${u.id}/block`, { method: 'PATCH', body: { blocked: u.status !== 'BLOCKED' } }); reload(); } catch (e) { setErr(e); } }
  async function role(u: U, r: string) { setErr(null); try { await api(`/users/${u.id}/roles`, { method: 'PUT', body: { roles: [r] } }); reload(); } catch (e) { setErr(e); } }
  return (
    <>
      <form className="filters" onSubmit={(e) => { e.preventDefault(); setPage(0); setApplied(q); }}><label className="field">Поиск по email<input value={q} onChange={(e) => setQ(e.target.value)} /></label><button>Найти</button></form>
      <ErrorBox error={error ?? err} />
      <div className="card table-wrap"><table><thead><tr><th>Email</th><th>Имя</th><th>Роль</th><th>Статус</th><th /></tr></thead>
        <tbody>{data?.content.map((u) => (
          <tr key={u.id}><td>{u.email}</td><td>{u.firstName} {u.lastName}</td>
            <td><select aria-label={`Роль ${u.email}`} value={u.roles[0]} onChange={(e) => role(u, e.target.value)}>{ROLES.map((r) => <option key={r} value={r}>{ROLE_LABEL[r]}</option>)}</select></td>
            <td><Badge s={u.status} /></td><td><button className={u.status === 'BLOCKED' ? 'ghost small' : 'danger small'} onClick={() => block(u)}>{u.status === 'BLOCKED' ? 'Разблокировать' : 'Заблокировать'}</button></td></tr>))}</tbody></table></div>
      {data && <Pager page={page} total={data.totalElements} size={data.size} onPage={setPage} />}
      <p className="muted">Статусы: {Object.entries(STATUS_LABEL).filter(([k]) => ['ACTIVE', 'PENDING', 'BLOCKED'].includes(k)).map(([, v]) => v).join(', ')}. Смена роли завершает активные сессии пользователя.</p>
    </>
  );
}

function Rules() {
  const { data, error, reload } = useLoad(() => api<Rule[]>('/rules'), []);
  const [err, setErr] = useState<unknown>(null);
  const [ok, setOk] = useState('');
  async function save(r: Rule, value: number, active: boolean) {
    setErr(null); setOk('');
    try { await api(`/rules/${r.id}`, { method: 'PUT', body: { value, active } }); setOk(`Сохранено: ${r.paramName}`); reload(); } catch (e) { setErr(e); }
  }
  return (
    <>
      <ErrorBox error={error ?? err} />{ok && <Alert kind="ok">{ok}</Alert>}
      <p className="muted">Параметры применяются при следующей валидации без перекомпиляции. Отключённый параметр заменяется значением по умолчанию.</p>
      <div className="card table-wrap"><table><thead><tr><th>Правило</th><th>Параметр</th><th>Описание</th><th>Значение</th><th>Активно</th><th /></tr></thead>
        <tbody>{data?.map((r) => <RuleRow key={r.id} r={r} onSave={save} />)}</tbody></table></div>
    </>
  );
}

function RuleRow({ r, onSave }: { r: Rule; onSave: (r: Rule, v: number, a: boolean) => void }) {
  const [v, setV] = useState(String(r.value));
  const [a, setA] = useState(r.active);
  return (
    <tr><td><b>{r.code}</b></td><td>{r.paramName}</td><td>{r.description}</td>
      <td><input aria-label={`Значение ${r.paramName}`} type="number" step="any" min={0} value={v} onChange={(e) => setV(e.target.value)} style={{ width: 90 }} /></td>
      <td><input type="checkbox" aria-label={`Активно ${r.paramName}`} checked={a} onChange={(e) => setA(e.target.checked)} /></td>
      <td><button className="small" onClick={() => onSave(r, Number(v), a)}>Сохранить</button></td></tr>
  );
}

function Audit() {
  const [page, setPage] = useState(0);
  const { data, error } = useLoad(() => api<{ content: { id: number; actor?: string; action: string; target?: string; details?: string; createdAt: string }[]; totalElements: number; size: number }>('/audit', { query: { page, size: 30 } }), [page]);
  const tz = Intl.DateTimeFormat().resolvedOptions().timeZone;
  return (
    <>
      <ErrorBox error={error} />
      <div className="card table-wrap"><table><thead><tr><th>Время</th><th>Действие</th><th>Объект</th><th>Детали</th></tr></thead>
        <tbody>{data?.content.map((a) => <tr key={a.id}><td>{fmtDateTime(a.createdAt, tz)}</td><td>{a.action}</td><td>{a.target}</td><td>{a.details}</td></tr>)}</tbody></table></div>
      {data && <Pager page={page} total={data.totalElements} size={data.size} onPage={setPage} />}
    </>
  );
}
