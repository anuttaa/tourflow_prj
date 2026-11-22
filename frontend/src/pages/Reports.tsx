import { useState } from 'react';
import {
  BarChart,
  Bar,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  Legend,
  ResponsiveContainer,
  PieChart,
  Pie,
  Cell,
} from 'recharts';
import { api } from '../api';
import { useAuth } from '../auth';
import { money, STATUS_LABEL, TYPE_LABEL } from '../format';
import { ErrorBox, Page, useLoad } from '../components/ui';

type Rows = Record<string, any>[];

const CHART_COLORS = ['#0f766e', '#2563eb', '#f59e0b', '#10b981', '#8b5cf6', '#ef4444', '#ec4899', '#06b6d4'];

function Table({ rows, cols }: { rows?: Rows; cols: [string, string, ((v: any) => string)?][] }) {
  if (!rows) return null;
  return (
    <div className="card table-wrap">
      <table>
        <thead>
          <tr>
            {cols.map(([, h]) => (
              <th key={h}>{h}</th>
            ))}
          </tr>
        </thead>
        <tbody>
          {rows.map((r, i) => (
            <tr key={i}>
              {cols.map(([k, h, f]) => (
                <td key={h}>{f ? f(r[k]) : String(r[k] ?? '—')}</td>
              ))}
            </tr>
          ))}
          {rows.length === 0 && (
            <tr>
              <td colSpan={cols.length} className="muted">
                Нет данных за период
              </td>
            </tr>
          )}
        </tbody>
      </table>
    </div>
  );
}

export default function Reports() {
  const { has } = useAuth();
  const [range, setRange] = useState({ from: '', to: '' });
  const [applied, setApplied] = useState(range);
  const [showCharts, setShowCharts] = useState(true);

  const mgr = useLoad(
    () => api<{ managerLoad: Rows; autopick: Rows; toursByStatus: Rows }>('/reports/manager', { query: applied }),
    [applied]
  );
  const adm = useLoad(
    () => (has('ADMIN') ? api<{ topOffers: Rows }>('/reports/admin/planning') : Promise.resolve(null)),
    []
  );
  const bk = useLoad(
    () =>
      has('ADMIN')
        ? api<{ revenue: Rows; statuses: Rows; conversion: Record<string, any> }>('/reports/admin/bookings', {
            query: applied,
          })
        : Promise.resolve(null),
    [applied]
  );

  // Подготовка данных для графиков
  const statusChartData = (bk.data?.statuses || []).map((s) => ({
    name: STATUS_LABEL[s.status] || s.status,
    bookings: Number(s.bookings) || 0,
    amount: Number(s.amount) || 0,
  }));

  const revenueChartData = (bk.data?.revenue || []).map((r) => ({
    destination: `${r.destination_name || '—'} (${r.month || ''})`,
    revenue: Number(r.net_revenue) || 0,
    bookings: Number(r.bookings) || 0,
  }));

  const managerChartData = (mgr.data?.managerLoad || []).map((m) => ({
    manager: String(m.manager_email || '—').split('@')[0],
    tours: Number(m.tours) || 0,
    versions: Number(m.versions) || 0,
    accepted: Number(m.accepted_alternatives) || 0,
  }));

  const tourStatusData = (mgr.data?.toursByStatus || []).map((t) => ({
    name: STATUS_LABEL[t.status] || t.status,
    value: Number(t.tours) || 0,
  }));

  return (
    <Page
      title="Аналитические отчёты"
      lead="Нагрузка менеджеров, эффективность автоподбора, выручка по направлениям и конверсия заявок."
    >
      <div className="row between" style={{ alignItems: 'center', marginBottom: '1.25rem', flexWrap: 'wrap', gap: '1rem' }}>
        <form
          className="filters"
          onSubmit={(e) => {
            e.preventDefault();
            setApplied(range);
          }}
          style={{ margin: 0 }}
        >
          <label className="field">
            С<input type="date" value={range.from} onChange={(e) => setRange({ ...range, from: e.target.value })} />
          </label>
          <label className="field">
            По<input type="date" value={range.to} onChange={(e) => setRange({ ...range, to: e.target.value })} />
          </label>
          <button>Применить фильтр</button>
        </form>

        <button
          type="button"
          className="ghost"
          onClick={() => setShowCharts((v) => !v)}
          style={{ whiteSpace: 'nowrap' }}
        >
          {showCharts ? 'Скрыть графики' : 'Показать графики'}
        </button>
      </div>

      <ErrorBox error={mgr.error ?? adm.error ?? bk.error} />

      {/* ── Блок графиков для аналитики ── */}
      {showCharts && (
        <div className="reports-charts">
          {/* График 1: Выручка по направлениям (для админа) */}
          {has('ADMIN') && revenueChartData.length > 0 && (
            <div className="reports-chart-card" style={{ gridColumn: '1 / -1' }}>
              <div className="reports-chart-title">Выручка по направлениям (₽)</div>
              <div style={{ width: '100%', height: 280 }}>
                <ResponsiveContainer width="100%" height="100%">
                  <BarChart data={revenueChartData} margin={{ top: 10, right: 20, left: 20, bottom: 40 }}>
                    <CartesianGrid strokeDasharray="3 3" vertical={false} stroke="#f0f0f0" />
                    <XAxis
                      dataKey="destination"
                      interval={0}
                      angle={-15}
                      textAnchor="end"
                      height={50}
                      tick={{ fontSize: 11 }}
                    />
                    <YAxis tickFormatter={(val) => `${(val / 1000).toFixed(0)}k`} />
                    <Tooltip formatter={(value: any) => [`${Number(value).toLocaleString('ru-RU')} ₽`, 'Выручка']} />
                    <Legend />
                    <Bar dataKey="revenue" name="Выручка (₽)" fill="#0f766e" radius={[4, 4, 0, 0]} />
                  </BarChart>
                </ResponsiveContainer>
              </div>
            </div>
          )}

          {/* График 2: Заявки по статусам (PieChart) */}
          {has('ADMIN') && statusChartData.length > 0 && (
            <div className="reports-chart-card">
              <div className="reports-chart-title">Распределение заявок по статусам</div>
              <div style={{ width: '100%', height: 240 }}>
                <ResponsiveContainer width="100%" height="100%">
                  <PieChart>
                    <Pie
                      data={statusChartData}
                      dataKey="bookings"
                      nameKey="name"
                      cx="50%"
                      cy="50%"
                      outerRadius={80}
                      innerRadius={45}
                      paddingAngle={4}
                      label={({ name, percent }) => `${name} ${((percent ?? 0) * 100).toFixed(0)}%`}
                    >
                      {statusChartData.map((_, index) => (
                        <Cell key={`cell-${index}`} fill={CHART_COLORS[index % CHART_COLORS.length]} />
                      ))}
                    </Pie>
                    <Tooltip />
                  </PieChart>
                </ResponsiveContainer>
              </div>
            </div>
          )}

          {/* График 3: Статусы туров */}
          {tourStatusData.length > 0 && (
            <div className="reports-chart-card">
              <div className="reports-chart-title">Туры по статусам</div>
              <div style={{ width: '100%', height: 240 }}>
                <ResponsiveContainer width="100%" height="100%">
                  <PieChart>
                    <Pie
                      data={tourStatusData}
                      dataKey="value"
                      nameKey="name"
                      cx="50%"
                      cy="50%"
                      outerRadius={80}
                      innerRadius={45}
                      paddingAngle={4}
                      label={({ name, value }) => `${name}: ${value}`}
                    >
                      {tourStatusData.map((_, index) => (
                        <Cell key={`cell-${index}`} fill={CHART_COLORS[(index + 2) % CHART_COLORS.length]} />
                      ))}
                    </Pie>
                    <Tooltip />
                  </PieChart>
                </ResponsiveContainer>
              </div>
            </div>
          )}

          {/* График 4: Нагрузка менеджеров */}
          {managerChartData.length > 0 && (
            <div className="reports-chart-card" style={{ gridColumn: has('ADMIN') ? '1 / -1' : undefined }}>
              <div className="reports-chart-title">Нагрузка менеджеров (количество туров и версий)</div>
              <div style={{ width: '100%', height: 260 }}>
                <ResponsiveContainer width="100%" height="100%">
                  <BarChart data={managerChartData} margin={{ top: 10, right: 20, left: 10, bottom: 20 }}>
                    <CartesianGrid strokeDasharray="3 3" vertical={false} stroke="#f0f0f0" />
                    <XAxis dataKey="manager" />
                    <YAxis allowDecimals={false} />
                    <Tooltip />
                    <Legend />
                    <Bar dataKey="tours" name="Туров" fill="#0f766e" radius={[4, 4, 0, 0]} />
                    <Bar dataKey="versions" name="Версий" fill="#2563eb" radius={[4, 4, 0, 0]} />
                    <Bar dataKey="accepted" name="Принято альт." fill="#10b981" radius={[4, 4, 0, 0]} />
                  </BarChart>
                </ResponsiveContainer>
              </div>
            </div>
          )}
        </div>
      )}

      {/* ── Таблицы с подробными данными ── */}
      <div className="stack">
        <h2>Нагрузка менеджеров</h2>
        <Table
          rows={mgr.data?.managerLoad}
          cols={[
            ['manager_email', 'Менеджер'],
            ['tours', 'Туров'],
            ['versions', 'Версий'],
            ['validation_errors', 'Ошибок в проверках'],
            ['accepted_alternatives', 'Принятых альтернатив'],
          ]}
        />

        <h2>Эффективность автоподбора</h2>
        <Table
          rows={mgr.data?.autopick}
          cols={[
            ['type', 'Тип сегмента', (v) => TYPE_LABEL[v] ?? v],
            ['profile', 'Профиль'],
            ['requests', 'Запросов'],
            ['accepted', 'Принято'],
            ['acceptance_pct', 'Доля принятых, %'],
          ]}
        />

        <h2>Туры по статусам</h2>
        <Table
          rows={mgr.data?.toursByStatus}
          cols={[
            ['status', 'Статус', (v) => STATUS_LABEL[v] ?? v],
            ['tours', 'Туров'],
          ]}
        />

        {has('ADMIN') && (
          <>
            <h2>Выручка по направлениям (нетто, с учётом возвратов)</h2>
            <Table
              rows={bk.data?.revenue}
              cols={[
                ['destination_name', 'Направление'],
                ['month', 'Месяц'],
                ['bookings', 'Заявок'],
                ['net_revenue', 'Выручка', money],
              ]}
            />

            <h2>Заявки по статусам</h2>
            <Table
              rows={bk.data?.statuses}
              cols={[
                ['status', 'Статус', (v) => STATUS_LABEL[v] ?? v],
                ['bookings', 'Заявок'],
                ['amount', 'Сумма', money],
              ]}
            />

            {bk.data && (
              <div className="card" style={{ padding: '1.25rem 1.5rem', background: 'var(--paper)', border: '1px solid var(--line)' }}>
                Конверсия заявок в подтверждённые:{' '}
                <b style={{ color: 'var(--teal-dark)', fontSize: '1.1rem' }}>
                  {bk.data.conversion.conversion_pct ?? 0}%
                </b>{' '}
                ({bk.data.conversion.converted} из {bk.data.conversion.created})
              </div>
            )}

            <h2>Топ опций в опубликованных турах</h2>
            <Table
              rows={adm.data?.topOffers}
              cols={[
                ['title', 'Опция'],
                ['type', 'Тип', (v) => TYPE_LABEL[v] ?? v],
                ['uses', 'Использований'],
              ]}
            />
          </>
        )}
      </div>
    </Page>
  );
}
