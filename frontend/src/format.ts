export const TYPE_LABEL: Record<string, string> = {
  FLIGHT: 'Авиаперелёт', TRAIN: 'Поезд', BUS: 'Автобус', HOTEL: 'Проживание', TRANSFER: 'Трансфер', EXCURSION: 'Экскурсия', INSURANCE: 'Страховка', MEAL: 'Питание',
};
export const STATUS_LABEL: Record<string, string> = {
  DRAFT: 'Черновик', VALIDATED: 'Проверен', PUBLISHED: 'Опубликован', ARCHIVED: 'Архив',
  PENDING: 'Ожидает', CONFIRMED: 'Подтверждена', REJECTED: 'Отклонена', EXPIRED: 'Истекла', PAID: 'Оплачена', COMPLETED: 'Завершена', CANCELLED: 'Отменена',
  ACTIVE: 'Активен', BLOCKED: 'Заблокирован',
};
export const PROFILE_LABEL: Record<string, string> = { ECONOMY: 'Эконом', BALANCE: 'Баланс', COMFORT: 'Комфорт' };

export const money = (v: number | string | null | undefined) =>
  v === null || v === undefined ? '—' : new Intl.NumberFormat('ru-RU', { minimumFractionDigits: 2, maximumFractionDigits: 2 }).format(Number(v)) + ' BYN';

function parts(ms: number, tz: string) {
  const dtf = new Intl.DateTimeFormat('en-US', { timeZone: tz, hourCycle: 'h23', year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', second: '2-digit' });
  return Object.fromEntries(dtf.formatToParts(new Date(ms)).map((x) => [x.type, x.value])) as Record<string, string>;
}

function offsetMs(ms: number, tz: string) {
  const p = parts(ms, tz);
  return Date.UTC(+p.year, +p.month - 1, +p.day, +p.hour, +p.minute, +p.second) - ms;
}

/** Локальное время «YYYY-MM-DDTHH:mm» в зоне tz → ISO-инстант UTC. */
export function zonedToUtc(local: string, tz: string): string {
  const [d, t] = local.split('T');
  const [y, m, da] = d.split('-').map(Number);
  const [h, mi] = t.split(':').map(Number);
  const wall = Date.UTC(y, m - 1, da, h, mi);
  let utc = wall;
  for (let i = 0; i < 3; i++) utc = wall - offsetMs(utc, tz);
  return new Date(utc).toISOString();
}

/** ISO-инстант → «YYYY-MM-DDTHH:mm» в зоне tz (для input datetime-local). */
export function utcToLocal(iso: string, tz: string): string {
  const p = parts(new Date(iso).getTime(), tz);
  return `${p.year}-${p.month}-${p.day}T${p.hour}:${p.minute}`;
}

export const fmtTime = (iso: string, tz: string) => utcToLocal(iso, tz).slice(11);
export const fmtDate = (iso: string, tz: string) => { const l = utcToLocal(iso, tz); return `${l.slice(8, 10)}.${l.slice(5, 7)}`; };
export const fmtDateTime = (iso: string, tz: string) => `${fmtDate(iso, tz)} ${fmtTime(iso, tz)}`;
export const fmtIsoDate = (d: string) => `${d.slice(8, 10)}.${d.slice(5, 7)}.${d.slice(0, 4)}`;

export function daysBetween(from: string, to: string): string[] {
  const out: string[] = [];
  for (let d = new Date(from + 'T00:00:00Z'); d <= new Date(to + 'T00:00:00Z'); d = new Date(d.getTime() + 86400000)) out.push(d.toISOString().slice(0, 10));
  return out;
}
