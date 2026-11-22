import { describe, expect, it } from 'vitest';
import { daysBetween, money, utcToLocal, zonedToUtc } from './format';

describe('format', () => {
  it('round-trips local time through UTC for a zone with offset', () => {
    const iso = zonedToUtc('2026-12-11T10:00', 'Europe/Istanbul');
    expect(iso).toBe('2026-12-11T07:00:00.000Z');
    expect(utcToLocal(iso, 'Europe/Istanbul')).toBe('2026-12-11T10:00');
  });
  it('lists inclusive days', () => {
    expect(daysBetween('2026-12-10', '2026-12-13')).toHaveLength(4);
  });
  it('formats money with currency', () => {
    expect(money(12.5)).toContain('BYN');
    expect(money(null)).toBe('—');
  });
});
