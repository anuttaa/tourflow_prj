import { useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../api';
import { fmtDateTime, fmtIsoDate, money, STATUS_LABEL, TYPE_LABEL } from '../format';
import { Badge, ErrorBox, Modal, Pager, Page, useLoad } from '../components/ui';

interface Booking { id: number; ref: string; userEmail: string; tourTitle: string; startDate: string; endDate: string; pax: number; status: string; totalPrice: number; holdExpiresAt?: string; comment?: string }
interface View { booking: Booking; items: { id: number; type: string; title: string; lineTotal: number }[]; travelers: { id: number; fullName: string }[]; history: { fromStatus?: string; toStatus: string; reason?: string; createdAt: string }[]; payments: { id: number; kind: string; amount: number }[] }

export default function Queue() {
  const [status, setStatus] = useState('PENDING');
  const [page, setPage] = useState(0);
  const { data, error, loading, reload } = useLoad(() => api<{ content: Booking[]; totalElements: number; size: number }>('/bookings', { query: { status, page, size: 15 } }), [status, page]);
  const [view, setView] = useState<View | null>(null);
  const [text, setText] = useState('');
  const [err, setErr] = useState<unknown>(null);
  const tz = Intl.DateTimeFormat().resolvedOptions().timeZone;

  async function open(id: number) { setErr(null); setText(''); try { setView(await api<View>(`/bookings/${id}`)); } catch (e) { setErr(e); } }
  async function act(what: 'confirm' | 'reject' | 'request-changes' | 'cancel') {
    if (!view) return;
    setErr(null);
    try {
      const v = await api<View>(`/bookings/${view.booking.id}/${what}`, { method: 'POST', body: what === 'confirm' ? undefined : { reason: text } });
      setView(v); setText(''); reload();
    } catch (e) { setErr(e); reload(); }
  }

  return (
    <Page title="Очередь заявок" lead="Подтверждение превращает удержанные места в резерв. Просроченные удержания закрываются автоматически.">
      <div className="filters"><label className="field">Статус<select value={status} onChange={(e) => { setStatus(e.target.value); setPage(0); }}><option value="">Все</option>{['PENDING', 'CONFIRMED', 'PAID', 'COMPLETED', 'REJECTED', 'EXPIRED', 'CANCELLED'].map((s) => <option key={s} value={s}>{STATUS_LABEL[s]}</option>)}</select></label></div>
      <ErrorBox error={error} />
      {loading && <p className="muted">Загрузка…</p>}
      <div className="card table-wrap">
        <table><thead><tr><th>Заявка</th><th>Тур</th><th>Клиент</th><th>Даты</th><th>Чел.</th><th>Сумма</th><th>Статус</th><th /></tr></thead>
          <tbody>{data?.content.map((b) => (
            <tr key={b.id}><td>{b.ref}</td><td>{b.tourTitle}</td><td>{b.userEmail}</td><td>{fmtIsoDate(b.startDate)}</td><td>{b.pax}</td><td>{money(b.totalPrice)}</td><td><Badge s={b.status} /></td>
              <td><button className="ghost small" onClick={() => open(b.id)}>Открыть</button></td></tr>))}
            {data?.content.length === 0 && <tr><td colSpan={8} className="muted">Заявок нет</td></tr>}</tbody></table>
      </div>
      {data && <Pager page={page} total={data.totalElements} size={data.size} onPage={setPage} />}
      {view && (
        <Modal title={`Заявка ${view.booking.ref}`} onClose={() => setView(null)}>
          <div className="stack">
            <ErrorBox error={err} />
            <div className="row between">
              <div className="row">
                <Badge s={view.booking.status} />
                <b>{money(view.booking.totalPrice)}</b>
                <span className="muted">{view.booking.tourTitle} · {view.booking.pax} чел. · {view.booking.userEmail}</span>
              </div>
              <Link to={`/bookings/${view.booking.id}`} className="btn small ghost" target="_blank">
                Страница путевки ↗
              </Link>
            </div>
            {view.booking.holdExpiresAt && <p className="muted">Удержание до {fmtDateTime(view.booking.holdExpiresAt, tz)}</p>}
            {view.booking.comment && <p>Комментарий клиента: {view.booking.comment}</p>}
            <h3>Участники</h3><ul>{view.travelers.map((t) => <li key={t.id}>{t.fullName}</li>)}</ul>
            <h3>Состав</h3><ul>{view.items.map((i) => <li key={i.id}>{TYPE_LABEL[i.type]}: {i.title} — {money(i.lineTotal)}</li>)}</ul>
            <h3>История</h3><ul>{view.history.map((h, i) => <li key={i}>{fmtDateTime(h.createdAt, tz)}: {h.fromStatus ? `${h.fromStatus} → ` : ''}{h.toStatus}{h.reason ? ` — ${h.reason}` : ''}</li>)}</ul>
            {view.booking.status === 'PENDING' && (
              <>
                <textarea rows={2} placeholder="Причина отказа или описание нужных изменений" maxLength={500} value={text} onChange={(e) => setText(e.target.value)} />
                <div className="row"><button onClick={() => act('confirm')}>Подтвердить</button><button className="ghost" onClick={() => act('request-changes')}>Запросить изменения</button><button className="danger" onClick={() => act('reject')}>Отклонить</button></div>
              </>
            )}
            {['CONFIRMED', 'PAID'].includes(view.booking.status) && <div><button className="danger" onClick={() => confirm('Отменить заявку?') && act('cancel')}>Отменить заявку</button></div>}
          </div>
        </Modal>
      )}
    </Page>
  );
}
