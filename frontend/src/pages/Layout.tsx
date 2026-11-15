import { useEffect, useRef, useState } from 'react';
import { Link, NavLink, Outlet, useLocation } from 'react-router';
import { authApi } from '../features/auth/api';
import { useSession } from '../features/auth/useSession';
import { inboxApi } from '../features/inbox/api';
import { todayIso } from '../shared/date/calendar';
import { formatJalaliDateWithWeekday, formatNumber } from '../shared/format';
import { applyTheme, storedTheme, type Theme } from '../shared/theme';
import { Icon, type IconName } from '../shared/ui/Icon';
import { useApiData } from '../shared/ui/useApiData';

interface NavItem {
  to: string;
  label: string;
  icon: IconName;
  /** Designed but not built yet (no page/API): shown disabled. */
  soon?: boolean;
}

/** Main pages, in the top bar. */
const NAV: NavItem[] = [
  { to: '/dashboard', label: 'داشبورد', icon: 'dashboard' },
  { to: '/goals', label: 'اهداف', icon: 'target' },
  { to: '/focus', label: 'آیتم‌های تمرکز', icon: 'layers' },
  { to: '/weekly', label: 'برنامه هفتگی', icon: 'calendar' },
  { to: '/activity-log', label: 'لاگ روزانه', icon: 'clock' },
  { to: '/today', label: 'تخته امروز', icon: 'today' },
  { to: '/reports', label: 'گزارش‌ها', icon: 'chart', soon: true },
];

/** Secondary pages, in the account menu. */
const MORE: NavItem[] = [
  { to: '/inbox', label: 'صندوق ورودی', icon: 'inbox' },
  { to: '/archive', label: 'بایگانی', icon: 'archive' },
  { to: '/settings', label: 'تنظیمات', icon: 'settings' },
];

const WIDE = ['/dashboard', '/goals', '/focus', '/weekly', '/today', '/archive', '/activity-log'];

/** App shell (design: owner screenshot): one top bar — brand + date, main pages, inbox bell, account menu. */
export function Layout() {
  const location = useLocation();
  const wide = WIDE.includes(location.pathname);
  const session = useSession();
  const user = session.status === 'authenticated' ? session.user : null;
  const today = todayIso();
  // unprocessed inbox notes = the bell's count (refetched on every page change)
  const inbox = useApiData((signal) => inboxApi.list('unprocessed', signal), [location.pathname]);
  const unprocessed = inbox.data?.length ?? 0;

  return (
    <div className="shell">
      <a className="skip-link" href="#main">
        پرش به محتوای اصلی
      </a>
      <header className="topbar">
        <div className="topbar__brand">
          <span className="topbar__logo" aria-hidden="true"><Icon name="check" size={18} /></span>
          <span className="app-title">برنامه‌ریز شخصی</span>
          <time className="topbar__date" dateTime={today}>{formatJalaliDateWithWeekday(today)}</time>
        </div>
        <nav className="topbar__nav" aria-label="ناوبری اصلی">
          <ul className="top-nav">
            {NAV.map((item) => (
              <li key={item.to}>
                {item.soon ? (
                  <span className="top-nav__link top-nav__link--soon" aria-disabled="true" title="به‌زودی">
                    <Icon name={item.icon} size={20} />
                    <span>{item.label}</span>
                  </span>
                ) : (
                  <NavLink to={item.to} className={({ isActive }) => `top-nav__link${isActive ? ' top-nav__link--active' : ''}`}>
                    <Icon name={item.icon} size={20} />
                    <span>{item.label}</span>
                  </NavLink>
                )}
              </li>
            ))}
          </ul>
        </nav>
        <div className="topbar__actions">
          <Link to="/inbox" className="icon-button topbar__bell"
            aria-label={unprocessed > 0 ? `صندوق ورودی، ${formatNumber(unprocessed)} یادداشت پردازش‌نشده` : 'صندوق ورودی'}
            title="صندوق ورودی">
            <Icon name="bell" size={22} />
            {unprocessed > 0 && <span className="topbar__badge" aria-hidden="true">{formatNumber(unprocessed)}</span>}
          </Link>
          {user && <AccountMenu name={user.displayName ?? user.email} email={user.email} />}
        </div>
      </header>
      <div className="shell__main">
        {/* keyed by path: each page enters with a short fade/slide (see .page in styles.css) */}
        <main key={location.pathname} id="main" className={wide ? 'page page--wide' : 'page'} tabIndex={-1}>
          <Outlet />
        </main>
      </div>
    </div>
  );
}

/** Avatar (initial) + chevron: secondary pages, theme switch and sign-out. */
function AccountMenu({ name, email }: { name: string; email: string }) {
  const [open, setOpen] = useState(false);
  const [theme, setTheme] = useState<Theme>(storedTheme);
  const [loggingOut, setLoggingOut] = useState(false);
  const box = useRef<HTMLDivElement>(null);
  const button = useRef<HTMLButtonElement>(null);
  const location = useLocation();
  useEffect(() => setOpen(false), [location.pathname]);
  useEffect(() => {
    if (!open) return;
    const onDown = (e: PointerEvent) => !box.current?.contains(e.target as Node) && setOpen(false);
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') {
        setOpen(false);
        button.current?.focus();
      }
    };
    window.addEventListener('pointerdown', onDown);
    window.addEventListener('keydown', onKey);
    return () => {
      window.removeEventListener('pointerdown', onDown);
      window.removeEventListener('keydown', onKey);
    };
  }, [open]);
  const switchTheme = () => {
    const next = theme === 'dark' ? 'light' : 'dark';
    applyTheme(next);
    setTheme(next);
  };
  const initial = (name.trim()[0] ?? '?').toUpperCase();
  return (
    <div className="account" ref={box}>
      <button ref={button} type="button" className="account__toggle" aria-expanded={open} aria-controls="account-menu"
        aria-label={`حساب کاربری (${email})`} onClick={() => setOpen((o) => !o)}>
        <span className="account__avatar" aria-hidden="true">{initial}</span>
        <Icon name="chevron-down" size={18} />
      </button>
      {open && (
        <div id="account-menu" className="account__menu">
          <p className="account__who" dir="auto" title={email}>{name}</p>
          <ul className="account__list">
            {MORE.map((item) => (
              <li key={item.to}>
                <NavLink to={item.to} className={({ isActive }) => `account__item${isActive ? ' account__item--active' : ''}`}>
                  <Icon name={item.icon} size={20} /> {item.label}
                </NavLink>
              </li>
            ))}
            <li>
              <button type="button" className="account__item" onClick={switchTheme}>
                <Icon name={theme === 'dark' ? 'sun' : 'moon'} size={20} /> {theme === 'dark' ? 'تم روشن' : 'تم تیره'}
              </button>
            </li>
            <li>
              <button type="button" className="account__item" disabled={loggingOut}
                onClick={() => {
                  setLoggingOut(true);
                  void authApi.logout().finally(() => setLoggingOut(false));
                }}>
                <Icon name="logout" size={20} /> خروج
              </button>
            </li>
          </ul>
        </div>
      )}
    </div>
  );
}
