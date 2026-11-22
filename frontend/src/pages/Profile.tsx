import { FormEvent, useEffect, useState } from 'react';
import { api, fieldError } from '../api';
import { useAuth } from '../auth';
import { PROFILE_LABEL } from '../format';
import { Alert, ErrorBox, Field, Page } from '../components/ui';

export default function Profile() {
  const { has } = useAuth();
  const [f, setF] = useState<any>({ firstName: '', lastName: '', phone: '', budget: '', classLevel: '', interests: '', profile: 'BALANCE' });
  const [email, setEmail] = useState('');
  const [error, setError] = useState<unknown>(null);
  const [saved, setSaved] = useState(false);

  useEffect(() => { api('/profile').then((p) => { setEmail(p.email); setF({ ...p, budget: p.budget ?? '', classLevel: p.classLevel ?? '', firstName: p.firstName ?? '', lastName: p.lastName ?? '', phone: p.phone ?? '', interests: p.interests ?? '' }); }).catch(setError); }, []);
  const set = (k: string) => (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement | HTMLTextAreaElement>) => setF({ ...f, [k]: e.target.value });

  async function save(e: FormEvent) {
    e.preventDefault(); setError(null); setSaved(false);
    try {
      await api('/profile', { method: 'PUT', body: { ...f, budget: f.budget === '' ? null : Number(f.budget), classLevel: f.classLevel === '' ? null : Number(f.classLevel) } });
      setSaved(true);
    } catch (err) { setError(err); }
  }
  return (
    <Page title="Профиль" lead={email}>
      <form className="card stack" style={{ maxWidth: 620 }} onSubmit={save}>
        <ErrorBox error={error} />
        {saved && <Alert kind="ok">Сохранено</Alert>}
        <div className="form-grid">
          <Field label="Имя" error={fieldError(error, 'firstName')}><input value={f.firstName} onChange={set('firstName')} /></Field>
          <Field label="Фамилия" error={fieldError(error, 'lastName')}><input value={f.lastName} onChange={set('lastName')} /></Field>
          <Field label="Телефон" error={fieldError(error, 'phone')}><input value={f.phone} onChange={set('phone')} /></Field>
        </div>
        <h3>Предпочтения {has('TOUR_MANAGER') ? '(профиль подбора по умолчанию для новых туров)' : ''}</h3>
        <div className="form-grid">
          <Field label="Бюджет, BYN" error={fieldError(error, 'budget')}><input type="number" min={0} value={f.budget} onChange={set('budget')} /></Field>
          <Field label="Класс размещения" error={fieldError(error, 'classLevel')}>
            <select value={f.classLevel} onChange={set('classLevel')}><option value="">Не важно</option>{[1, 2, 3, 4, 5].map((n) => <option key={n} value={n}>{n} ★</option>)}</select>
          </Field>
          <Field label="Профиль подбора"><select value={f.profile} onChange={set('profile')}>{Object.entries(PROFILE_LABEL).map(([k, v]) => <option key={k} value={k}>{v}</option>)}</select></Field>
        </div>
        <Field label="Интересы" error={fieldError(error, 'interests')}><textarea rows={2} maxLength={500} value={f.interests} onChange={set('interests')} /></Field>
        <button>Сохранить</button>
      </form>
    </Page>
  );
}
