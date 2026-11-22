import { useState, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { api } from '../api';
import { money } from '../format';

interface TravelerInput {
  fullName: string;
  birthDate: string;
  docNumber: string;
  citizenship: string;
}

export default function TravelerForm() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();

  const [tour, setTour] = useState<any>(null);
  const [loading, setLoading] = useState(true);
  const [pax, setPax] = useState(2);
  const [travelers, setTravelers] = useState<TravelerInput[]>([
    { fullName: '', birthDate: '', docNumber: '', citizenship: 'РБ' },
    { fullName: '', birthDate: '', docNumber: '', citizenship: 'РБ' },
  ]);
  const [comment, setComment] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    setLoading(true);
    api(`/tours/${id}`)
      .then((t) => setTour(t))
      .catch((err) => setError(err.message || 'Ошибка загрузки тура'))
      .finally(() => setLoading(false));
  }, [id]);

  function handlePaxChange(newPax: number) {
    const count = Math.max(1, Math.min(20, newPax));
    setPax(count);
    setTravelers((prev) => {
      const next = [...prev];
      while (next.length < count) {
        next.push({ fullName: '', birthDate: '', docNumber: '', citizenship: 'РБ' });
      }
      return next.slice(0, count);
    });
  }

  function updateTraveler(index: number, field: keyof TravelerInput, value: string) {
    setTravelers((prev) =>
      prev.map((t, i) => (i === index ? { ...t, [field]: value } : t))
    );
  }

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);

    // Validation
    for (let i = 0; i < travelers.length; i++) {
      if (!travelers[i].fullName.trim()) {
        setError(`Пожалуйста, укажите ФИО для туриста №${i + 1}`);
        return;
      }
    }

    setSubmitting(true);
    try {
      const payload = {
        tourId: Number(id),
        pax,
        travelers: travelers.map((t) => ({
          fullName: t.fullName.trim(),
          birthDate: t.birthDate || null,
          docNumber: t.docNumber.trim() || null,
          citizenship: t.citizenship.trim() || null,
        })),
        comment: comment.trim() || undefined,
      };

      const res = await api('/bookings', {
        method: 'POST',
        body: payload,
      });

      // Redirect to the newly created voucher/booking page
      if (res && res.id) {
        navigate(`/bookings/${res.id}`);
      } else {
        navigate('/my-bookings');
      }
    } catch (err: any) {
      setError(err.message || 'Не удалось оформить бронирование');
    } finally {
      setSubmitting(false);
    }
  }

  if (loading) {
    return (
      <div className="traveler-form-page">
        <p className="muted">Загрузка информации о туре...</p>
      </div>
    );
  }

  const pricePerPerson = tour?.priceTotal || tour?.basePrice || 0;
  const estimatedTotal = pricePerPerson * pax;

  return (
    <div className="traveler-form-page">
      <div className="traveler-form-wrap">
        <div className="traveler-form-header">
          <button type="button" className="traveler-form-back" onClick={() => navigate(-1)}>
            ← Вернуться к туру
          </button>
          <h1>Оформление поездки и данные туристов</h1>
          {tour && (
            <div className="traveler-form-tour-name">
              <strong>{tour.title}</strong> · {tour.destinationName || tour.destination} ·{' '}
              {money(pricePerPerson)} / чел.
            </div>
          )}
        </div>

        <form onSubmit={handleSubmit}>
          {/* Pax counter */}
          <div className="traveler-form-section">
            <h2 className="traveler-form-section-title">Количество путешественников</h2>
            <div className="traveler-form-pax-row">
              <button
                type="button"
                className="traveler-pax-btn"
                onClick={() => handlePaxChange(pax - 1)}
                disabled={pax <= 1}
              >
                −
              </button>
              <span className="traveler-pax-count">{pax}</span>
              <button
                type="button"
                className="traveler-pax-btn"
                onClick={() => handlePaxChange(pax + 1)}
              >
                +
              </button>
              <span className="traveler-pax-label">чел. (ориентировочно: {money(estimatedTotal)})</span>
            </div>
          </div>

          {/* Traveler cards */}
          {travelers.map((t, index) => (
            <div key={index} className="traveler-form-section traveler-card">
              <h2 className="traveler-form-section-title">
                Турист №{index + 1} {index === 0 && <span className="muted">(Основной заказчик)</span>}
              </h2>
              <div className="traveler-form-grid">
                <div className="traveler-form-field traveler-form-field--wide">
                  <label>
                    Фамилия, Имя и Отчество <span className="required">*</span>
                  </label>
                  <input
                    type="text"
                    required
                    placeholder="Например: Иванов Иван Иванович"
                    value={t.fullName}
                    onChange={(e) => updateTraveler(index, 'fullName', e.target.value)}
                  />
                </div>

                <div className="traveler-form-field">
                  <label>Дата рождения</label>
                  <input
                    type="date"
                    value={t.birthDate}
                    onChange={(e) => updateTraveler(index, 'birthDate', e.target.value)}
                  />
                </div>

                <div className="traveler-form-field">
                  <label>Гражданство</label>
                  <input
                    type="text"
                    placeholder="РБ, РФ и т.д."
                    value={t.citizenship}
                    onChange={(e) => updateTraveler(index, 'citizenship', e.target.value)}
                  />
                </div>

                <div className="traveler-form-field traveler-form-field--wide">
                  <label>Номер документа (паспорт / ID-карта)</label>
                  <input
                    type="text"
                    placeholder="Например: MP1234567"
                    value={t.docNumber}
                    onChange={(e) => updateTraveler(index, 'docNumber', e.target.value)}
                  />
                  <small className="muted" style={{ fontSize: '0.78rem', marginTop: '0.2rem' }}>
                    Документы отправляются на проверку туроператором (статус документов: На проверке).
                  </small>
                </div>
              </div>
            </div>
          ))}

          {/* Comment */}
          <div className="traveler-form-section">
            <h2 className="traveler-form-section-title">Пожелания к поездке</h2>
            <div className="traveler-form-field traveler-form-field--wide">
              <textarea
                rows={3}
                placeholder="Укажите особые пожелания, тип размещения или вопросы..."
                value={comment}
                onChange={(e) => setComment(e.target.value)}
                style={{
                  padding: '0.6rem 0.9rem',
                  border: '1.5px solid var(--line)',
                  borderRadius: 'var(--radius)',
                  fontFamily: 'inherit',
                  fontSize: '0.95rem',
                }}
              />
            </div>
          </div>

          {error && <div className="traveler-form-error">{error}</div>}

          <div className="traveler-form-actions">
            <button
              type="button"
              className="traveler-btn-cancel"
              onClick={() => navigate(-1)}
            >
              Отмена
            </button>
            <button
              type="submit"
              className="traveler-btn-submit"
              disabled={submitting}
            >
              {submitting ? 'Оформление заявки...' : `Забронировать тур (${money(estimatedTotal)})`}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
