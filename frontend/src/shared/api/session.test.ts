import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiError, apiRequest } from './client';
import { acceptTokens, currentAccessToken, refreshSession, resetSessionForTests, sessionState } from './session';

const user = { id: 'u1', email: 'a@test.local', displayName: null, createdAt: '2026-11-09T00:00:00.000Z' };
const tokens = (t: string) => ({ accessToken: t, tokenType: 'Bearer', expiresIn: 900, user });
const json = (status: number, body: unknown) =>
  new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });

type Call = { url: string; init: RequestInit };
let calls: Call[];
let handler: (url: string, init: RequestInit) => Response | Promise<Response>;

beforeEach(() => {
  resetSessionForTests();
  calls = [];
  vi.stubGlobal('fetch', vi.fn(async (url: string, init: RequestInit = {}) => {
    calls.push({ url, init });
    return handler(url, init);
  }));
});

afterEach(() => {
  vi.unstubAllGlobals();
  vi.useRealTimers();
});

const auth = (c: Call) => (c.init.headers as Record<string, string> | undefined)?.Authorization;

describe('session + client', () => {
  it('sends the in-memory access token as a Bearer header', async () => {
    acceptTokens(tokens('AT1') as never);
    handler = () => json(200, []);
    await apiRequest('/goals');
    expect(auth(calls[0])).toBe('Bearer AT1');
    expect(calls[0].init.credentials).toBe('same-origin');
  });

  it('on 401 refreshes once with the cookie and repeats the request with the new token', async () => {
    acceptTokens(tokens('OLD') as never);
    handler = (url, init) => {
      if (url.endsWith('/auth/refresh')) return json(200, tokens('NEW'));
      return (init.headers as Record<string, string>).Authorization === 'Bearer NEW' ? json(200, ['ok']) : json(401, { code: 'invalid_token', message: 'x' });
    };
    await expect(apiRequest<string[]>('/goals')).resolves.toEqual(['ok']);
    expect(calls.map((c) => c.url)).toEqual(['/api/v1/goals', '/api/v1/auth/refresh', '/api/v1/goals']);
    expect(currentAccessToken()).toBe('NEW');
  });

  it('many parallel 401s cause a single refresh call', async () => {
    acceptTokens(tokens('OLD') as never);
    handler = (url, init) => {
      if (url.endsWith('/auth/refresh')) return json(200, tokens('NEW'));
      return (init.headers as Record<string, string>).Authorization === 'Bearer NEW' ? json(200, 1) : json(401, { code: 'invalid_token', message: 'x' });
    };
    await Promise.all([apiRequest('/goals'), apiRequest('/tasks'), apiRequest('/inbox-items')]);
    expect(calls.filter((c) => c.url.endsWith('/auth/refresh'))).toHaveLength(1);
  });

  it('a failed refresh ends the session and surfaces the 401', async () => {
    acceptTokens(tokens('OLD') as never);
    handler = (url) =>
      url.endsWith('/auth/refresh') ? json(401, { code: 'invalid_refresh_token', message: 'x' }) : json(401, { code: 'invalid_token', message: 'x' });
    await expect(apiRequest('/goals')).rejects.toMatchObject({ status: 401 } satisfies Partial<ApiError>);
    expect(sessionState()).toEqual({ status: 'anonymous' });
    expect(currentAccessToken()).toBeNull();
  });

  it('retries once when a parallel tab just rotated the refresh cookie', async () => {
    let refreshCalls = 0;
    handler = (url) => {
      if (!url.endsWith('/auth/refresh')) return json(200, []);
      refreshCalls++;
      return refreshCalls === 1 ? json(401, { code: 'refresh_token_rotated', message: 'x' }) : json(200, tokens('T2'));
    };
    await expect(refreshSession()).resolves.toBe(true);
    expect(refreshCalls).toBe(2);
    expect(sessionState()).toMatchObject({ status: 'authenticated' });
  });

  it('a network error during refresh does not log the user out', async () => {
    acceptTokens(tokens('OLD') as never);
    handler = () => {
      throw new TypeError('offline');
    };
    await expect(refreshSession()).resolves.toBe(false);
    expect(sessionState()).toMatchObject({ status: 'authenticated' });
  });

  it('never retries auth endpoints themselves (no refresh loop on a wrong password)', async () => {
    handler = () => json(401, { code: 'invalid_credentials', message: 'x' });
    await expect(apiRequest('/auth/login', { method: 'POST', body: {} })).rejects.toMatchObject({ code: 'invalid_credentials' });
    expect(calls).toHaveLength(1);
  });

  it('keeps tokens out of web storage', async () => {
    acceptTokens(tokens('SECRET') as never);
    expect(JSON.stringify(globalThis.localStorage ?? {})).not.toContain('SECRET');
    expect(JSON.stringify(globalThis.sessionStorage ?? {})).not.toContain('SECRET');
  });
});
