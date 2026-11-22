import { useMemo } from 'react';
import { Link, useParams } from 'react-router-dom';
import { api } from '../api';
import { useAuth } from '../auth';
import { daysBetween, fmtDate, fmtIsoDate, fmtTime, money, TYPE_LABEL } from '../format';
import { destinationImage } from '../images';
import { ErrorBox, useLoad } from '../components/ui';

interface Item {
  type: string;
  title: string;
  startUtc: string;
  endUtc: string;
  tz: string;
  placeFrom?: string;
  placeTo?: string;
}

interface View {
  tour: {
    id: number;
    title: string;
    destinationName: string;
    startDate: string;
    endDate: string;
    pax: number;
    totalPrice: number;
    perPerson: number;
  };
  program: Item[];
}

export default function TourDetail() {
  const { id } = useParams();
  const { has, user } = useAuth();
  const { data, error, loading } = useLoad(() => api<View>(`/public/tours/${id}`), [id]);

  const days = useMemo(() => {
    const m = new Map<string, Item[]>();
    data?.program.forEach((p) => {
      const k = fmtDate(p.startUtc, p.tz);
      m.set(k, [...(m.get(k) ?? []), p]);
    });
    return [...m.entries()];
  }, [data]);

  if (loading) return <p className="muted tour-content-wrap">Загрузка информации о туре…</p>;
  if (error || !data) return <div className="tour-content-wrap"><ErrorBox error={error} /></div>;

  const t = data.tour;
  const duration = daysBetween(t.startDate, t.endDate).length;

  return (
    <div className="romantic-design tour-detail-page">
      <nav className="breadcrumbs tour-content-wrap" aria-label="Хлебные крошки">
        <Link to="/">Главная</Link>
        <span aria-hidden="true">/</span>
        <Link to="/#tours">Туры</Link>
        <span aria-hidden="true">/</span>
        <span aria-current="page">{t.title}</span>
      </nav>

      <section className="tour-detail-hero tour-content-wrap" aria-labelledby="tour-title">
        <div className="tour-intro">
          <span className="eyebrow">{t.destinationName} · {fmtIsoDate(t.startDate)} — {fmtIsoDate(t.endDate)}</span>
          <h1 id="tour-title">{t.title}</h1>
          <div className="tour-intro-meta">
            <span>{duration} дн.</span>
            <span>до {t.pax} чел. в группе</span>
            <span>{t.destinationName}</span>
          </div>
          <div className="tour-intro-price">
            <span>Стоимость от</span>
            <strong>{money(t.perPerson)}</strong>
            <small>на одного человека</small>
          </div>
          <a className="tour-intro-link" href="#booking">Выбрать дату <span aria-hidden="true">↓</span></a>
        </div>
        <figure className="tour-artwork">
          <img src={destinationImage(t.destinationName)} alt={t.title} />
          <figcaption><span>{t.destinationName}</span><span>Пейзажная серия · TourFlow</span></figcaption>
        </figure>
      </section>

      <div className="tour-content-wrap tour-detail-grid">
        <div className="tour-main-column">
          <section className="tour-facts" aria-labelledby="facts-title">
            <div className="section-kicker">
              <span className="eyebrow">Коротко о поездке</span>
              <h2 id="facts-title">Условия тура</h2>
            </div>
            <dl className="tour-facts-grid">
              <div><dt>Продолжительность</dt><dd>{duration} дней</dd></div>
              <div><dt>Даты поездки</dt><dd>{fmtIsoDate(t.startDate)} — {fmtIsoDate(t.endDate)}</dd></div>
              <div><dt>Направление</dt><dd>{t.destinationName}</dd></div>
              <div><dt>Размер группы</dt><dd>{t.pax} человек</dd></div>
              <div><dt>Стоимость на человека</dt><dd>{money(t.perPerson)}</dd></div>
              <div><dt>Полная стоимость</dt><dd>{money(t.totalPrice)}</dd></div>
            </dl>
          </section>

          <section className="itinerary-section" aria-labelledby="itinerary-title">
            <div className="section-kicker">
              <span className="eyebrow">План путешествия</span>
              <h2 id="itinerary-title">Программа по дням</h2>
            </div>
            {days.length === 0 ? (
              <p className="muted">Программа формируется туроператором.</p>
            ) : (
              <div className="itinerary-list">
                {days.map(([day, items], index) => (
                  <details className="itinerary-day" key={day} open={index === 0}>
                    <summary>
                      <span className="itinerary-day-number">{String(index + 1).padStart(2, '0')}</span>
                      <span className="itinerary-day-heading">
                        <strong>День {index + 1} · {day}</strong>
                        <small>{items[0]?.placeTo || items[0]?.placeFrom || t.destinationName}</small>
                      </span>
                      <span className="itinerary-toggle" aria-hidden="true">+</span>
                    </summary>
                    <div className="itinerary-content">
                      <ol>
                        {items.map((p, i) => (
                          <li key={i}>
                            <time>{fmtTime(p.startUtc, p.tz)}</time>
                            <span>
                              <b>{TYPE_LABEL[p.type] ?? p.type}:</b> {p.title}
                              {p.placeFrom && p.placeTo && p.placeFrom !== p.placeTo && (
                                <> · {p.placeFrom} → {p.placeTo}</>
                              )}
                              {' '}<small>до {fmtTime(p.endUtc, p.tz)}</small>
                            </span>
                          </li>
                        ))}
                      </ol>
                    </div>
                  </details>
                ))}
              </div>
            )}
          </section>
        </div>

        <aside className="tour-booking" id="booking" aria-labelledby="booking-title">
          <span className="eyebrow">Место в группе</span>
          <h2 id="booking-title">{money(t.perPerson)} <small>/ чел.</small></h2>
          <p className="booking-note">Предварительная стоимость на выбранные даты</p>
          <div className="booking-field"><span>Даты поездки</span><b>{fmtIsoDate(t.startDate)} — {fmtIsoDate(t.endDate)}</b></div>
          <div className="booking-field"><span>Размер группы</span><b>до {t.pax} человек</b></div>
          <div className="booking-total"><span>Итого за группу</span><strong>{money(t.totalPrice)}</strong></div>
          {has('CLIENT') && (
            <Link className="booking-cta" to={`/tours/${id}/book`}>Оформить заявку <span aria-hidden="true">↗</span></Link>
          )}
          {user && !has('CLIENT') && (
            <p className="booking-disclaimer">Бронирование доступно для туристов (роль «Турист»).</p>
          )}
          {!user && (
            <>
              <Link className="booking-cta" to="/login">Войти и оформить <span aria-hidden="true">↗</span></Link>
              <p className="booking-disclaimer">Войдите или зарегистрируйтесь, чтобы оставить заявку на этот тур.</p>
            </>
          )}
          <p className="booking-disclaimer">Оформление заявки не резервирует места. Наличие и полные условия подтвердит менеджер.</p>
        </aside>
      </div>

      <section className="tour-contact tour-content-wrap" id="contact">
        <div>
          <span className="eyebrow">Поможем с деталями</span>
          <h2>Остались вопросы<br /><em>по этому маршруту?</em></h2>
          <p>Уточните даты, состав программы или требования к поездке у команды TourFlow.</p>
        </div>
        <a href={`mailto:support@tourflow.local?subject=${encodeURIComponent(`Вопрос о туре: ${t.title}`)}`}>
          Связаться с командой <span aria-hidden="true">↗</span>
        </a>
      </section>
    </div>
  );
}
