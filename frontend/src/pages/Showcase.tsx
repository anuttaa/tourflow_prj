import { useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../api';
import { fmtIsoDate, money } from '../format';
import { destinationImage } from '../images';
import { ErrorBox, Pager, useLoad } from '../components/ui';

interface PublicTour {
  id: number;
  title: string;
  destinationName: string;
  startDate: string;
  endDate: string;
  pax: number;
  totalPrice: number;
  perPerson: number;
}

interface PageRes {
  content: PublicTour[];
  totalElements: number;
  page: number;
  size: number;
}

const DESTINATIONS = [
  { name: 'Прага и Богемия', region: 'Чехия · города и история', image: '/tour-prague-painting.png', position: 'prague' },
  { name: 'Тосканские холмы', region: 'Италия · культура и природа', image: '/tour-tuscany-painting.png', position: 'tuscany' },
  { name: 'Доломитовые Альпы', region: 'Италия · горные маршруты', image: '/tour-dolomites-painting.png', position: 'dolomites' },
];

export default function Showcase() {
  const [q, setQ] = useState('');
  const [maxPrice, setMax] = useState('');
  const [sort, setSort] = useState('start,asc');
  const [page, setPage] = useState(0);
  const [applied, setApplied] = useState({ q: '', maxPrice: '', sort: 'start,asc' });

  const { data, error, loading } = useLoad(
    () => api<PageRes>('/public/tours', { query: { ...applied, page, size: 9 } }),
    [applied, page]
  );

  function submit(e: React.FormEvent) {
    e.preventDefault();
    setPage(0);
    setApplied({ q, maxPrice, sort });
    document.getElementById('tours')?.scrollIntoView({ behavior: 'smooth' });
  }

  return (
    <div className="romantic-design">
      <section className="hero-layout" id="top" aria-labelledby="hero-title">
        <div className="hero-copy">
          <span className="eyebrow">Идеи для поездок, которые запомнятся</span>
          <h1 id="hero-title">Откройте<br /><em>новые места.</em></h1>
          <p>Туры по городам, историческим местам и природным уголкам — выбирайте впечатления в своём ритме.</p>
          <a className="hero-link" href="#destinations">Исследовать направления <span aria-hidden="true">↗</span></a>
          <span className="hero-index">Каталог маршрутов&nbsp; / &nbsp;от идеи до поездки</span>
        </div>
        <div className="hero-artwork" aria-label="Романтический европейский пейзаж">
          <img
            src="/tourflow-romantic-landscape.png"
            alt="Романтическая масляная картина: путешественник смотрит на горную долину и исторический город в утреннем свете"
          />
          <div className="stone-column" aria-hidden="true">
            <span className="column-capital" /><span className="column-shaft" /><span className="column-base" />
          </div>
          <div className="art-caption"><span>Долина и дальний город</span><span>Оригинальный пейзаж · TourFlow</span></div>
        </div>
      </section>

      <section className="search-console" aria-label="Подбор тура">
        <div className="console-heading"><span>Найдите путешествие для себя</span><span className="console-rule" aria-hidden="true" /></div>
        <form className="search-fields" onSubmit={submit}>
          <label>
            <span>Направление</span>
            <input value={q} onChange={(e) => setQ(e.target.value)} placeholder="Страна, город или регион" />
          </label>
          <label>
            <span>Бюджет до, BYN / чел.</span>
            <input type="number" min={0} value={maxPrice} onChange={(e) => setMax(e.target.value)} placeholder="Любая цена" />
          </label>
          <label>
            <span>Сортировка</span>
            <select value={sort} onChange={(e) => setSort(e.target.value)}>
              <option value="start,asc">Ближайшие даты</option>
              <option value="price,asc">Сначала доступные</option>
              <option value="price,desc">Премиум-программы</option>
              <option value="title,asc">По алфавиту</option>
            </select>
          </label>
          <button type="submit">Подобрать тур <span aria-hidden="true">↗</span></button>
        </form>
      </section>

      <section className="destination-section section-wrap" id="destinations">
        <div className="section-intro">
          <span className="eyebrow">Популярные направления</span>
          <h2>Выберите место<br /><em>для открытия.</em></h2>
          <p>Исторические города, культурные маршруты и природа — найдите свой способ путешествовать.</p>
          <a href="#tours">Смотреть все направления <span aria-hidden="true">↗</span></a>
        </div>
        <div className="destination-gallery">
          {DESTINATIONS.map((item, index) => (
            <a className={`destination-panel destination-${index + 1} ${item.position}`} href="#tours" key={item.name}>
              <img src={item.image} alt={item.name} />
              <div className="destination-caption">
                <small>{item.region}</small>
                <h3>{item.name}</h3>
                <span aria-hidden="true">↗</span>
              </div>
            </a>
          ))}
        </div>
      </section>

      <section className="popular-tours section-wrap" id="tours">
        <div className="tours-heading">
          <span className="eyebrow">Туры с продуманной программой</span>
          <h2>Популярные путешествия</h2>
          <p>Понятные маршруты, продолжительность и стоимость — чтобы легко сравнить варианты.</p>
        </div>
        <div>
          <ErrorBox error={error} />
          {loading && <p className="muted">Загрузка актуальных предложений…</p>}
          {!loading && data && data.content.length === 0 && (
            <p className="muted">Туры по вашему запросу не найдены. Попробуйте изменить направление или бюджет.</p>
          )}
          <div className="tour-list">
            {data?.content.map((t, index) => (
              <Link className="tour-row" to={`/tours/${t.id}`} key={t.id}>
                <span className="tour-number">0{index + 1}</span>
                <img src={destinationImage(t.destinationName)} alt="" />
                <div className="tour-description">
                  <small>{t.destinationName} · {fmtIsoDate(t.startDate)} — {fmtIsoDate(t.endDate)} · {t.pax} чел.</small>
                  <h3>{t.title}</h3>
                </div>
                <b>{money(t.perPerson)}</b>
                <span className="tour-arrow" aria-hidden="true">↗</span>
              </Link>
            ))}
          </div>
          {data && <Pager page={page} total={data.totalElements} size={data.size} onPage={setPage} />}
        </div>
      </section>

      <section className="how-section section-wrap" aria-labelledby="how-title">
        <div className="how-heading">
          <span className="eyebrow">Просто начать</span>
          <h2 id="how-title">От идеи до маршрута</h2>
          <p>Три понятных шага — и вы знаете, что делать дальше.</p>
        </div>
        <div className="steps-list">
          <article><span>01</span><div><h3>Выберите направление</h3><p>Укажите интересы, даты и удобный формат поездки.</p></div></article>
          <article><span>02</span><div><h3>Сравните программы</h3><p>Посмотрите продолжительность тура, включённые услуги и стоимость.</p></div></article>
          <article><span>03</span><div><h3>Оставьте заявку</h3><p>Менеджер уточнит детали и поможет подготовиться к поездке.</p></div></article>
        </div>
      </section>

      <section className="about-section section-wrap" id="about">
        <div className="about-image">
          <img src="/concept-impressionism.png" alt="Садовая дорожка ведёт к исторической усадьбе" />
          <span>Разные страны · один удобный выбор</span>
        </div>
        <div className="about-copy">
          <span className="eyebrow">О компании</span>
          <h2>Помогаем выбрать<br /><em>путешествие по душе.</em></h2>
          <p>TourFlow собирает в одном месте культурные, экскурсионные и природные маршруты. Сравнивайте программы, стоимость и длительность, а затем оставляйте заявку на удобный вариант.</p>
          <a href="#contact">Подробнее о сервисе <span aria-hidden="true">↗</span></a>
        </div>
      </section>

      <section className="contact-section section-wrap" id="contact">
        <div>
          <span className="eyebrow">Поддержка путешественников</span>
          <h2>Поможем с выбором<br /><em>и подготовкой.</em></h2>
          <p>Напишите команде TourFlow, если нужна помощь с программой или условиями тура.</p>
        </div>
        <a href="mailto:support@tourflow.local">Связаться с нами <span aria-hidden="true">↗</span></a>
      </section>
    </div>
  );
}
