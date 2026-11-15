import { useEffect, useSyncExternalStore } from 'react';
import { onSessionChange, refreshSession, sessionState } from '../../shared/api/session';

/** Current session; on first use it restores a session from the refresh cookie (silent sign-in after reload). */
export function useSession() {
  const state = useSyncExternalStore(onSessionChange, sessionState, sessionState);
  useEffect(() => {
    if (sessionState().status === 'unknown') void refreshSession();
  }, []);
  return state;
}
