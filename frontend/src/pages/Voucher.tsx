import { useRef, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { api } from '../api';
import { useAuth } from '../auth';
import { useLoad } from '../components/ui';

// ── SVG icons ─────────────────────────────────────────────────────────────────
const IcCalendar = () => (
  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
    <rect x="3" y="4" width="18" height="18" rx="2"/><line x1="16" y1="2" x2="16" y2="6"/>
    <line x1="8" y1="2" x2="8" y2="6"/><line x1="3" y1="10" x2="21" y2="10"/>
  </svg>
);
const IcUsers = () => (
  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
    <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/>
    <circle cx="9" cy="7" r="4"/>
    <path d="M23 21v-2a4 4 0 0 0-3-3.87"/>
    <path d="M16 3.13a4 4 0 0 1 0 7.75"/>
  </svg>
);
const IcMapPin = () => (
  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
    <path d="M21 10c0 7-9 13-9 13s-9-6-9-13a9 9 0 0 1 18 0z"/>
    <circle cx="12" cy="10" r="3"/>
  </svg>
);
const IcCard = () => (
  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
    <rect x="1" y="4" width="22" height="16" rx="2"/>
    <line x1="1" y1="10" x2="23" y2="10"/>
  </svg>
);
const IcFile = () => (
  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
    <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/>
    <polyline points="14 2 14 8 20 8"/>
    <line x1="16" y1="13" x2="8" y2="13"/>
    <line x1="16" y1="17" x2="8" y2="17"/>
  </svg>
);
const IcMail = () => (
  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
    <path d="M4 4h16c1.1 0 2 .9 2 2v12c0 1.1-.9 2-2 2H4c-1.1 0-2-.9-2-2V6c0-1.1.9-2 2-2z"/>
    <polyline points="22,6 12,13 2,6"/>
  </svg>
);
const IcDownload = () => (
  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
    <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/>
    <polyline points="7 10 12 15 17 10"/>
    <line x1="12" y1="15" x2="12" y2="3"/>
  </svg>
);
const IcChevDown = () => (
  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
    <polyline points="6 9 12 15 18 9"/>
  </svg>
);
const IcChevUp = () => (
  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
    <polyline points="18 15 12 9 6 15"/>
  </svg>
);
const IcShield = () => (
  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
    <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/>
  </svg>
);
const IcInfo = () => (
  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
    <circle cx="12" cy="12" r="10"/>
    <line x1="12" y1="8" x2="12" y2="12"/>
    <line x1="12" y1="16" x2="12.01" y2="16"/>
  </svg>
);
const IcCheck = () => (
  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
    <polyline points="20 6 9 17 4 12"/>
  </svg>
);
const IcPhone = () => (
  <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
    <path d="M22 16.92v3a2 2 0 0 1-2.18 2 19.79 19.79 0 0 1-8.63-3.07A19.5 19.5 0 0 1 4.69 12 19.79 19.79 0 0 1 1.61 3.44 2 2 0 0 1 3.57 1h3a2 2 0 0 1 2 1.72c.127.96.361 1.903.7 2.81a2 2 0 0 1-.45 2.11L7.91 8.54a16 16 0 0 0 6 6l.91-.91a2 2 0 0 1 2.11-.45c.907.339 1.85.573 2.81.7A2 2 0 0 1 22 16.92z"/>
  </svg>
);

// ── Фотографии городов и направлений ──────────────────────────────────────────
const DEST_PHOTOS: Record<string, string> = {
  стамбул: 'https://images.unsplash.com/photo-1524231757912-21f4fe3a7200?auto=format&fit=crop&w=1200&q=80',
  турция: 'https://images.unsplash.com/photo-1541432901042-2d8bd64b4a9b?auto=format&fit=crop&w=1200&q=80',
  барселона: 'https://images.unsplash.com/photo-1583422409516-2895a77efded?auto=format&fit=crop&w=1200&q=80',
  испания: 'https://images.unsplash.com/photo-1543783207-ec64e4d95325?auto=format&fit=crop&w=1200&q=80',
  париж: 'https://images.unsplash.com/photo-1502602898657-3e91760cbb34?auto=format&fit=crop&w=1200&q=80',
  франция: 'https://images.unsplash.com/photo-1509299349698-dd22323b5963?auto=format&fit=crop&w=1200&q=80',
  рим: 'https://images.unsplash.com/photo-1552832230-c0197dd311b5?auto=format&fit=crop&w=1200&q=80',
  италия: 'https://images.unsplash.com/photo-1516483638261-f4dbaf036963?auto=format&fit=crop&w=1200&q=80',
  прага: 'https://images.unsplash.com/photo-1519671482749-fd09be7ccebf?auto=format&fit=crop&w=1200&q=80',
  дубай: 'https://images.unsplash.com/photo-1512453979798-5ea266f8880c?auto=format&fit=crop&w=1200&q=80',
  оаэ: 'https://images.unsplash.com/photo-1518684079-3c830dcef090?auto=format&fit=crop&w=1200&q=80',
  египет: 'https://images.unsplash.com/photo-1539650116574-8efeb43e2750?auto=format&fit=crop&w=1200&q=80',
  греция: 'https://images.unsplash.com/photo-1570077188670-e3a8d69ac5ff?auto=format&fit=crop&w=1200&q=80',
  токио: 'https://images.unsplash.com/photo-1503899036084-c55cdd92da26?auto=format&fit=crop&w=1200&q=80',
  минск: 'https://images.unsplash.com/photo-1568084680786-a84f91d1153c?auto=format&fit=crop&w=1200&q=80',
  мальдивы: 'https://images.unsplash.com/photo-1514282401047-d79a71a590e8?auto=format&fit=crop&w=1200&q=80',
  таиланд: 'https://images.unsplash.com/photo-1528181304800-259b08848526?auto=format&fit=crop&w=1200&q=80',
};

function getDestinationPhoto(name: string): string | null {
  const n = (name || '').toLowerCase();
  for (const [key, url] of Object.entries(DEST_PHOTOS)) {
    if (n.includes(key)) return url;
  }
  return null;
}

const STATUS_LABEL: Record<string, string> = {
  PENDING: 'Ожидает оплаты', PAID: 'Оплачено', CANCELLED: 'Отменено', CONFIRMED: 'Подтверждено',
};
const STATUS_COLOR: Record<string, string> = {
  PENDING: '#f59e0b', PAID: '#10b981', CANCELLED: '#ef4444', CONFIRMED: '#3b82f6',
};
const TYPE_LABEL: Record<string, string> = {
  HOTEL: 'Проживание', FLIGHT: 'Перелёт', TRAIN: 'Ж/д билет', BUS: 'Автобус',
  EXCURSION: 'Экскурсии', INSURANCE: 'Страхование', TRANSFER: 'Трансфер', OTHER: 'Прочее',
};

function fmtQty(type: string, qty: number): string {
  const t = (type || '').toUpperCase();
  if (t === 'HOTEL') return qty === 1 ? '1 ночь' : qty < 5 ? `${qty} ночи` : `${qty} ночей`;
  if (t === 'FLIGHT' || t === 'TRAIN' || t === 'BUS')
    return qty === 1 ? '1 место' : qty < 5 ? `${qty} места` : `${qty} мест`;
  if (t === 'INSURANCE') return qty === 1 ? '1 полис' : qty < 5 ? `${qty} полиса` : `${qty} полисов`;
  return `${qty} чел.`;
}

export default function Voucher() {
  const { id } = useParams();
  const navigate = useNavigate();
  const { user } = useAuth();
  const voucherRef = useRef<HTMLDivElement>(null);

  const [paying, setPaying] = useState(false);
  const [downloading, setDownloading] = useState(false);
  const [showProgram, setShowProgram] = useState(false);
  const [showServices, setShowServices] = useState(false);
  const [booking, setBooking] = useState<any>(null);

  const { loading, error } = useLoad(async () => {
    // Вызов api(...) напрямую — работает гарантированно
    const data = await api(`/bookings/${id}`);
    setBooking(data);
  }, [id]);

  async function handlePay() {
    setPaying(true);
    try {
      const updated = await api(`/bookings/${id}/pay`, { method: 'POST' });
      setBooking(updated);
    } catch (e: any) {
      alert('Ошибка оплаты: ' + e.message);
    } finally {
      setPaying(false);
    }
  }

  async function handleDownloadPdf() {
    if (!voucherRef.current) return;
    setDownloading(true);
    try {
      const { default: html2canvas } = await import('html2canvas');
      const jsPDFModule = await import('jspdf');
      const jsPDF = jsPDFModule.default || (jsPDFModule as any).jsPDF;
      const canvas = await html2canvas(voucherRef.current, {
        scale: 2, useCORS: true, backgroundColor: '#ffffff', logging: false,
      });
      const imgData = canvas.toDataURL('image/png');
      const pdf = new jsPDF({ orientation: 'portrait', unit: 'mm', format: 'a4' });
      const pageW = pdf.internal.pageSize.getWidth();
      const pageH = pdf.internal.pageSize.getHeight();
      const imgH = (canvas.height * pageW) / canvas.width;
      let posY = 0;
      pdf.addImage(imgData, 'PNG', 0, posY, pageW, imgH);
      let remaining = imgH - pageH;
      while (remaining > 0) {
        posY -= pageH;
        pdf.addPage();
        pdf.addImage(imgData, 'PNG', 0, posY, pageW, imgH);
        remaining -= pageH;
      }
      pdf.save(`voucher-${booking?.ref || id}.pdf`);
    } catch (e: any) {
      alert('Ошибка генерации PDF: ' + e.message);
    } finally {
      setDownloading(false);
    }
  }

  if (loading) return (
    <div className="vc-loading">
      <div className="vc-spinner"></div>
      <p>Загрузка путёвки...</p>
    </div>
  );
  if (error) return (
    <div className="vc-error">
      <IcInfo />
      <h2>Ошибка загрузки</h2>
      <p>{String(error)}</p>
      <button className="vc-btn-secondary" onClick={() => navigate(-1)}>Назад</button>
    </div>
  );
  if (!booking) return null;

  const b = booking;
  const status: string = b.status || 'PENDING';
  const isPaid = status === 'PAID';
  const isCancelled = status === 'CANCELLED';
  const isOwner = !user || String(b.userRef) === String(user.id) || String(b.userId) === String(user.id);
  const destName: string = b.destinationName || b.destination || b.tourTitle || 'Путешествие';
  const destPhoto = getDestinationPhoto(destName);
  const travelers: any[] = b.travelers || [];
  const items: any[] = b.items || [];

  // Group items by type
  const grouped: Record<string, any[]> = {};
  for (const item of items) {
    const t = item.type || item.offerType || 'OTHER';
    if (!grouped[t]) grouped[t] = [];
    grouped[t].push(item);
  }

  const fmtDate = (d: string | undefined) =>
    d ? new Date(d).toLocaleDateString('ru-RU', { day: 'numeric', month: 'long', year: 'numeric' }) : '—';

  return (
    <div className="vc-page">
      {/* action bar */}
      <div className="vc-actions-bar">
        <button className="vc-btn-secondary" onClick={() => navigate(-1)}>← Назад</button>
        <div className="vc-actions-right">
          {isPaid && (
            <button className="vc-btn-download" onClick={handleDownloadPdf} disabled={downloading}>
              <IcDownload /> {downloading ? 'Генерация...' : 'Скачать PDF'}
            </button>
          )}
          {!isPaid && !isCancelled && isOwner && (
            <button className="vc-btn-pay" onClick={handlePay} disabled={paying}>
              <IcCard /> {paying ? 'Обработка...' : 'Оплатить путёвку'}
            </button>
          )}
        </div>
      </div>

      <div className="vc-wrap" ref={voucherRef}>
        {/* hero with photo */}
        <div
          className="vc-hero"
          style={{
            backgroundImage: destPhoto ? `url(${destPhoto})` : undefined,
            backgroundSize: 'cover',
            backgroundPosition: 'center',
            backgroundColor: '#0f172a',
          }}
        >
          <div className="vc-hero-overlay">
            <div className="vc-hero-badge">Электронная туристическая путёвка</div>
            <h1 className="vc-hero-title">{destName}</h1>
            <div className="vc-hero-ref">Бронирование № {b.ref || b.id}</div>
            <span className="vc-status-chip" style={{
              color: '#ffffff',
              borderColor: STATUS_COLOR[status] || '#6b7280',
              background: STATUS_COLOR[status] || '#6b7280',
            }}>
              {STATUS_LABEL[status] || status}
            </span>
          </div>
        </div>

        {/* info grid */}
        <div className="vc-info-grid">
          <div className="vc-info-card">
            <div className="vc-info-label"><IcCalendar /> Даты поездки</div>
            <div className="vc-info-value">
              {fmtDate(b.startDate || b.departureDate)}
              {(b.endDate || b.returnDate) ? ` — ${fmtDate(b.endDate || b.returnDate)}` : ''}
            </div>
          </div>
          <div className="vc-info-card">
            <div className="vc-info-label"><IcMapPin /> Направление</div>
            <div className="vc-info-value">{destName}</div>
          </div>
          <div className="vc-info-card">
            <div className="vc-info-label"><IcUsers /> Количество туристов</div>
            <div className="vc-info-value">{travelers.length || b.pax || 1} чел.</div>
          </div>
          <div className="vc-info-card">
            <div className="vc-info-label"><IcCard /> Стоимость</div>
            <div className="vc-info-value vc-price">
              {b.totalPrice != null ? `${Number(b.totalPrice).toLocaleString('ru-RU')} ₽` : '—'}
            </div>
          </div>
        </div>

        {/* traveler list */}
        {travelers.length > 0 && (
          <div className="vc-section">
            <div className="vc-section-hd"><IcUsers /><h2>Туристы</h2></div>
            <div className="vc-travelers">
              {travelers.map((t: any, i: number) => (
                <div className="vc-traveler-row" key={i}>
                  <div className="vc-traveler-num">{i + 1}</div>
                  <div className="vc-traveler-info">
                    <div className="vc-traveler-name">{t.firstName} {t.lastName}{t.fullName && !t.firstName ? t.fullName : ''}</div>
                    <div className="vc-traveler-meta">
                      {t.citizenship && <span>Гражданство: {t.citizenship}</span>}
                      {t.docNumber && <span>Документ: {t.docNumber}</span>}
                      {t.birthDate && <span>Дата рождения: {new Date(t.birthDate).toLocaleDateString('ru-RU')}</span>}
                    </div>
                  </div>
                  <div className={`vc-traveler-status ${t.docStatus === 'VERIFIED' ? 'verified' : ''}`}>
                    {t.docStatus === 'VERIFIED' ? <><IcCheck /> Проверено</> : t.docStatus === 'PENDING' || t.docStatus === 'PENDING_VERIFICATION' ? 'На проверке' : t.docStatus || 'На проверке'}
                  </div>
                </div>
              ))}
            </div>
          </div>
        )}

        {/* programme (collapsible) */}
        <div className="vc-section vc-collapsible">
          <button className="vc-collapse-btn" onClick={() => setShowProgram(v => !v)}>
            <div className="vc-collapse-title"><IcFile /><span>Программа поездки</span></div>
            {showProgram ? <IcChevUp /> : <IcChevDown />}
          </button>
          {showProgram && (
            <div className="vc-timeline">
              {b.segments && b.segments.length > 0 ? b.segments.map((seg: any, i: number) => (
                <div className="vc-tl-item" key={i}>
                  <div className="vc-tl-dot"></div>
                  <div className="vc-tl-content">
                    <div className="vc-tl-day">День {seg.position || i + 1}</div>
                    <div className="vc-tl-title">{seg.title || seg.offerTitle || seg.type || '—'}</div>
                    {seg.description && <div className="vc-tl-desc">{seg.description}</div>}
                  </div>
                </div>
              )) : (
                <p className="vc-empty">Детализированная программа доступна в маршрутном листе.</p>
              )}
            </div>
          )}
        </div>

        {/* services (collapsible) */}
        <div className="vc-section vc-collapsible">
          <button className="vc-collapse-btn" onClick={() => setShowServices(v => !v)}>
            <div className="vc-collapse-title"><IcCheck /><span>Включённые услуги</span></div>
            {showServices ? <IcChevUp /> : <IcChevDown />}
          </button>
          {showServices && (
            <div className="vc-services">
              {items.length === 0 && <p className="vc-empty">Список услуг формируется туроператором.</p>}
              {Object.entries(grouped).map(([type, typeItems]) => (
                <div key={type} className="vc-svc-group">
                  <div className="vc-svc-group-label">{TYPE_LABEL[type] || type}</div>
                  {(typeItems as any[]).map((item: any, i: number) => (
                    <div className="vc-svc-row" key={i}>
                      <div className="vc-svc-name">{item.title || item.offerTitle || '—'}</div>
                      <div className="vc-svc-qty">{fmtQty(type, item.unitsPerPax || item.qty || item.quantity || 1)}</div>
                      {item.lineTotal != null && (
                        <div className="vc-svc-price">{Number(item.lineTotal).toLocaleString('ru-RU')} ₽</div>
                      )}
                    </div>
                  ))}
                </div>
              ))}
            </div>
          )}
        </div>

        {/* important info */}
        <div className="vc-section">
          <div className="vc-section-hd"><IcShield /><h2>Важная информация для туриста</h2></div>
          <div className="vc-info-items">
            <div className="vc-info-item">
              <div className="vc-info-item-ic"><IcCheck /></div>
              <div>
                <div className="vc-info-item-title">Паспортный и визовый контроль</div>
                <div className="vc-info-item-desc">Срок действия паспорта должен составлять не менее 6 месяцев с даты возвращения. Для детей до 14 лет обязательно свидетельство о рождении с нотариальным согласием на выезд.</div>
              </div>
            </div>
            <div className="vc-info-item">
              <div className="vc-info-item-ic"><IcInfo /></div>
              <div>
                <div className="vc-info-item-title">Регистрация и багаж</div>
                <div className="vc-info-item-desc">Рекомендуем прибыть в аэропорт за 2,5–3 часа до вылета. Электронная регистрация открывается за 24 часа. Нормы провоза багажа указаны в маршрутной квитанции.</div>
              </div>
            </div>
            <div className="vc-info-item">
              <div className="vc-info-item-ic"><IcCard /></div>
              <div>
                <div className="vc-info-item-title">Условия изменения и отмены</div>
                <div className="vc-info-item-desc">При отмене тура более чем за 14 дней до выезда — возврат 100%. При отмене со стороны туроператора или менеджера средства возвращаются в полном объёме без штрафов.</div>
              </div>
            </div>
            <div className="vc-info-item">
              <div className="vc-info-item-ic"><IcShield /></div>
              <div>
                <div className="vc-info-item-title">Медицинская страховка</div>
                <div className="vc-info-item-desc">Полис действует круглосуточно на всей территории пребывания. В случае необходимости экстренной помощи немедленно свяжитесь с ассистанс-службой по номеру в полисе.</div>
              </div>
            </div>
          </div>
        </div>

        {/* support */}
        <div className="vc-support">
          <div className="vc-support-ic"><IcPhone /></div>
          <div className="vc-support-content">
            <h3>Круглосуточная поддержка клиентов</h3>
            <p>Персональный куратор поездки готов ответить на ваши вопросы в любое время.</p>
            <div className="vc-support-contacts">
              <div className="vc-support-contact"><IcPhone /><span>+7 (495) 000-00-00</span></div>
              <div className="vc-support-contact"><IcMail /><span>support@tourflow.local</span></div>
            </div>
          </div>
          <a
            className="vc-btn-support"
            href={`mailto:annatyreiko@gmail.com?subject=Вопрос по бронированию №${b.ref || b.id}`}
          >
            <IcMail /> Написать в поддержку
          </a>
        </div>

        {/* footer */}
        <div className="vc-footer">
          <div>TourFlow Travel Platform &copy; {new Date().getFullYear()}</div>
          <div>Путёвка № {b.ref || b.id} · {new Date().toLocaleDateString('ru-RU')}</div>
        </div>
      </div>
    </div>
  );
}
