import type { ReactNode } from 'react';
import { Navigate, useLocation } from 'react-router';
import { Loading } from '../../shared/ui/AsyncState';
import { useSession } from './useSession';

/** Renders the app only for a signed-in user; otherwise sends to /login and comes back afterwards. */
export function RequireAuth({ children }: { children: ReactNode }) {
  const session = useSession();
  const location = useLocation();
  if (session.status === 'unknown') return <Loading />;
  if (session.status === 'anonymous') {
    const next = location.pathname + location.search;
    return <Navigate to={`/login?next=${encodeURIComponent(next)}`} replace />;
  }
  return <>{children}</>;
}

/** Only same-app paths are accepted as a return target (no open redirect). */
export function safeNext(raw: string | null): string {
  return raw && raw.startsWith('/') && !raw.startsWith('//') && !raw.startsWith('/login') && !raw.startsWith('/register') ? raw : '/dashboard';
}
