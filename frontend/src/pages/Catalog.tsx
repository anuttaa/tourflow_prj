import { FormEvent, useState } from 'react';
import { api, fieldError } from '../api';
import { money, TYPE_LABEL, utcToLocal, zonedToUtc } from '../format';
import { Alert, ErrorBox, Field, Modal, Pager, Page, useLoad } from '../components/ui';

interface Dest { id: number; name: string; country: string; tz: string; lat?: number; lon?: number }
interface Sup { id: number; name: string; contact?: string; active: boolean }
interface Offer { id: number; supplierId: number; destinationId: number; type: string; title: string; description?: string; classLevel: number; basePrice: number; capacity: number; startUtc?: string; endUtc?: string; tz: string;
  placeFrom?: string; placeTo?: string; fromLat?: number; fromLon?: number; toLat?: number; toLon?: number; international: boolean; checkInFrom?: number; checkOutUntil?: number; ratingAvg: number }

const hm = (m?: number | null) => (m === undefined || m === null ? '' : `${String(Math.floor(m / 60)).padStart(2, '0')}:${String(m % 60).padStart(2, '0')}`);
const mins = (s: string) => (s ? Number(s.slice(0, 2)) * 60 + Number(s.slice(3, 5)) : undefined);
const num = (s: string | number | undefined) => (s === '' || s === undefined ? undefined : Number(s));

export default function Catalog() {
  const [tab, setTab] = useState<'offers' | 'dest' | 'sup'>('offers');
  return (
    <Page title="Каталог" lead="Направления, поставщики, опции и календарь квот. Изменения сразу влияют на валидацию туров и автоподбор.">
      <div className="tabs" role="tablist">
        {([['offers', 'Опции'], ['dest', 'Направления'], ['sup', 'Поставщики']] as const).map(([k, l]) => <button key={k} role="tab" className={tab === k ? 'on' : ''} onClick={() => setTab(k)}>{l}</button>)}
      </div>
      {tab === 'offers' && <Offers />}
      {tab === 'dest' && <Dests />}
      {tab === 'sup' && <Sups />}
    </Page>
  );
}

function Offers() {
  const dests = useLoad(() => api<Dest[]>('/catalog/destinations'), []);
  const sups = useLoad(() => api<Sup[]>('/catalog/suppliers'), []);
  const [flt, setFlt] = useState({ q: '', type: '', destinationId: '', sort: 'title,asc' });
  const [page, setPage] = useState(0);
  const list = useLoad(() => api<{ content: Offer[]; totalElements: number; size: number }>('/catalog/offers', { query: { ...flt, page, size: 12 } }), [flt, page]);
  const [edit, setEdit] = useState<Offer | 'new' | null>(null);
  const [quota, setQuota] = useState<Offer | null>(null);

  return (
    <>
      <div className="filters">
        <label className="field">Поиск<input value={flt.q} onChange={(e) => { setPage(0); setFlt({ ...flt, q: e.target.value }); }} /></label>
        <label className="field">Тип<select value={flt.type} onChange={(e) => { setPage(0); setFlt({ ...flt, type: e.target.value }); }}><option value="">Все</option>{Object.entries(TYPE_LABEL).map(([k, v]) => <option key={k} value={k}>{v}</option>)}</select></label>
        <label className="field">Направление<select value={flt.destinationId} onChange={(e) => { setPage(0); setFlt({ ...flt, destinationId: e.target.value }); }}><option value="">Все</option>{dests.data?.map((d) => <option key={d.id} value={d.id}>{d.name}</option>)}</select></label>
        <label className="field">Сортировка<select value={flt.sort} onChange={(e) => setFlt({ ...flt, sort: e.target.value })}><option value="title,asc">По названию</option><option value="basePrice,asc">Цена ↑</option><option value="basePrice,desc">Цена ↓</option><option value="ratingAvg,desc">Рейтинг ↓</option><option value="startUtc,asc">По времени</option></select></label>
        <button onClick={() => setEdit('new')}>Новая опция</button>
      </div>
      <ErrorBox error={list.error} />
      <div className="card table-wrap">
        <table><thead><tr><th>Название</th><th>Тип</th><th>Цена</th><th>Класс</th><th>Рейтинг</th><th>Вмест.</th><th /></tr></thead>
          <tbody>{list.data?.content.map((o) => (
            <tr key={o.id}><td>{o.title}</td><td>{TYPE_LABEL[o.type]}</td><td>{money(o.basePrice)}</td><td>{o.classLevel}★</td><td>{Number(o.ratingAvg).toFixed(1)}</td><td>{o.capacity}</td>
              <td className="row"><button className="ghost small" onClick={() => setEdit(o)}>Изменить</button><button className="ghost small" onClick={() => setQuota(o)}>Квоты</button>
                <button className="ghost small" onClick={async () => { if (confirm('Скрыть опцию из каталога?')) { await api(`/catalog/offers/${o.id}`, { method: 'DELETE' }); list.reload(); } }}>Скрыть</button></td></tr>))}</tbody></table>
      </div>
      {list.data && <Pager page={page} total={list.data.totalElements} size={list.data.size} onPage={setPage} />}
      {edit && dests.data && sups.data && <OfferForm offer={edit === 'new' ? null : edit} dests={dests.data} sups={sups.data} onClose={() => setEdit(null)} onSaved={() => { setEdit(null); list.reload(); }} />}
      {quota && <Quota offer={quota} onClose={() => setQuota(null)} />}
    </>
  );
}

function OfferForm({ offer, dests, sups, onClose, onSaved }: { offer: Offer | null; dests: Dest[]; sups: Sup[]; onClose: () => void; onSaved: () => void }) {
  const [f, setF] = useState<any>(() => offer ? { ...offer, startLocal: offer.startUtc ? utcToLocal(offer.startUtc, offer.tz) : '', endLocal: offer.endUtc ? utcToLocal(offer.endUtc, offer.tz) : '',
    checkIn: hm(offer.checkInFrom), checkOut: hm(offer.checkOutUntil), openFrom: '', openTo: '', days: [1, 2, 3, 4, 5, 6, 7], pFrom: '', pTo: '', pK: '' }
    : { supplierId: sups[0]?.id, destinationId: dests[0]?.id, type: 'EXCURSION', title: '', description: '', classLevel: 3, basePrice: 10, capacity: 20, tz: dests[0]?.tz ?? 'Europe/Minsk', startLocal: '', endLocal: '', placeFrom: '', placeTo: '',
      fromLat: '', fromLon: '', toLat: '', toLon: '', international: false, checkIn: '', checkOut: '', openFrom: '', openTo: '', days: [1, 2, 3, 4, 5, 6, 7], pFrom: '', pTo: '', pK: '' });
  const [error, setError] = useState<unknown>(null);
  // расписание и сезонные периоды при редактировании подгружаются отдельно
  useLoad(async () => {
    if (!offer) return null;
    const d = await api<{ schedules: { dayOfWeek: number; openFrom: number; openTo: number }[]; pricePeriods: { dateFrom: string; dateTo: string; coefficient: number }[] }>(`/catalog/offers/${offer.id}`);
    setF((x: any) => ({ ...x, openFrom: d.schedules[0] ? hm(d.schedules[0].openFrom) : '', openTo: d.schedules[0] ? hm(d.schedules[0].openTo) : '', days: d.schedules.length ? d.schedules.map((s) => s.dayOfWeek) : x.days,
      pFrom: d.pricePeriods[0]?.dateFrom ?? '', pTo: d.pricePeriods[0]?.dateTo ?? '', pK: d.pricePeriods[0]?.coefficient ?? '' }));
    return d;
  }, [offer?.id]);
  const set = (k: string) => (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement | HTMLTextAreaElement>) => setF({ ...f, [k]: e.target.value });
  const fixed = ['FLIGHT', 'TRAIN', 'BUS'].includes(f.type);

  async function save(e: FormEvent) {
    e.preventDefault(); setError(null);
    const body = {
      supplierId: Number(f.supplierId), destinationId: Number(f.destinationId), type: f.type, title: f.title, description: f.description, classLevel: Number(f.classLevel), basePrice: Number(f.basePrice), capacity: Number(f.capacity),
      startUtc: f.startLocal ? zonedToUtc(f.startLocal, f.tz) : null, endUtc: f.endLocal ? zonedToUtc(f.endLocal, f.tz) : null, tz: f.tz, placeFrom: f.placeFrom, placeTo: f.placeTo,
      fromLat: num(f.fromLat), fromLon: num(f.fromLon), toLat: num(f.toLat), toLon: num(f.toLon), international: !!f.international, checkInFrom: mins(f.checkIn), checkOutUntil: mins(f.checkOut),
      schedules: f.openFrom && f.openTo ? f.days.map((d: number) => ({ dayOfWeek: d, openFrom: mins(f.openFrom), openTo: mins(f.openTo) })) : [],
      pricePeriods: f.pFrom && f.pTo && f.pK ? [{ dateFrom: f.pFrom, dateTo: f.pTo, coefficient: Number(f.pK) }] : [],
    };
    try { await api(offer ? `/catalog/offers/${offer.id}` : '/catalog/offers', { method: offer ? 'PUT' : 'POST', body }); onSaved(); } catch (err) { setError(err); }
  }

  return (
    <Modal title={offer ? 'Изменить опцию' : 'Новая опция'} onClose={onClose}>
      <form className="stack" onSubmit={save}>
        <ErrorBox error={error} />
        <div className="form-grid">
          <Field label="Тип"><select value={f.type} onChange={set('type')}>{Object.entries(TYPE_LABEL).map(([k, v]) => <option key={k} value={k}>{v}</option>)}</select></Field>
          <Field label="Название" error={fieldError(error, 'title')}><input required maxLength={200} value={f.title} onChange={set('title')} /></Field>
          <Field label="Поставщик"><select value={f.supplierId} onChange={set('supplierId')}>{sups.map((s) => <option key={s.id} value={s.id}>{s.name}</option>)}</select></Field>
          <Field label="Направление"><select value={f.destinationId} onChange={set('destinationId')}>{dests.map((d) => <option key={d.id} value={d.id}>{d.name}</option>)}</select></Field>
          <Field label="Цена за единицу, BYN" error={fieldError(error, 'basePrice')}><input type="number" min={0} step="0.01" required value={f.basePrice} onChange={set('basePrice')} /></Field>
          <Field label="Вместимость" error={fieldError(error, 'capacity')}><input type="number" min={1} required value={f.capacity} onChange={set('capacity')} /></Field>
          <Field label="Класс (1–5)"><input type="number" min={1} max={5} value={f.classLevel} onChange={set('classLevel')} /></Field>
          <Field label="Часовой пояс (IANA)" error={fieldError(error, 'tz')}><input required value={f.tz} onChange={set('tz')} /></Field>
          {fixed && <><Field label="Отправление (местное)"><input type="datetime-local" required value={f.startLocal} onChange={set('startLocal')} /></Field><Field label="Прибытие (в зоне отправления)"><input type="datetime-local" required value={f.endLocal} onChange={set('endLocal')} /></Field></>}
          <Field label="Откуда"><input value={f.placeFrom ?? ''} onChange={set('placeFrom')} /></Field>
          <Field label="Куда"><input value={f.placeTo ?? ''} onChange={set('placeTo')} /></Field>
          {['fromLat', 'fromLon', 'toLat', 'toLon'].map((k) => <Field key={k} label={`Координата ${k}`}><input type="number" step="any" value={f[k] ?? ''} onChange={set(k)} /></Field>)}
          {f.type === 'HOTEL' && <><Field label="Заселение с"><input type="time" value={f.checkIn} onChange={set('checkIn')} /></Field><Field label="Выселение до"><input type="time" value={f.checkOut} onChange={set('checkOut')} /></Field></>}
          <Field label="Работает с"><input type="time" value={f.openFrom} onChange={set('openFrom')} /></Field>
          <Field label="Работает до"><input type="time" value={f.openTo} onChange={set('openTo')} /></Field>
          <Field label="Сезон: с"><input type="date" value={f.pFrom} onChange={set('pFrom')} /></Field>
          <Field label="Сезон: по"><input type="date" value={f.pTo} onChange={set('pTo')} /></Field>
          <Field label="Сезонный коэффициент"><input type="number" step="0.01" min={0.1} value={f.pK} onChange={set('pK')} /></Field>
        </div>
        <div className="row">{[1, 2, 3, 4, 5, 6, 7].map((d) => <label key={d} className="row"><input type="checkbox" checked={f.days.includes(d)} onChange={(e) => setF({ ...f, days: e.target.checked ? [...f.days, d] : f.days.filter((x: number) => x !== d) })} />{['Пн', 'Вт', 'Ср', 'Чт', 'Пт', 'Сб', 'Вс'][d - 1]}</label>)}
          <label className="row"><input type="checkbox" checked={!!f.international} onChange={(e) => setF({ ...f, international: e.target.checked })} />Международный рейс</label></div>
        <Field label="Описание"><textarea rows={2} maxLength={2000} value={f.description ?? ''} onChange={set('description')} /></Field>
        <button>Сохранить</button>
      </form>
    </Modal>
  );
}

function Quota({ offer, onClose }: { offer: Offer; onClose: () => void }) {
  const today = new Date().toISOString().slice(0, 10);
  const plus = (n: number) => new Date(Date.now() + n * 86400000).toISOString().slice(0, 10);
  const [range, setRange] = useState({ from: plus(14), to: plus(35) });
  const [q, setQ] = useState(offer.capacity);
  const [msg, setMsg] = useState('');
  const [err, setErr] = useState<unknown>(null);
  const cal = useLoad(() => api<{ date: string; quotaTotal: number; quotaFree: number }[]>(`/catalog/offers/${offer.id}/availability`, { query: { from: today, to: plus(60) } }), [offer.id]);

  async function apply(quota: number) {
    setErr(null); setMsg('');
    try { const r = await api<{ updatedDays: number }>(`/catalog/offers/${offer.id}/availability`, { method: 'PUT', body: { from: range.from, to: range.to, quota } }); setMsg(`Обновлено дней: ${r.updatedDays}`); cal.reload(); } catch (e) { setErr(e); }
  }
  return (
    <Modal title={`Квоты: ${offer.title}`} onClose={onClose}>
      <div className="stack">
        <ErrorBox error={err} />{msg && <Alert kind="ok">{msg}</Alert>}
        <div className="row">
          <Field label="С"><input type="date" value={range.from} onChange={(e) => setRange({ ...range, from: e.target.value })} /></Field>
          <Field label="По"><input type="date" value={range.to} onChange={(e) => setRange({ ...range, to: e.target.value })} /></Field>
          <Field label="Квота"><input type="number" min={0} value={q} onChange={(e) => setQ(Number(e.target.value))} style={{ width: 90 }} /></Field>
          <button onClick={() => apply(q)}>Задать</button><button className="danger" onClick={() => apply(0)}>Закрыть даты</button>
        </div>
        <h3>Ближайшие 60 дней: свободно / всего</h3>
        <div className="cal">{cal.data?.map((a) => <div key={a.date} className={a.quotaFree === 0 ? 'zero' : a.quotaFree < a.quotaTotal * 0.3 ? 'low' : 'ok'}><b>{a.date.slice(8)}.{a.date.slice(5, 7)}</b><br />{a.quotaFree}/{a.quotaTotal}</div>)}</div>
        {cal.data?.length === 0 && <p className="muted">Квоты на ближайшие даты не заданы.</p>}
      </div>
    </Modal>
  );
}

function Dests() {
  const { data, error, reload } = useLoad(() => api<Dest[]>('/catalog/destinations'), []);
  const [f, setF] = useState({ name: '', country: '', tz: 'Europe/Minsk', lat: '', lon: '' });
  const [err, setErr] = useState<unknown>(null);
  async function add(e: FormEvent) { e.preventDefault(); setErr(null); try { await api('/catalog/destinations', { method: 'POST', body: { ...f, lat: num(f.lat), lon: num(f.lon) } }); setF({ ...f, name: '', country: '' }); reload(); } catch (x) { setErr(x); } }
  async function del(id: number) { if (!confirm('Удалить направление?')) return; setErr(null); try { await api(`/catalog/destinations/${id}`, { method: 'DELETE' }); reload(); } catch (x) { setErr(x); } }
  return (
    <div className="stack">
      <ErrorBox error={error ?? err} />
      <form className="card form-grid" onSubmit={add}>
        <Field label="Название" error={fieldError(err, 'name')}><input required value={f.name} onChange={(e) => setF({ ...f, name: e.target.value })} /></Field>
        <Field label="Страна"><input required value={f.country} onChange={(e) => setF({ ...f, country: e.target.value })} /></Field>
        <Field label="Часовой пояс"><input required value={f.tz} onChange={(e) => setF({ ...f, tz: e.target.value })} /></Field>
        <Field label="Широта"><input type="number" step="any" value={f.lat} onChange={(e) => setF({ ...f, lat: e.target.value })} /></Field>
        <Field label="Долгота"><input type="number" step="any" value={f.lon} onChange={(e) => setF({ ...f, lon: e.target.value })} /></Field>
        <div style={{ alignSelf: 'end' }}><button>Добавить</button></div>
      </form>
      <div className="card table-wrap"><table><thead><tr><th>Название</th><th>Страна</th><th>Пояс</th><th /></tr></thead>
        <tbody>{data?.map((d) => <tr key={d.id}><td>{d.name}</td><td>{d.country}</td><td>{d.tz}</td><td><button className="ghost small" onClick={() => del(d.id)}>Удалить</button></td></tr>)}</tbody></table></div>
    </div>
  );
}

function Sups() {
  const { data, error, reload } = useLoad(() => api<Sup[]>('/catalog/suppliers'), []);
  const [f, setF] = useState({ name: '', contact: '' });
  const [err, setErr] = useState<unknown>(null);
  async function add(e: FormEvent) { e.preventDefault(); setErr(null); try { await api('/catalog/suppliers', { method: 'POST', body: { ...f, active: true } }); setF({ name: '', contact: '' }); reload(); } catch (x) { setErr(x); } }
  async function del(id: number) { if (!confirm('Удалить поставщика?')) return; setErr(null); try { await api(`/catalog/suppliers/${id}`, { method: 'DELETE' }); reload(); } catch (x) { setErr(x); } }
  return (
    <div className="stack">
      <ErrorBox error={error ?? err} />
      <form className="card form-grid" onSubmit={add}>
        <Field label="Название" error={fieldError(err, 'name')}><input required value={f.name} onChange={(e) => setF({ ...f, name: e.target.value })} /></Field>
        <Field label="Контакт"><input value={f.contact} onChange={(e) => setF({ ...f, contact: e.target.value })} /></Field>
        <div style={{ alignSelf: 'end' }}><button>Добавить</button></div>
      </form>
      <div className="card table-wrap"><table><thead><tr><th>Название</th><th>Контакт</th><th /></tr></thead>
        <tbody>{data?.map((s) => <tr key={s.id}><td>{s.name}</td><td>{s.contact}</td><td><button className="ghost small" onClick={() => del(s.id)}>Удалить</button></td></tr>)}</tbody></table></div>
    </div>
  );
}
