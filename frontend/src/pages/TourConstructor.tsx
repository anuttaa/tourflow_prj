import { FormEvent, useEffect, useMemo, useRef, useState } from 'react';
import { useParams } from 'react-router-dom';
import { api, fieldError } from '../api';
import { daysBetween, fmtDate, fmtDateTime, fmtTime, money, PROFILE_LABEL, TYPE_LABEL, utcToLocal, zonedToUtc } from '../format';
import { Alert, Badge, ErrorBox, Field, Modal, useLoad } from '../components/ui';

interface Seg {
  id: number;
  offerRef: number;
  type: string;
  title: string;
  startUtc: string;
  endUtc: string;
  tz: string;
  unitPrice: number;
  seasonK: number;
  unitsPerPax: number;
  freeSnapshot: number | null;
  stale: boolean;
  earlyCheckIn: boolean;
  classLevel: number;
  rating: number;
  placeFrom?: string;
  placeTo?: string;
}

interface Conflict {
  id: number;
  rule: string;
  severity: 'ERROR' | 'WARNING';
  segmentA: number | null;
  segmentB: number | null;
  segmentATitle?: string;
  segmentBTitle?: string;
  violationMin: number;
  hint: string;
  ruleDescription?: string;
}

interface Report {
  errors: number;
  warnings: number;
  catalogAvailable: boolean;
  conflicts: Conflict[];
  links: { fromId: number; toId: number; requiredMin: number; slackMin: number; critical: boolean }[];
  price: { total: number; perPerson: number; subtotal: number; discountRate: number; margin: number; pax: number };
}

interface Tour {
  id: number;
  code: string;
  title: string;
  destinationName: string;
  destinationId: number;
  status: string;
  versionNo: number;
  startDate: string;
  endDate: string;
  pax: number;
  budget: number | null;
  profile: string;
  totalPrice: number;
  perPerson: number;
  tz: string;
}

interface View {
  tour: Tour;
  segments: Seg[];
  report: Report | null;
}

interface Offer {
  id: number;
  type: string;
  title: string;
  basePrice: number;
  classLevel: number;
  ratingAvg: number;
  startUtc?: string;
  endUtc?: string;
  tz: string;
  placeFrom?: string;
  placeTo?: string;
  fromLat?: number;
  fromLon?: number;
  toLat?: number;
  toLon?: number;
}

interface Cand {
  candidateId: number;
  rank: number;
  score: number;
  type: string;
  title: string;
  start: string;
  end: string;
  tz: string;
  pricePerPax: number;
  rating: number;
  classLevel: number;
  deltaStartMin: number;
  deltaPrice: number;
  explanation: string;
  contributions: Record<string, number>;
}

interface Alts {
  requestId: number;
  empty: boolean;
  candidates: Cand[];
  hints: string[];
}

const CONTRIB: Record<string, string> = {
  price: 'Цена',
  time: 'Время',
  rating: 'Рейтинг',
  proximity: 'Близость',
  similarity: 'Схожесть',
};

const FIXED = ['FLIGHT', 'TRAIN', 'BUS'];

// Человекочитаемые названия правил вместо кодов VR-*
const RULE_LABEL: Record<string, string> = {
  'VR-01': 'Накладка событий по времени',
  'VR-02': 'Недостаточно времени на пересадку',
  'VR-03': 'Некорректное время или часовой пояс',
  'VR-04': 'Не хватает времени на дорогу / трансфер',
  'VR-05': 'Объект закрыт в это время (часы работы)',
  'VR-06': 'Несоответствие правилам заселения в отель',
  'VR-07': 'Даты выходят за границы тура',
  'VR-08': 'Неполный маршрут (нужен отель и билеты)',
  'VR-09': 'Превышение бюджета поездки',
  'VR-10': 'Недостаточно времени на ночной отдых',
};

// Преобразование минут в читаемый вид (например: 90 мин -> 1 ч 30 мин)
function formatDurationRu(minutes: number): string {
  const m = Math.abs(minutes);
  if (m < 60) return `${m} мин`;
  const hrs = Math.floor(m / 60);
  const rem = m % 60;
  return rem ? `${hrs} ч ${rem} мин` : `${hrs} ч`;
}

// Форматирование подсказки понятным человеческим языком без сухих минусовых цифр
function formatHumanHint(c: Conflict): string {
  if (c.rule === 'VR-01' && c.violationMin > 0) {
    return `События накладываются друг на друга на ${formatDurationRu(c.violationMin)}. Перенесите следующее событие позже или выберите другое время.`;
  }
  if (c.rule === 'VR-04' || c.rule === 'VR-02') {
    if (c.violationMin < 0) {
      return `Недостаточно времени на перемещение (не хватает ${formatDurationRu(-c.violationMin)}). Рекомендуется запланировать трансфер или перенести следующее событие.`;
    }
    return `Очень короткий интервал между событиями. Заложите дополнительное время на дорогу.`;
  }
  return c.hint;
}

export default function TourConstructor() {
  const { id } = useParams();
  const { data, error, loading, reload } = useLoad(() => api<View>(`/tours/${id}`), [id]);
  const [sel, setSel] = useState<number | null>(null);
  const [selectedDay, setSelectedDay] = useState<string | null>(null);
  const [err, setErr] = useState<unknown>(null);
  const [info, setInfo] = useState('');
  const [busy, setBusy] = useState(false);
  const [alts, setAlts] = useState<Alts | null>(null);
  const [altDeltaMin, setAltDeltaMin] = useState(180);
  const [altProfile, setAltProfile] = useState('');
  const [editing, setEditing] = useState(false);

  const tour = data?.tour;
  const segs = data?.segments ?? [];
  const report = data?.report;
  const editable = tour ? !['PUBLISHED', 'ARCHIVED'].includes(tour.status) : false;

  const bad = useMemo(() => {
    const m = new Map<number, 'ERROR' | 'WARNING'>();
    report?.conflicts.forEach((c) =>
      [c.segmentA, c.segmentB].forEach((s) => {
        if (s !== null && m.get(s) !== 'ERROR') m.set(s, c.severity);
      })
    );
    return m;
  }, [report]);

  const selected = segs.find((s) => s.id === sel) ?? null;

  // Фильтрация конфликтов: если ничего не выбрано — все; если выбран элемент — его конфликты
  const visibleConflicts = useMemo(() => {
    if (!report?.conflicts) return [];
    if (sel === null) return report.conflicts;
    return report.conflicts.filter((c) => c.segmentA === sel || c.segmentB === sel);
  }, [report, sel]);

  async function run<T>(fn: () => Promise<T>, ok?: string): Promise<T | undefined> {
    setBusy(true);
    setErr(null);
    setInfo('');
    try {
      const r = await fn();
      if (ok) setInfo(ok);
      reload();
      return r;
    } catch (e) {
      setErr(e);
      reload();
    } finally {
      setBusy(false);
    }
  }

  async function openAlternatives(segId: number, delta = altDeltaMin, prof = altProfile) {
    setBusy(true);
    setErr(null);
    try {
      setAlts(
        await api<Alts>(`/tours/${id}/alternatives`, {
          method: 'POST',
          body: {
            segmentId: segId,
            profile: prof || undefined,
            deltaMinutes: Number(delta),
          },
        })
      );
    } catch (e) {
      setErr(e);
    } finally {
      setBusy(false);
    }
  }

  async function apply(c: Cand) {
    if (!alts) return;
    const ok = await run(
      () =>
        api(`/tours/${id}/alternatives/${alts.requestId}/apply`, {
          method: 'POST',
          body: { candidateId: c.candidateId },
        }),
      'Альтернатива успешно применена, расписание перепроверено'
    );
    if (ok !== undefined) setAlts(null);
  }

  if (loading && !data) return <p className="muted">Загрузка конструктора тура…</p>;
  if (error || !tour) return <ErrorBox error={error} />;
  const days = daysBetween(tour.startDate, tour.endDate);

  return (
    <>
      <div className="row between" style={{ marginBottom: '.75rem', alignItems: 'center' }}>
        <div>
          <h1 style={{ marginBottom: '0.2rem' }}>{tour.title}</h1>
          <span className="muted" style={{ fontSize: '0.9rem' }}>
            Код: <b>{tour.code}</b> · {tour.destinationName} · {tour.startDate} — {tour.endDate} · {tour.pax} чел. · Профиль: «{PROFILE_LABEL[tour.profile]}» · Версия {tour.versionNo}
          </span>
        </div>
        <div className="row" style={{ gap: '0.5rem' }}>
          <Badge s={tour.status} />
          <button
            className="ghost small"
            disabled={busy}
            onClick={() =>
              run(
                () => api(`/tours/${id}/validate`, { method: 'POST', query: { refresh: true } }),
                'Проверка выполнена: доступность и цены синхронизированы'
              )
            }
          >
            Перепроверить тур
          </button>
          {editable && (
            <button className="ghost small" onClick={() => setEditing(!editing)}>
              Параметры
            </button>
          )}
          {editable && (
            <button
              className="small"
              disabled={busy}
              onClick={() => run(() => api(`/tours/${id}/publish`, { method: 'POST' }), 'Тур опубликован на витрине')}
            >
              Опубликовать
            </button>
          )}
          {tour.status === 'PUBLISHED' && (
            <button
              className="small"
              disabled={busy}
              onClick={() =>
                run(
                  () => api(`/tours/${id}/versions`, { method: 'POST' }),
                  'Создана новая версия для редактирования'
                )
              }
            >
              Новая версия
            </button>
          )}
        </div>
      </div>

      <ErrorBox error={err} />
      {info && <Alert kind="ok">{info}</Alert>}
      {report && !report.catalogAvailable && (
        <Alert kind="warn">Каталог поставщиков временно недоступен: актуальность мест не подтверждена.</Alert>
      )}
      {tour.status === 'PUBLISHED' && (
        <Alert kind="warn">
          Тур опубликован и доступен туристам на витрине. Для внесения изменений создайте новую версию.
        </Alert>
      )}
      {editing && <ParamsForm tour={tour} onDone={() => { setEditing(false); reload(); }} />}

      {/* Инфо-карточки */}
      <div className="row" style={{ margin: '.75rem 0', gap: '1rem' }}>
        <div className="card" style={{ flex: 1 }}>
          <span className="muted" style={{ fontSize: '0.8rem', textTransform: 'uppercase' }}>Итоговая стоимость</span>
          <div className="price">{money(report?.price.total ?? tour.totalPrice)}</div>
          <span className="muted" style={{ fontSize: '0.85rem' }}>
            {money(report?.price.perPerson ?? tour.perPerson)} / чел.
            {report && report.price.discountRate > 0
              ? ` · скидка группе ${Math.round(report.price.discountRate * 100)}%`
              : ''}
          </span>
        </div>
        <div className="card" style={{ flex: 1 }}>
          <span className="muted" style={{ fontSize: '0.8rem', textTransform: 'uppercase' }}>Бюджет заказчика</span>
          <div className="price">{tour.budget !== null ? money(tour.budget) : 'Не ограничен'}</div>
        </div>
        <div className="card" style={{ flex: 1 }}>
          <span className="muted" style={{ fontSize: '0.8rem', textTransform: 'uppercase' }}>Результат валидации</span>
          <div
            className="price"
            style={{ color: report && report.errors > 0 ? 'var(--red)' : 'var(--green)' }}
          >
            {report ? (report.errors === 0 ? 'Ошибок нет' : `${report.errors} ошибок, ${report.warnings} предупр.`) : '—'}
          </div>
        </div>
      </div>

      <div className="builder">
        <div className="stack" style={{ minWidth: 0 }}>
          {/* Временная шкала с Drag-and-Drop */}
          <section className="card timeline" aria-label="Временная шкала тура">
            <div className="row between" style={{ alignItems: 'baseline', marginBottom: '0.75rem' }}>
              <h2 style={{ margin: 0 }}>
                Шкала расписания{' '}
                <span className="muted" style={{ fontSize: '.8rem', fontWeight: 400 }}>
                  ({tour.tz})
                </span>
              </h2>
              {editable && (
                <span className="muted" style={{ fontSize: '0.82rem' }}>
                  Перетаскивайте блоки мышью (время округляется с шагом 15 мин)
                </span>
              )}
            </div>

            <div className="hours">
              <span />
              {Array.from({ length: 24 }, (_, h) => (
                <span key={h}>{h}:00</span>
              ))}
            </div>

            {days.map((d) => (
              <DayRow
                key={d}
                day={d}
                tz={tour.tz}
                segs={segs}
                bad={bad}
                sel={sel}
                editable={editable}
                onSel={(segId) => {
                  setSel(segId);
                  setSelectedDay(d);
                }}
                onSelectDay={() => setSelectedDay(d)}
                onMoveSeg={(segId, newStart, newEnd, early) =>
                  run(() =>
                    api(`/tours/${id}/segments/${segId}`, {
                      method: 'PATCH',
                      body: { start: newStart, end: newEnd, earlyCheckIn: early },
                    }),
                    'Время обновлено перетаскиванием'
                  )
                }
              />
            ))}

            {segs.length === 0 && (
              <p className="muted" style={{ padding: '2rem 1rem', textAlign: 'center' }}>
                Маршрут пуст. Добавьте услуги из каталога справа или воспользуйтесь Мастером автосборки.
              </p>
            )}
          </section>

          {/* Блок конфликтов с понятными объяснениями */}
          <section className="card" aria-label="Конфликты расписания">
            <div className="row between" style={{ alignItems: 'center', marginBottom: '0.75rem' }}>
              <h2 style={{ margin: 0 }}>
                {sel === null
                  ? `Конфликты и стыковки (${visibleConflicts.length})`
                  : `Конфликты элемента «${selected?.title || `#${sel}`}» (${visibleConflicts.length})`}
              </h2>
              {sel !== null && (
                <button
                  type="button"
                  className="ghost small"
                  onClick={() => setSel(null)}
                >
                  Показать все конфликты
                </button>
              )}
            </div>

            {visibleConflicts.length === 0 ? (
              <Alert kind="ok">
                {sel === null
                  ? 'Все элементы маршрута идеально состыкованы. Нарушений нет.'
                  : 'У выбранного элемента нет накладок с другими событиями тура.'}
              </Alert>
            ) : (
              <div className="stack" style={{ gap: '0.75rem' }}>
                {visibleConflicts.map((c) => {
                  const segA = segs.find((s) => s.id === c.segmentA);
                  const segB = segs.find((s) => s.id === c.segmentB);
                  const ruleName = RULE_LABEL[c.rule] || c.ruleDescription || c.rule;
                  const humanText = formatHumanHint(c);

                  return (
                    <div
                      key={c.id}
                      className={`conflict ${c.severity}`}
                      style={{
                        padding: '1rem 1.25rem',
                        borderRadius: 'var(--radius)',
                        display: 'flex',
                        flexDirection: 'column',
                        gap: '0.5rem',
                      }}
                    >
                      <div className="row between" style={{ alignItems: 'flex-start' }}>
                        <div>
                          <b style={{ fontSize: '0.95rem', color: c.severity === 'ERROR' ? 'var(--red)' : '#d97706' }}>
                            {ruleName}
                          </b>
                          <div style={{ marginTop: '0.25rem', fontSize: '0.9rem', lineHeight: 1.45 }}>
                            {humanText}
                          </div>
                        </div>
                        <Badge s={c.severity === 'ERROR' ? 'Ошибка стыковки' : 'Предупреждение'} />
                      </div>

                      <div
                        className="row between"
                        style={{
                          paddingTop: '0.5rem',
                          borderTop: '1px solid rgba(0,0,0,0.06)',
                          alignItems: 'center',
                          flexWrap: 'wrap',
                          gap: '0.5rem',
                        }}
                      >
                        <div className="muted" style={{ fontSize: '0.85rem' }}>
                          Участники: <b>{segA?.title || 'Событие 1'}</b>
                          {segB && <> ↔ <b>{segB.title}</b></>}
                        </div>

                        {editable && (
                          <div className="row" style={{ gap: '0.5rem' }}>
                            {c.segmentA && (
                              <button
                                type="button"
                                className="ghost small"
                                disabled={busy}
                                onClick={() => openAlternatives(c.segmentA!)}
                              >
                                Подобрать замену ({segA ? TYPE_LABEL[segA.type] || segA.type : '1'})
                              </button>
                            )}
                            {c.segmentB && (
                              <button
                                type="button"
                                className="ghost small"
                                disabled={busy}
                                onClick={() => openAlternatives(c.segmentB!)}
                              >
                                Подобрать замену ({segB ? TYPE_LABEL[segB.type] || segB.type : '2'})
                              </button>
                            )}
                            <button
                              type="button"
                              className="small"
                              onClick={() => setSel(c.segmentA ?? c.segmentB)}
                            >
                              Выбрать на шкале
                            </button>
                          </div>
                        )}
                      </div>
                    </div>
                  );
                })}
              </div>
            )}
          </section>
        </div>

        {/* Правая колонка */}
        <aside className="stack sidepanel">
          {selected && (
            <SegmentPanel
              key={selected.id}
              seg={selected}
              tz={tour.tz}
              editable={editable}
              busy={busy}
              tourId={tour.id}
              onAlt={() => openAlternatives(selected.id)}
              onDelete={() =>
                run(
                  () => api(`/tours/${id}/segments/${selected.id}`, { method: 'DELETE' }),
                  'Сегмент удалён из маршрута'
                ).then(() => setSel(null))
              }
              onMove={(start, end, early) =>
                run(() =>
                  api(`/tours/${id}/segments/${selected.id}`, {
                    method: 'PATCH',
                    body: { start, end, earlyCheckIn: early },
                  })
                )
              }
            />
          )}

          {/* Каталог с фильтрацией по дням тура */}
          {editable && (
            <CatalogPanel
              tour={tour}
              days={days}
              selectedDay={selectedDay}
              onSelectDay={setSelectedDay}
              onAdd={(offerId, start, end) =>
                run(
                  () => api(`/tours/${id}/segments`, { method: 'POST', body: { offerId, start, end } }),
                  'Услуга успешно добавлена в программу'
                )
              }
              busy={busy}
            />
          )}
        </aside>
      </div>

      {/* Модалка автоподбора альтернатив с человеческим интерфейсом */}
      {alts && (
        <Modal title="Автоподбор альтернативных вариантов" onClose={() => setAlts(null)}>
          <div className="row" style={{ marginBottom: '1rem', flexWrap: 'wrap', gap: '0.75rem' }}>
            <Field label="Профиль подбора">
              <select
                value={altProfile}
                onChange={(e) => setAltProfile(e.target.value)}
              >
                <option value="">Как в туре ({PROFILE_LABEL[tour.profile]})</option>
                {Object.entries(PROFILE_LABEL).map(([k, v]) => (
                  <option key={k} value={k}>
                    {v}
                  </option>
                ))}
              </select>
            </Field>

            <Field label="Допустимое отклонение времени">
              <select
                value={altDeltaMin}
                onChange={(e) => setAltDeltaMin(Number(e.target.value))}
              >
                <option value="60">±1 час</option>
                <option value="180">±3 часа (рекомендуется)</option>
                <option value="360">±6 часов</option>
                <option value="720">±12 часов</option>
                <option value="1440">В течение суток</option>
              </select>
            </Field>

            <div style={{ display: 'flex', alignItems: 'flex-end' }}>
              <button
                className="ghost"
                disabled={busy}
                onClick={() => selected && openAlternatives(selected.id, altDeltaMin, altProfile)}
              >
                Обновить варианты
              </button>
            </div>
          </div>

          <ErrorBox error={err} />

          {alts.empty && (
            <Alert kind="warn">
              <b>Подходящих вариантов не найдено.</b>
              <div style={{ marginTop: '0.4rem' }}>
                {alts.hints.map((h, i) => (
                  <div key={i} style={{ marginBottom: '0.2rem' }}>• {h}</div>
                ))}
              </div>
            </Alert>
          )}

          <div className="stack" style={{ gap: '0.75rem', maxHeight: '60vh', overflowY: 'auto' }}>
            {alts.candidates.map((c) => (
              <article key={c.candidateId} className="cand" style={{ padding: '1rem', border: '1px solid var(--line)' }}>
                <div className="row between">
                  <div>
                    <b style={{ fontSize: '1rem' }}>#{c.rank} {c.title}</b>
                    <div className="muted" style={{ fontSize: '0.85rem', marginTop: '0.2rem' }}>
                      {TYPE_LABEL[c.type] ?? c.type} · {fmtDateTime(c.start, c.tz)} — {fmtTime(c.end, c.tz)} · {c.classLevel}★ · Рейтинг {c.rating}
                    </div>
                  </div>
                  <div style={{ textAlign: 'right' }}>
                    <div className="score" style={{ fontSize: '1.2rem', fontWeight: 800 }}>
                      {(c.score * 100).toFixed(0)}
                    </div>
                    <span className="muted" style={{ fontSize: '0.75rem' }}>баллов</span>
                  </div>
                </div>

                <p style={{ margin: '.5rem 0', fontSize: '0.9rem' }}>
                  {c.explanation}. Стоимость: <b>{money(c.pricePerPax)}</b> на чел.
                </p>

                <div className="stack" style={{ marginBottom: '.75rem', gap: '0.25rem' }}>
                  {Object.entries(c.contributions).map(([k, v]) => (
                    <div key={k} className="contrib" style={{ fontSize: '0.82rem' }}>
                      <span>{CONTRIB[k] ?? k}</span>
                      <span style={{ background: 'var(--line)', height: 6, borderRadius: 3 }}>
                        <span className="bar2" style={{ width: `${Math.min(100, v * 400)}%` }} />
                      </span>
                      <span>{v.toFixed(2)}</span>
                    </div>
                  ))}
                </div>

                <button disabled={busy} onClick={() => apply(c)}>
                  Заменить на этот вариант
                </button>
              </article>
            ))}
          </div>
        </Modal>
      )}
    </>
  );
}

// ── DayRow с Drag-and-Drop ────────────────────────────────────────────────────
function DayRow({
  day,
  tz,
  segs,
  bad,
  sel,
  editable,
  onSel,
  onSelectDay,
  onMoveSeg,
}: {
  day: string;
  tz: string;
  segs: Seg[];
  bad: Map<number, string>;
  sel: number | null;
  editable: boolean;
  onSel: (id: number) => void;
  onSelectDay: () => void;
  onMoveSeg: (segId: number, start: string, end: string, early: boolean) => void;
}) {
  const laneRef = useRef<HTMLDivElement>(null);
  const [dragState, setDragState] = useState<{
    segId: number;
    startX: number;
    currentX: number;
    initialStartUtc: string;
    initialEndUtc: string;
    early: boolean;
  } | null>(null);

  const ds = new Date(zonedToUtc(`${day}T00:00`, tz)).getTime();
  const next = new Date(Date.parse(day + 'T00:00:00Z') + 86400000).toISOString().slice(0, 10);
  const de = new Date(zonedToUtc(`${next}T00:00`, tz)).getTime();
  const dayMs = de - ds;

  const items = segs
    .map((s) => ({ s, a: Math.max(Date.parse(s.startUtc), ds), b: Math.min(Date.parse(s.endUtc), de) }))
    .filter((x) => x.b > x.a)
    .sort((x, y) => x.a - y.a);

  const rows: number[] = [];
  const placed = items.map((x) => {
    let r = rows.findIndex((end) => end <= x.a);
    if (r === -1) {
      r = rows.length;
      rows.push(0);
    }
    rows[r] = x.b;
    return { ...x, r };
  });

  useEffect(() => {
    if (!dragState) return;

    function handleMouseMove(e: MouseEvent) {
      setDragState((prev) => (prev ? { ...prev, currentX: e.clientX } : null));
    }

    function handleMouseUp(e: MouseEvent) {
      if (!dragState || !laneRef.current) {
        setDragState(null);
        return;
      }

      const laneW = laneRef.current.clientWidth || 1;
      const dx = e.clientX - dragState.startX;
      const fifteenMinMs = 15 * 60 * 1000;
      const rawDtMs = (dx / laneW) * dayMs;
      const dtMs = Math.round(rawDtMs / fifteenMinMs) * fifteenMinMs;

      if (Math.abs(dtMs) >= fifteenMinMs) {
        const newStartMs = Date.parse(dragState.initialStartUtc) + dtMs;
        const newEndMs = Date.parse(dragState.initialEndUtc) + dtMs;
        onMoveSeg(
          dragState.segId,
          new Date(newStartMs).toISOString(),
          new Date(newEndMs).toISOString(),
          dragState.early
        );
      }

      setDragState(null);
    }

    window.addEventListener('mousemove', handleMouseMove);
    window.addEventListener('mouseup', handleMouseUp);
    return () => {
      window.removeEventListener('mousemove', handleMouseMove);
      window.removeEventListener('mouseup', handleMouseUp);
    };
  }, [dragState, dayMs, onMoveSeg]);

  return (
    <div className="day-row" onClick={onSelectDay} style={{ cursor: 'pointer' }}>
      <div className="day-label">
        <b>{fmtDate(new Date(ds).toISOString(), tz)}</b>
      </div>
      <div
        ref={laneRef}
        className="lane"
        style={{ height: Math.max(1, rows.length) * 48 + 6, position: 'relative' }}
      >
        {placed.map(({ s, a, b, r }) => {
          const isFixed = FIXED.includes(s.type);
          const isDraggable = editable && !isFixed;
          const isCurrentlyDragging = dragState?.segId === s.id;
          const dragOffset = isCurrentlyDragging ? dragState.currentX - dragState.startX : 0;

          return (
            <button
              key={s.id}
              className={`bar type-${s.type} ${sel === s.id ? 'sel' : ''} ${
                bad.get(s.id) === 'ERROR' ? 'err' : bad.get(s.id) === 'WARNING' ? 'wrn' : ''
              } ${isDraggable ? 'dnd-bar' : 'dnd-fixed'} ${isCurrentlyDragging ? 'dragging' : ''}`}
              style={{
                left: `${((a - ds) / dayMs) * 100}%`,
                width: `${Math.max(((b - a) / dayMs) * 100, 3)}%`,
                top: 6 + r * 48,
                transform: dragOffset ? `translateX(${dragOffset}px)` : undefined,
                transition: isCurrentlyDragging ? 'none' : 'box-shadow 0.2s',
              }}
              onClick={(e) => {
                e.stopPropagation();
                onSel(s.id);
              }}
              onMouseDown={(e) => {
                if (!isDraggable) return;
                setDragState({
                  segId: s.id,
                  startX: e.clientX,
                  currentX: e.clientX,
                  initialStartUtc: s.startUtc,
                  initialEndUtc: s.endUtc,
                  early: s.earlyCheckIn,
                });
              }}
              title={`${s.title}\n${fmtTime(s.startUtc, tz)}–${fmtTime(s.endUtc, tz)}${
                isDraggable ? '\n(Перетаскивайте для изменения времени)' : '\n(Рейс с фиксированным расписанием)'
              }`}
            >
              {fmtTime(s.startUtc, tz)} {s.title}
            </button>
          );
        })}
      </div>
    </div>
  );
}

// ── SegmentPanel: сохранение фиксированной длительности при сдвиге ─────────────
function SegmentPanel({
  seg,
  tz,
  editable,
  busy,
  onAlt,
  onDelete,
  onMove,
}: {
  seg: Seg;
  tz: string;
  editable: boolean;
  busy: boolean;
  tourId: number;
  onAlt: () => void;
  onDelete: () => void;
  onMove: (s: string, e: string, early: boolean) => void;
}) {
  const [s, setS] = useState(utcToLocal(seg.startUtc, seg.tz));
  const [e, setE] = useState(utcToLocal(seg.endUtc, seg.tz));
  const [early, setEarly] = useState(seg.earlyCheckIn);
  const fixed = FIXED.includes(seg.type);

  // Вычисляем длительность сегмента (мс)
  const durationMs = useMemo(() => {
    return Math.max(0, Date.parse(seg.endUtc) - Date.parse(seg.startUtc));
  }, [seg.startUtc, seg.endUtc]);

  // При изменении времени начала автоматически сдвигаем окончание для фиксированных длительностей (экскурсии, трансфер)
  function handleStartChange(newStartLocal: string) {
    setS(newStartLocal);
    if (seg.type === 'EXCURSION' || seg.type === 'TRANSFER') {
      const newStartMs = Date.parse(zonedToUtc(newStartLocal, seg.tz));
      const newEndIso = new Date(newStartMs + durationMs).toISOString();
      setE(utcToLocal(newEndIso, seg.tz));
    }
  }

  const shift = (min: number) => {
    const f = (x: string) =>
      utcToLocal(new Date(Date.parse(zonedToUtc(x, seg.tz)) + min * 60000).toISOString(), seg.tz);
    setS(f(s));
    setE(f(e));
  };

  return (
    <section className="card stack" key={seg.id}>
      <h3>
        {TYPE_LABEL[seg.type] || seg.type}: {seg.title}
      </h3>
      <p className="muted" style={{ fontSize: '0.88rem' }}>
        {fmtDateTime(seg.startUtc, seg.tz)} — {fmtDateTime(seg.endUtc, seg.tz)} ({seg.tz})
        <br />
        {money(seg.unitPrice)} · {formatDurationRu(Math.round(durationMs / 60000))} длительность
        {seg.freeSnapshot !== null && ` · свободно мест: ${seg.freeSnapshot}`}
      </p>

      {editable && !fixed && (
        <>
          <Field label="Время начала (местное)">
            <input type="datetime-local" value={s} onChange={(x) => handleStartChange(x.target.value)} />
          </Field>
          <Field label="Время окончания">
            <input type="datetime-local" value={e} onChange={(x) => setE(x.target.value)} />
          </Field>
          <div className="row" style={{ gap: '0.35rem', flexWrap: 'wrap' }}>
            {[
              [-60, '−1 ч'],
              [-30, '−30 м'],
              [30, '+30 м'],
              [60, '+1 ч'],
              [1440, '+1 д'],
            ].map(([m, l]) => (
              <button key={String(l)} className="ghost small" type="button" onClick={() => shift(Number(m))}>
                {l}
              </button>
            ))}
          </div>
          {seg.type === 'HOTEL' && (
            <label className="row" style={{ gap: '0.4rem', fontSize: '0.88rem' }}>
              <input type="checkbox" checked={early} onChange={(x) => setEarly(x.target.checked)} /> Ранний заезд по запросу
            </label>
          )}
          <button
            disabled={busy}
            onClick={() => onMove(zonedToUtc(s, seg.tz), zonedToUtc(e, seg.tz), early)}
          >
            Сохранить время
          </button>
        </>
      )}

      {fixed && (
        <p className="muted" style={{ fontSize: '0.85rem' }}>
          Время рейса зафиксировано расписанием перевозчика. Чтобы изменить время, подберите другой рейс.
        </p>
      )}

      {editable && (
        <div className="row" style={{ gap: '0.5rem', marginTop: '0.5rem' }}>
          <button className="ghost small" disabled={busy} onClick={onAlt}>
            Подобрать замену
          </button>
          <button className="danger small" disabled={busy} onClick={onDelete}>
            Удалить
          </button>
        </div>
      )}
    </section>
  );
}

// ── CatalogPanel с фильтрацией опций по дням тура ─────────────────────────────
function CatalogPanel({
  tour,
  days,
  selectedDay,
  onSelectDay,
  onAdd,
  busy,
}: {
  tour: Tour;
  days: string[];
  selectedDay: string | null;
  onSelectDay: (d: string | null) => void;
  onAdd: (offerId: number, start?: string, end?: string) => void;
  busy: boolean;
}) {
  const [type, setType] = useState('');
  const [q, setQ] = useState('');
  const [applied, setApplied] = useState({ type: '', q: '' });

  const { data, error } = useLoad(
    () =>
      api<{ content: Offer[] }>('/catalog/offers', {
        query: { destinationId: tour.destinationId, type: applied.type, q: applied.q, size: 50, sort: 'title,asc' },
      }),
    [tour.destinationId, applied]
  );

  const [adding, setAdding] = useState<Offer | null>(null);
  const [s, setS] = useState('');
  const [e, setE] = useState('');

  // Фильтрация опций по выбранному дню
  const filteredOffers = useMemo(() => {
    const list = data?.content || [];
    if (!selectedDay) return list;

    return list.filter((o) => {
      // Для рейсов с фиксированной датой вылета
      if (o.startUtc) {
        const offerDate = o.startUtc.slice(0, 10);
        return offerDate === selectedDay;
      }
      // Отели, экскурсии и трансферы доступны для добавления в выбранный день
      return true;
    });
  }, [data?.content, selectedDay]);

  function startAdd(o: Offer) {
    if (o.startUtc) {
      onAdd(o.id);
      return;
    }
    const targetDay = selectedDay || tour.startDate;
    setAdding(o);
    if (o.type === 'HOTEL') {
      setS(`${tour.startDate}T14:30`);
      setE(`${tour.endDate}T11:30`);
    } else if (o.type === 'TRANSFER') {
      setS(`${targetDay}T12:00`);
      setE(`${targetDay}T13:00`);
    } else if (o.type === 'INSURANCE') {
      setS(`${tour.startDate}T00:00`);
      setE(`${tour.endDate}T23:59`);
    } else {
      // Экскурсия: фиксированная длительность 3 часа
      setS(`${targetDay}T10:00`);
      setE(`${targetDay}T13:00`);
    }
  }

  return (
    <section className="card stack">
      <h3>Каталог доступных услуг ({tour.destinationName})</h3>

      {/* Фильтр по дням тура */}
      <div style={{ display: 'flex', gap: '0.35rem', overflowX: 'auto', paddingBottom: '0.4rem' }}>
        <button
          type="button"
          className={selectedDay === null ? 'small' : 'ghost small'}
          onClick={() => onSelectDay(null)}
          style={{ whiteSpace: 'nowrap' }}
        >
          Все дни
        </button>
        {days.map((d, i) => (
          <button
            key={d}
            type="button"
            className={selectedDay === d ? 'small' : 'ghost small'}
            onClick={() => onSelectDay(d)}
            style={{ whiteSpace: 'nowrap' }}
          >
            День {i + 1} ({d.slice(5)})
          </button>
        ))}
      </div>

      <div className="row" style={{ gap: '0.5rem' }}>
        <select
          aria-label="Тип опции"
          value={type}
          onChange={(x) => {
            setType(x.target.value);
            setApplied({ type: x.target.value, q });
          }}
          style={{ width: '40%' }}
        >
          <option value="">Все типы</option>
          {Object.entries(TYPE_LABEL).map(([k, v]) => (
            <option key={k} value={k}>
              {v}
            </option>
          ))}
        </select>
        <input
          aria-label="Поиск"
          placeholder="Поиск по названию..."
          value={q}
          onChange={(x) => setQ(x.target.value)}
          onKeyDown={(x) => x.key === 'Enter' && setApplied({ type, q })}
          style={{ flex: 1, minWidth: 90 }}
        />
      </div>

      <ErrorBox error={error} />

      <div style={{ maxHeight: '380px', overflowY: 'auto', display: 'flex', flexDirection: 'column', gap: '0.5rem' }}>
        {filteredOffers.map((o) => (
          <div key={o.id} className="offer" style={{ padding: '0.75rem', border: '1px solid var(--line)', borderRadius: 'var(--radius)' }}>
            <b>{o.title}</b>
            <div className="muted" style={{ fontSize: '0.82rem', margin: '0.2rem 0' }}>
              {TYPE_LABEL[o.type] ?? o.type} · {money(o.basePrice)} · {o.classLevel}★ · Рейтинг {Number(o.ratingAvg).toFixed(1)}
              {o.startUtc && (
                <> · {fmtDateTime(o.startUtc, o.tz)}–{fmtTime(o.endUtc!, o.tz)}</>
              )}
            </div>
            <button className="small" disabled={busy} onClick={() => startAdd(o)}>
              + Добавить в тур
            </button>
          </div>
        ))}

        {filteredOffers.length === 0 && (
          <p className="muted" style={{ padding: '1rem', textAlign: 'center', fontSize: '0.9rem' }}>
            {selectedDay
              ? `Для дня ${selectedDay} нет подходящих опций данного типа.`
              : 'В каталоге ничего не найдено.'}
          </p>
        )}
      </div>

      {adding && (
        <Modal title={`Добавить услугу: ${adding.title}`} onClose={() => setAdding(null)}>
          <form
            className="stack"
            onSubmit={(ev: FormEvent) => {
              ev.preventDefault();
              onAdd(adding.id, zonedToUtc(s, tour.tz), zonedToUtc(e, tour.tz));
              setAdding(null);
            }}
          >
            <Field label={`Время начала (${tour.tz})`}>
              <input type="datetime-local" required value={s} onChange={(x) => setS(x.target.value)} />
            </Field>
            <Field label="Время окончания">
              <input type="datetime-local" required value={e} onChange={(x) => setE(x.target.value)} />
            </Field>
            <button style={{ marginTop: '0.5rem' }}>Добавить в маршрутную программу</button>
          </form>
        </Modal>
      )}
    </section>
  );
}

function ParamsForm({ tour, onDone }: { tour: Tour; onDone: () => void }) {
  const [f, setF] = useState({
    title: tour.title,
    startDate: tour.startDate,
    endDate: tour.endDate,
    pax: tour.pax,
    budget: tour.budget ?? '',
    profile: tour.profile,
  });
  const [error, setError] = useState<unknown>(null);

  async function save(e: FormEvent) {
    e.preventDefault();
    setError(null);
    try {
      await api(`/tours/${tour.id}`, {
        method: 'PUT',
        body: {
          ...f,
          destinationId: tour.destinationId,
          pax: Number(f.pax),
          budget: f.budget === '' ? null : Number(f.budget),
        },
      });
      onDone();
    } catch (err) {
      setError(err);
    }
  }

  return (
    <form className="card stack" onSubmit={save} style={{ marginBottom: '.75rem' }}>
      <ErrorBox error={error} />
      <div className="form-grid">
        <Field label="Название тура" error={fieldError(error, 'title')}>
          <input required value={f.title} onChange={(e) => setF({ ...f, title: e.target.value })} />
        </Field>
        <Field label="Дата начала">
          <input type="date" required value={f.startDate} onChange={(e) => setF({ ...f, startDate: e.target.value })} />
        </Field>
        <Field label="Дата окончания">
          <input type="date" required value={f.endDate} onChange={(e) => setF({ ...f, endDate: e.target.value })} />
        </Field>
        <Field label="Число участников">
          <input
            type="number"
            min={1}
            max={200}
            value={f.pax}
            onChange={(e) => setF({ ...f, pax: Number(e.target.value) })}
          />
        </Field>
        <Field label="Бюджет (₽)">
          <input
            type="number"
            min={0}
            value={f.budget}
            onChange={(e) => setF({ ...f, budget: e.target.value })}
          />
        </Field>
        <Field label="Профиль">
          <select value={f.profile} onChange={(e) => setF({ ...f, profile: e.target.value })}>
            {Object.entries(PROFILE_LABEL).map(([k, v]) => (
              <option key={k} value={k}>
                {v}
              </option>
            ))}
          </select>
        </Field>
      </div>
      <div>
        <button>Сохранить изменения и пересчитать</button>
      </div>
    </form>
  );
}
