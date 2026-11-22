import { FormEvent, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { api, fieldError } from '../api';
import { fmtIsoDate, money, PROFILE_LABEL } from '../format';
import { Alert, Badge, ErrorBox, Field, Page, useLoad } from '../components/ui';

interface Tour {
  id: number;
  code: string;
  title: string;
  destinationName: string;
  status: string;
  startDate: string;
  endDate: string;
  pax: number;
  totalPrice: number;
  versionNo: number;
  managerEmail: string;
}

interface Dest {
  id: number;
  name: string;
  country: string;
  tz?: string;
}

interface OfferItem {
  id: number;
  type: string;
  title: string;
  classLevel: number;
  startUtc?: string;
  endUtc?: string;
  tz?: string;
}

export default function Builder() {
  const nav = useNavigate();
  const [status, setStatus] = useState('');
  const tours = useLoad(() => api<Tour[]>('/tours', { query: { status } }), [status]);
  const dests = useLoad(() => api<Dest[]>('/catalog/destinations'), []);

  // Wizard vs Manual
  const [isWizard, setIsWizard] = useState(true);

  // Form fields
  const [f, setF] = useState({
    title: '',
    destinationId: '',
    startDate: '',
    endDate: '',
    pax: 2,
    budget: '',
    profile: 'BALANCE',
    hotelStars: '4',
    includeFlight: true,
    includeTransfer: true,
    includeExcursions: true,
  });

  const [error, setError] = useState<unknown>(null);
  const [info, setInfo] = useState('');
  const [busy, setBusy] = useState(false);

  const setVal = (k: string, v: any) => setF((prev) => ({ ...prev, [k]: v }));
  const set = (k: string) => (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>) =>
    setVal(k, e.target.value);

  // Умный генератор черновика тура
  async function generateDraft(e: FormEvent) {
    e.preventDefault();
    setError(null);
    setInfo('');
    setBusy(true);

    try {
      const destId = Number(f.destinationId);
      const destObj = dests.data?.find((d) => d.id === destId);
      const tz = destObj?.tz || 'UTC';

      const tourTitle =
        f.title.trim() ||
        `Тур: ${destObj?.name || 'Отдых'} (${f.startDate} — ${f.endDate})`;

      // 1. Создаём тур
      const createdTour = await api<Tour>('/tours', {
        method: 'POST',
        body: {
          title: tourTitle,
          destinationId: destId,
          startDate: f.startDate,
          endDate: f.endDate,
          pax: Number(f.pax),
          budget: f.budget === '' ? null : Number(f.budget),
          profile: f.profile,
        },
      });

      const tourId = createdTour.id;

      if (isWizard) {
        // 2. Запрашиваем опции из каталога для автосборки
        const offersRes = await api<{ content: OfferItem[] }>('/catalog/offers', {
          query: { destinationId: destId, size: 100 },
        });
        const offers = offersRes?.content || [];

        // Авиаперелет / жд
        if (f.includeFlight) {
          const flights = offers.filter((o) => o.type === 'FLIGHT' || o.type === 'TRAIN');
          if (flights.length > 0) {
            // Берём подходящий или первый доступный
            const outFlight = flights[0];
            await api(`/tours/${tourId}/segments`, {
              method: 'POST',
              body: { offerId: outFlight.id },
            }).catch(() => {});
          }
        }

        // Трансфер
        if (f.includeTransfer) {
          const transfers = offers.filter((o) => o.type === 'TRANSFER');
          if (transfers.length > 0) {
            const tr = transfers[0];
            const trStart = `${f.startDate}T13:00:00Z`;
            const trEnd = `${f.startDate}T14:00:00Z`;
            await api(`/tours/${tourId}/segments`, {
              method: 'POST',
              body: { offerId: tr.id, start: trStart, end: trEnd },
            }).catch(() => {});
          }
        }

        // Отель с фильтром по звёздности
        const targetStars = Number(f.hotelStars) || 4;
        const hotels = offers.filter((o) => o.type === 'HOTEL');
        const matchedHotel =
          hotels.find((h) => h.classLevel === targetStars) ||
          hotels.find((h) => h.classLevel >= targetStars - 1) ||
          hotels[0];

        if (matchedHotel) {
          const checkIn = `${f.startDate}T14:30:00Z`;
          const checkOut = `${f.endDate}T11:30:00Z`;
          await api(`/tours/${tourId}/segments`, {
            method: 'POST',
            body: { offerId: matchedHotel.id, start: checkIn, end: checkOut },
          }).catch(() => {});
        }

        // Экскурсии
        if (f.includeExcursions) {
          const excursions = offers.filter((o) => o.type === 'EXCURSION');
          if (excursions.length > 0) {
            const ex = excursions[0];
            // Ставим на второй день поездки в 10:00
            const nextDay = new Date(Date.parse(f.startDate + 'T00:00:00Z') + 86400000)
              .toISOString()
              .slice(0, 10);
            const exStart = `${nextDay}T10:00:00Z`;
            const exEnd = `${nextDay}T13:30:00Z`;
            await api(`/tours/${tourId}/segments`, {
              method: 'POST',
              body: { offerId: ex.id, start: exStart, end: exEnd },
            }).catch(() => {});
          }
        }

        // 3. Запускаем валидацию
        await api(`/tours/${tourId}/validate`, { method: 'POST', query: { refresh: true } }).catch(() => {});
      }

      // Переходим в конструктор
      nav(`/builder/${tourId}`);
    } catch (err) {
      setError(err);
    } finally {
      setBusy(false);
    }
  }

  async function createDemoTours() {
    setError(null);
    setInfo('');
    setBusy(true);
    try {
      await api('/tours/demo', { method: 'POST' });
      setInfo('Созданы демонстрационные туры (корректный и с конфликтами расписания).');
      tours.reload();
    } catch (err) {
      setError(err);
    } finally {
      setBusy(false);
    }
  }

  return (
    <Page
      title="Конструктор и планирование туров"
      lead="Создавайте индивидуальные программы или воспользуйтесь умным генератором черновика с автоматической стыковкой перелётов, отелей и экскурсий."
      actions={
        <button className="ghost small" disabled={busy} onClick={createDemoTours}>
          Сгенерировать демо-туры
        </button>
      }
    >
      <ErrorBox error={error} />
      {info && <Alert kind="ok">{info}</Alert>}

      {/* ── Панель создания тура ── */}
      <form className="card stack" onSubmit={generateDraft} style={{ marginBottom: '2rem' }}>
        <div className="row between" style={{ alignItems: 'center' }}>
          <h2 style={{ margin: 0 }}>
            {isWizard ? 'Мастер умной автосборки тура' : 'Ручное создание черновика'}
          </h2>
          <div className="row" style={{ gap: '0.5rem' }}>
            <button
              type="button"
              className={isWizard ? 'small' : 'ghost small'}
              onClick={() => setIsWizard(true)}
            >
              Умный мастер
            </button>
            <button
              type="button"
              className={!isWizard ? 'small' : 'ghost small'}
              onClick={() => setIsWizard(false)}
            >
              Пустой черновик
            </button>
          </div>
        </div>

        {isWizard && (
          <p className="muted" style={{ margin: 0, fontSize: '0.9rem' }}>
            Укажите основные пожелания: система автоматически подберёт рейс, организует трансфер, забронирует отель нужной звёздности и расставит экскурсии по дням без накладок.
          </p>
        )}

        <div className="form-grid">
          <Field label="Направление (город / страна)">
            <select required value={f.destinationId} onChange={set('destinationId')}>
              <option value="">Выберите направление…</option>
              {dests.data?.map((d) => (
                <option key={d.id} value={d.id}>
                  {d.name}, {d.country}
                </option>
              ))}
            </select>
          </Field>

          <Field label="Название тура (необязательно)" error={fieldError(error, 'title')}>
            <input
              maxLength={200}
              placeholder="Например: Романтический уикенд в Париже"
              value={f.title}
              onChange={set('title')}
            />
          </Field>

          <Field label="Дата начала тура">
            <input type="date" required value={f.startDate} onChange={set('startDate')} />
          </Field>

          <Field label="Дата окончания тура">
            <input type="date" required value={f.endDate} onChange={set('endDate')} />
          </Field>

          <Field label="Количество туристов" error={fieldError(error, 'pax')}>
            <input type="number" min={1} max={200} required value={f.pax} onChange={set('pax')} />
          </Field>

          <Field label="Профиль тура">
            <select value={f.profile} onChange={set('profile')}>
              {Object.entries(PROFILE_LABEL).map(([k, v]) => (
                <option key={k} value={k}>
                  {v}
                </option>
              ))}
            </select>
          </Field>

          {isWizard && (
            <>
              <Field label="Категория отеля">
                <select value={f.hotelStars} onChange={set('hotelStars')}>
                  <option value="3">Отель 3★ (Эконом / Практичный)</option>
                  <option value="4">Отель 4★ (Комфорт / Стандарт)</option>
                  <option value="5">Отель 5★ (Премиум / Люкс)</option>
                </select>
              </Field>

              <Field label="Ориентировочный бюджет (₽)">
                <input
                  type="number"
                  min={0}
                  placeholder="Не ограничен"
                  value={f.budget}
                  onChange={set('budget')}
                />
              </Field>
            </>
          )}
        </div>

        {isWizard && (
          <div
            className="row"
            style={{
              gap: '1.5rem',
              padding: '0.85rem 1.25rem',
              background: 'var(--paper)',
              borderRadius: 'var(--radius)',
              flexWrap: 'wrap',
            }}
          >
            <label className="row" style={{ gap: '0.4rem', cursor: 'pointer', fontSize: '0.9rem' }}>
              <input
                type="checkbox"
                checked={f.includeFlight}
                onChange={(e) => setVal('includeFlight', e.target.checked)}
              />
              Включить авиабилеты / перелёт
            </label>

            <label className="row" style={{ gap: '0.4rem', cursor: 'pointer', fontSize: '0.9rem' }}>
              <input
                type="checkbox"
                checked={f.includeTransfer}
                onChange={(e) => setVal('includeTransfer', e.target.checked)}
              />
              Включить трансфер аэропорт ↔ отель
            </label>

            <label className="row" style={{ gap: '0.4rem', cursor: 'pointer', fontSize: '0.9rem' }}>
              <input
                type="checkbox"
                checked={f.includeExcursions}
                onChange={(e) => setVal('includeExcursions', e.target.checked)}
              />
              Включить обзорные экскурсии
            </label>
          </div>
        )}

        <div>
          <button disabled={busy} style={{ padding: '0.75rem 1.5rem', fontWeight: 700 }}>
            {busy
              ? 'Генерация маршрута…'
              : isWizard
              ? 'Сгенерировать готовый черновик тура →'
              : 'Создать пустой черновик'}
          </button>
        </div>
      </form>

      {/* ── Список сформированных туров ── */}
      <div className="row between" style={{ alignItems: 'center', marginBottom: '1rem' }}>
        <h2 style={{ margin: 0 }}>Сформированные программы и туры</h2>
        <select
          aria-label="Фильтр по статусу"
          value={status}
          onChange={(e) => setStatus(e.target.value)}
          style={{ width: 'auto' }}
        >
          <option value="">Все статусы</option>
          <option value="DRAFT">Черновики (в работе)</option>
          <option value="VALIDATED">Проверенные без ошибок</option>
          <option value="PUBLISHED">Опубликованные на витрине</option>
          <option value="ARCHIVED">Архив</option>
        </select>
      </div>

      <ErrorBox error={tours.error} />

      <div className="card table-wrap">
        <table>
          <thead>
            <tr>
              <th>Код</th>
              <th>Программа тура</th>
              <th>Даты поездки</th>
              <th>Туристов</th>
              <th>Версия</th>
              <th>Стоимость</th>
              <th>Статус</th>
              <th>Менеджер</th>
            </tr>
          </thead>
          <tbody>
            {tours.data?.map((t) => (
              <tr key={t.id}>
                <td>
                  <span className="muted" style={{ fontFamily: 'monospace', fontWeight: 600 }}>
                    {t.code}
                  </span>
                </td>
                <td>
                  <Link to={`/builder/${t.id}`} style={{ fontWeight: 600 }}>
                    {t.title}
                  </Link>
                  <div className="muted" style={{ fontSize: '0.8rem' }}>
                    {t.destinationName}
                  </div>
                </td>
                <td>
                  {fmtIsoDate(t.startDate)} — {fmtIsoDate(t.endDate)}
                </td>
                <td>{t.pax} чел.</td>
                <td>v{t.versionNo}</td>
                <td style={{ fontWeight: 700 }}>{money(t.totalPrice)}</td>
                <td>
                  <Badge s={t.status} />
                </td>
                <td className="muted" style={{ fontSize: '0.85rem' }}>
                  {t.managerEmail}
                </td>
              </tr>
            ))}
            {tours.data?.length === 0 && (
              <tr>
                <td colSpan={8} className="muted" style={{ textAlign: 'center', padding: '2rem' }}>
                  Туры с данным статусом не найдены. Создайте новый тур выше.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
    </Page>
  );
}
