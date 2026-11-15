/** Inline SVG icons (no icon font / CDN). Decorative by default: give meaning with surrounding text. */
export type IconName =
  | 'dashboard' | 'target' | 'calendar' | 'check-circle' | 'chart' | 'settings' | 'sun' | 'moon' | 'inbox'
  | 'list' | 'layers' | 'more' | 'grip' | 'search' | 'book' | 'cap' | 'briefcase' | 'flag' | 'file' | 'sort' | 'chevron-right' | 'status' | 'archive' | 'filter' | 'tree' | 'leaf' | 'play' | 'pause' | 'reset' | 'plus' | 'chevron-left' | 'focus' | 'today' | 'logout' | 'check' | 'bell' | 'chevron-down' | 'clock' | 'alert' | 'trend-down' | 'timer' | 'minus';

const PATHS: Record<IconName, string> = {
  dashboard: 'M4 4h6v6H4zM14 4h6v6h-6zM4 14h6v6H4zM14 14h6v6h-6z',
  target: 'M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18zM12 16a4 4 0 1 0 0-8 4 4 0 0 0 0 8zM12 12h.01M12 12l7-7',
  calendar: 'M4 6h16v14H4zM4 10h16M8 3v4M16 3v4',
  'check-circle': 'M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18zM8 12l3 3 5-6',
  chart: 'M5 20V12M10 20V8M15 20v-6M20 20V4',
  settings: 'M12 15a3 3 0 1 0 0-6 3 3 0 0 0 0 6zM19.4 15a1.7 1.7 0 0 0 .3 1.9l.1.1a2 2 0 1 1-2.8 2.8l-.1-.1a1.7 1.7 0 0 0-1.9-.3 1.7 1.7 0 0 0-1 1.5V21a2 2 0 1 1-4 0v-.1a1.7 1.7 0 0 0-1.1-1.6 1.7 1.7 0 0 0-1.9.3l-.1.1a2 2 0 1 1-2.8-2.8l.1-.1a1.7 1.7 0 0 0 .3-1.9 1.7 1.7 0 0 0-1.5-1H3a2 2 0 1 1 0-4h.1a1.7 1.7 0 0 0 1.6-1.1 1.7 1.7 0 0 0-.3-1.9l-.1-.1a2 2 0 1 1 2.8-2.8l.1.1a1.7 1.7 0 0 0 1.9.3H9a1.7 1.7 0 0 0 1-1.5V3a2 2 0 1 1 4 0v.1a1.7 1.7 0 0 0 1 1.5 1.7 1.7 0 0 0 1.9-.3l.1-.1a2 2 0 1 1 2.8 2.8l-.1.1a1.7 1.7 0 0 0-.3 1.9V9a1.7 1.7 0 0 0 1.5 1H21a2 2 0 1 1 0 4h-.1a1.7 1.7 0 0 0-1.5 1z',
  sun: 'M12 17a5 5 0 1 0 0-10 5 5 0 0 0 0 10zM12 1v2M12 21v2M4.2 4.2l1.4 1.4M18.4 18.4l1.4 1.4M1 12h2M21 12h2M4.2 19.8l1.4-1.4M18.4 5.6l1.4-1.4',
  moon: 'M21 12.8A9 9 0 1 1 11.2 3a7 7 0 0 0 9.8 9.8z',
  inbox: 'M22 12h-6l-2 3h-4l-2-3H2M5.5 5h13L22 12v6a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2v-6z',
  list: 'M9 6h11M9 12h11M9 18h11M4 6h.01M4 12h.01M4 18h.01',
  layers: 'M12 3l9 5-9 5-9-5zM3 13l9 5 9-5M3 17.5l9 5 9-5',
  leaf: 'M5 19C5 10 10 5 20 4c-1 10-6 15-15 15zM5 19l8-8',
  play: 'M7 4l13 8-13 8z',
  pause: 'M7 4h4v16H7zM13 4h4v16h-4z',
  reset: 'M3 12a9 9 0 1 0 3-6.7L3 8M3 3v5h5',
  plus: 'M12 5v14M5 12h14',
  'chevron-left': 'M15 18l-6-6 6-6',
  focus: 'M3 7V3h4M17 3h4v4M21 17v4h-4M7 21H3v-4M12 15a3 3 0 1 0 0-6 3 3 0 0 0 0 6z',
  today: 'M4 6h16v14H4zM4 10h16M8 3v4M16 3v4M10 14l2 2 3-3',
  logout: 'M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4M16 17l5-5-5-5M21 12H9',
  check: 'M5 12l5 5 9-10',
  minus: 'M5 12h14',
  bell: 'M18 16V11a6 6 0 1 0-12 0v5l-2 2h16zM10 21h4',
  'chevron-down': 'M6 9l6 6 6-6',
  clock: 'M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18zM12 7v5l3 2',
  alert: 'M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18zM12 7v6M12 16.5h.01',
  'trend-down': 'M3 7l6 6 4-4 8 8M21 11v6h-6',
  timer: 'M12 22a8 8 0 1 0 0-16 8 8 0 0 0 0 16zM12 10v4l2 2M10 2h4M19 6l1.5-1.5',
  more: 'M12 5h.01M12 12h.01M12 19h.01',
  search: 'M11 18a7 7 0 1 0 0-14 7 7 0 0 0 0 14zM21 21l-5-5',
  book: 'M3 5a2 2 0 0 1 2-2h5v17H5a2 2 0 0 0-2 2zM21 5a2 2 0 0 0-2-2h-5v17h5a2 2 0 0 1 2 2z',
  cap: 'M2 9l10-5 10 5-10 5zM6 11v5c3 2 9 2 12 0v-5M22 9v6',
  briefcase: 'M3 7h18v13H3zM8 7V5a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2M3 12h18',
  flag: 'M5 21V4h11l-2 4 2 4H5',
  file: 'M14 3H6v18h12V7zM14 3v4h4M9 13h6M9 17h6',
  sort: 'M7 4v16M4 7l3-3 3 3M17 20V4M14 17l3 3 3-3',
  'chevron-right': 'M9 18l6-6-6-6',
  status: 'M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18zM8 12l3 3 5-6',
  grip: 'M9 5h.01M15 5h.01M9 12h.01M15 12h.01M9 19h.01M15 19h.01',
  archive: 'M3 4h18v4H3zM5 8v12h14V8M10 12h4',
  filter: 'M3 5h18l-7 8v6l-4-2v-4z',
  tree: 'M12 3v6M6 15v-3h12v3M4 15h4v5H4zM10 15h4v5h-4zM16 15h4v5h-4zM9 3h6v6H9z',
};

export function Icon({ name, size = 20, className }: { name: IconName; size?: number; className?: string }) {
  const filled = name === 'play' || name === 'pause';
  return (
    <svg
      className={className}
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill={filled ? 'currentColor' : 'none'}
      stroke="currentColor"
      strokeWidth={filled ? 0 : name === 'more' || name === 'grip' ? 3.2 : 1.8}
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
      focusable="false"
    >
      <path d={PATHS[name]} />
    </svg>
  );
}
