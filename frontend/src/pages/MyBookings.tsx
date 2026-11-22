import { useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../api';
import { fmtDateTime, fmtIsoDate, money, TYPE_LABEL } from '../format';
import { Alert, Badge, ErrorBox, Pager, Page, useLoad } from '../components/ui';

interface Booking { id: number; ref: string; tourTitle: string; destinationName: string; startDate: string; endDate: string; pax: number; status: string; totalPrice: number; penalty: number; holdExpiresAt?: string }
interface View { booking: Booking; items: { id: number; type: string; title: string; lineTotal: number }[]; history: { fromStatus?: string; toStatus: string; reason?: string; createdAt: string }[]; payments: { id: number; kind: string; amount: number; status: string }[] }

export default function MyBookings() {
  const [page, setPage] = useState(0);
  const { data, error, loading, reload } = useLoad(() => api<{ content: Booking[]; totalElements: number; size: number }>('/bookings/my', { query: { page, size: 10 } }), [page]);
  const [open, setOpen] = useState<number | null>(null);
  const [view, setView] = useState<View | null>(null);
  const [actErr, setActErr] = useState<unknown>(null);

  async function toggle(id: number) {
    if (open === id) { setOpen(null); return; }
    setOpen(id); setView(null);
    try { setView(await api<View>(`/bookings/${id}`)); } catch (e) { setActErr(e); }
  }
  async function act(id: number, what: 'pay' | 'cancel') {
    if (what === 'cancel' && !confirm('Отменить заявку? Для оплаченных заявок может удерживаться штраф по условиям отмены.')) return;
    setActErr(null);
    try { setView(await api<View>(`/bookings/${id}/${what}`, { method: 'POST', body: what === 'cancel' ? {} : undefined })); reload(); } catch (e) { setActErr(e); }
  }

  return (
    <Page title="Мои заявки">
      <ErrorBox error={error ?? actErr} />
      {loading && <p className="muted">Загрузка…</p>}
      {data?.content.length === 0 && <p className="card muted">Заявок пока нет. Выберите тур на витрине.</p>}
      <div className="stack">
        {data?.content.map((b) => (
          <article key={b.id} className="card">
            <div className="row between">
              <div><h3 style={{ margin: 0 }}>{b.tourTitle}</h3><span className="muted">{b.ref} · {fmtIsoDate(b.startDate)} — {fmtIsoDate(b.endDate)} · {b.pax} чел.</span></div>
              <div className="row">
                <Badge s={b.status} />
                <b>{money(b.totalPrice)}</b>
                <Link to={`/bookings/${b.id}`} className="btn small">
                  {b.status === 'PAID' ? '🎫 Ваучер' : 'Путевка'}
                </Link>
                <button className="ghost small" onClick={() => toggle(b.id)}>{open === b.id ? 'Скрыть' : 'Детали'}</button>
              </div>
            </div>
            {b.status === 'PENDING' && b.holdExpiresAt && <p className="muted">Места удержаны до {fmtDateTime(b.holdExpiresAt, Intl.DateTimeFormat().resolvedOptions().timeZone)}, затем заявка закроется автоматически.</p>}
            {open === b.id && view && (
              <div className="stack" style={{ marginTop: '.75rem' }}>
                <div className="table-wrap"><table><thead><tr><th>Услуга</th><th>Тип</th><th>Сумма (без наценки)</th></tr></thead>
                  <tbody>{view.items.map((i) => <tr key={i.id}><td>{i.title}</td><td>{TYPE_LABEL[i.type] ?? i.type}</td><td>{money(i.lineTotal)}</td></tr>)}</tbody></table></div>
                <h3>История</h3>
                <ul>{view.history.map((h, i) => <li key={i}>{fmtDateTime(h.createdAt, Intl.DateTimeFormat().resolvedOptions().timeZone)}: {h.fromStatus ? `${h.fromStatus} → ` : ''}{h.toStatus}{h.reason ? ` — ${h.reason}` : ''}</li>)}</ul>
                {view.payments.length > 0 && <ul>{view.payments.map((p) => <li key={p.id}>{p.kind === 'PAYMENT' ? 'Платёж' : 'Возврат'}: {money(p.amount)} ({p.status})</li>)}</ul>}
                {Number(view.booking.penalty) > 0 && <p className="muted">Штраф за отмену: {money(view.booking.penalty)}</p>}
                {view.booking.status === 'PAID' && (
                  <Alert kind="ok">
                    🎉 Путевка оплачена! Электронный ваучер сформирован и активен.{' '}
                    <Link to={`/bookings/${b.id}`} style={{ fontWeight: 600 }}>Перейти на страницу путевки →</Link>
                  </Alert>
                )}
                <div className="row">
                  <Link to={`/bookings/${b.id}`} className="btn small">
                    🎫 Электронная путевка / Ваучер
                  </Link>
                  {view.booking.status === 'CONFIRMED' && <button onClick={() => act(b.id, 'pay')}>Оплатить (sandbox)</button>}
                  {['PENDING', 'CONFIRMED', 'PAID'].includes(view.booking.status) && <button className="danger small" onClick={() => act(b.id, 'cancel')}>Отменить заявку</button>}
                </div>
              </div>
            )}
          </article>
        ))}
      </div>
      {data && <Pager page={page} total={data.totalElements} size={data.size} onPage={setPage} />}
    </Page>
  );
}
