import { Navigate, NavLink, Route, Routes, Link, useLocation, useNavigate } from 'react-router-dom';
import { ReactNode, useEffect, useState } from 'react';
import { useAuth } from './auth';
import Showcase from './pages/Showcase';
import TourDetail from './pages/TourDetail';
import TravelerForm from './pages/TravelerForm';
import { Login, Register, Confirm } from './pages/AuthPages';
import MyBookings from './pages/MyBookings';
import Profile from './pages/Profile';
import Builder from './pages/Builder';
import TourConstructor from './pages/TourConstructor';
import Queue from './pages/Queue';
import Catalog from './pages/Catalog';
import Reports from './pages/Reports';
import Admin from './pages/Admin';
import Voucher from './pages/Voucher';

function Guard({ roles, children }: { roles?: string[]; children: ReactNode }) {
  const { user, ready, has } = useAuth();
  if (!ready) return <p className="muted">Загрузка…</p>;
  if (!user) return <Navigate to="/login" replace />;
  if (roles && !has(...roles)) return <div className="alert error">Недостаточно прав для этого раздела.</div>;
  return <>{children}</>;
}

export default function App() {
  const { user, has, logout } = useAuth();
  const nav = useNavigate();
  const { pathname } = useLocation();
  const staff = has('TOUR_MANAGER', 'ADMIN');

  const [scrolled, setScrolled] = useState(false);
  const [menuOpen, setMenuOpen] = useState(false);

  useEffect(() => {
    const onScroll = () => setScrolled(window.scrollY > 24);
    onScroll();
    window.addEventListener('scroll', onScroll, { passive: true });
    return () => window.removeEventListener('scroll', onScroll);
  }, []);

  useEffect(() => setMenuOpen(false), [pathname]);

  // Публичные страницы (витрина и карточка тура) занимают всю ширину
  const wide = /^(\/|\/tours\/[^/]+)$/.test(pathname);
  const displayName = user?.firstName || user?.email || '';
  const initial = displayName.charAt(0).toUpperCase() || 'П';

  return (
    <div className="tourflow shell">
      <header className={`site-header ${scrolled ? 'is-scrolled' : ''}`}>
        <Link to="/" className="wordmark" aria-label="TourFlow — на главную">TourFlow<span>.</span></Link>
        <nav className={`desktop-nav ${menuOpen ? 'mobile-open' : ''}`} aria-label="Основная навигация">
          <NavLink to="/" end>Витрина</NavLink>
          {has('CLIENT') && <NavLink to="/my-bookings">Мои заявки</NavLink>}
          {staff && <NavLink to="/builder">Конструктор</NavLink>}
          {staff && <NavLink to="/catalog">Каталог</NavLink>}
          {staff && <NavLink to="/queue">Очередь заявок</NavLink>}
          {staff && <NavLink to="/reports">Отчёты</NavLink>}
          {has('ADMIN') && <NavLink to="/admin">Администрирование</NavLink>}
          {user ? (
            <details className="profile-menu">
              <summary>
                <span className="profile-dot" aria-hidden="true">{initial}</span>
                <span>{displayName}</span>
                <span className="profile-chevron" aria-hidden="true">⌄</span>
              </summary>
              <div className="profile-popover">
                <Link to="/profile">Мой профиль</Link>
                <button type="button" onClick={async () => { await logout(); nav('/'); }}>Выйти</button>
              </div>
            </details>
          ) : (
            <>
              <NavLink to="/login">Войти</NavLink>
              <NavLink to="/register">Регистрация</NavLink>
            </>
          )}
        </nav>
        <div className="header-actions">
          <button
            className="menu-toggle"
            type="button"
            aria-label={menuOpen ? 'Закрыть меню' : 'Открыть меню'}
            aria-expanded={menuOpen}
            onClick={() => setMenuOpen((v) => !v)}
          >
            <span /><span />
          </button>
        </div>
      </header>

      <main className={wide ? 'main-wide' : undefined}>
        <Routes>
          <Route path="/" element={<Showcase />} />
          <Route path="/tours/:id" element={<TourDetail />} />
          <Route path="/tours/:id/book" element={<Guard roles={['CLIENT']}><TravelerForm /></Guard>} />
          <Route path="/login" element={<Login />} />
          <Route path="/register" element={<Register />} />
          <Route path="/confirm" element={<Confirm />} />
          <Route path="/my-bookings" element={<Guard roles={['CLIENT']}><MyBookings /></Guard>} />
          <Route path="/bookings/:id" element={<Guard><Voucher /></Guard>} />
          <Route path="/profile" element={<Guard><Profile /></Guard>} />
          <Route path="/builder" element={<Guard roles={['TOUR_MANAGER', 'ADMIN']}><Builder /></Guard>} />
          <Route path="/builder/:id" element={<Guard roles={['TOUR_MANAGER', 'ADMIN']}><TourConstructor /></Guard>} />
          <Route path="/catalog" element={<Guard roles={['TOUR_MANAGER', 'ADMIN']}><Catalog /></Guard>} />
          <Route path="/queue" element={<Guard roles={['TOUR_MANAGER', 'ADMIN']}><Queue /></Guard>} />
          <Route path="/reports" element={<Guard roles={['TOUR_MANAGER', 'ADMIN']}><Reports /></Guard>} />
          <Route path="/admin" element={<Guard roles={['ADMIN']}><Admin /></Guard>} />
          <Route path="*" element={<p>Страница не найдена. <Link to="/">На витрину</Link></p>} />
        </Routes>
      </main>

      <footer className="site-footer">
        <Link className="wordmark" to="/">TourFlow<span>.</span></Link>
        <span>Новые места — понятный путь.</span>
        <div>
          <Link to="/">Витрина</Link>
          <a href="mailto:support@tourflow.local">Поддержка</a>
        </div>
        <span>© TourFlow 2026</span>
      </footer>
    </div>
  );
}
