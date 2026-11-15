/**
 * Authentication session of this tab.
 * - The access token (short-lived JWT) lives only in memory: never in localStorage/sessionStorage.
 * - The refresh token is an httpOnly cookie the browser sends to /api/v1/auth/* by itself; scripts cannot read it.
 * - `refreshSession()` is single-flight: many requests failing with 401 at once cause ONE refresh call.
 */

export interface SessionUser {
  id: string;
  email: string;
  displayName: string | null;
  createdAt: string;
}

/** Body of login/register/refresh (OAuth 2.0 token response shape, camelCase). */
export interface TokenResponse {
  accessToken: string;
  tokenType: 'Bearer';
  /** Seconds; absent while the server issues non-expiring access tokens (temporary setting). */
  expiresIn?: number;
  user: SessionUser;
}

export type SessionState = { status: 'unknown' } | { status: 'anonymous' } | { status: 'authenticated'; user: SessionUser };

const AUTH_BASE = '/api/v1/auth';
/** A parallel tab rotated the cookie a moment ago: wait for the browser to store the new one, then try once more. */
const ROTATED_RETRY_DELAY_MS = 300;

let accessToken: string | null = null;
let state: SessionState = { status: 'unknown' };
let inFlight: Promise<boolean> | null = null;
const listeners = new Set<(s: SessionState) => void>();

function setState(next: SessionState) {
  state = next;
  listeners.forEach((l) => l(next));
}

export function sessionState(): SessionState {
  return state;
}

export function onSessionChange(listener: (s: SessionState) => void): () => void {
  listeners.add(listener);
  return () => listeners.delete(listener);
}

export function currentAccessToken(): string | null {
  return accessToken;
}

/** Stores the result of a successful login/register/refresh. */
export function acceptTokens(tokens: TokenResponse) {
  accessToken = tokens.accessToken;
  setState({ status: 'authenticated', user: tokens.user });
}

/** Forgets the access token locally (logout, or the session ended on the server). */
export function endSession() {
  accessToken = null;
  setState({ status: 'anonymous' });
}

async function callRefresh(): Promise<{ ok: true; tokens: TokenResponse } | { ok: false; code: string | null }> {
  let response: Response;
  try {
    response = await fetch(`${AUTH_BASE}/refresh`, { method: 'POST', credentials: 'same-origin', headers: { Accept: 'application/json' } });
  } catch {
    return { ok: false, code: 'network_error' };
  }
  const payload = await response.json().catch(() => null);
  if (response.ok && payload && typeof payload.accessToken === 'string') return { ok: true, tokens: payload as TokenResponse };
  return { ok: false, code: payload && typeof payload.code === 'string' ? payload.code : null };
}

/**
 * Gets a new access token with the refresh cookie. Resolves true on success. On a definitive failure the session
 * becomes anonymous; on a network error the state is left as it is (the server may just be down).
 */
export function refreshSession(): Promise<boolean> {
  if (!inFlight) {
    inFlight = (async () => {
      let result = await callRefresh();
      if (!result.ok && result.code === 'refresh_token_rotated') {
        await new Promise((resolve) => setTimeout(resolve, ROTATED_RETRY_DELAY_MS));
        result = await callRefresh();
      }
      if (result.ok) {
        acceptTokens(result.tokens);
        return true;
      }
      if (result.code !== 'network_error') endSession();
      else if (state.status === 'unknown') setState({ status: 'anonymous' });
      return false;
    })().finally(() => {
      inFlight = null;
    });
  }
  return inFlight;
}

/** Test hook: back to the initial state. */
export function resetSessionForTests() {
  accessToken = null;
  state = { status: 'unknown' };
  inFlight = null;
  listeners.clear();
}
