import { afterEach, describe, expect, it, vi } from 'vitest';
import { ApiError, apiRequest } from './client';

function mockFetch(impl: (url: string, init: RequestInit) => Promise<Response>) {
  vi.stubGlobal('fetch', vi.fn(impl));
}

afterEach(() => vi.unstubAllGlobals());

describe('apiRequest', () => {
  it('prefixes /api/v1 and sends JSON', async () => {
    let seen: { url: string; init: RequestInit } | undefined;
    mockFetch(async (url, init) => {
      seen = { url, init };
      return new Response(JSON.stringify({ id: 'x' }), { status: 201 });
    });
    const result = await apiRequest<{ id: string }>('/goals', { method: 'POST', body: { title: 't', baseline: 0.1 } });
    expect(result).toEqual({ id: 'x' });
    expect(seen?.url).toBe('/api/v1/goals');
    expect(seen?.init.body).toBe('{"title":"t","baseline":0.1}');
  });

  it('turns contract error bodies into ApiError with field codes', async () => {
    mockFetch(async () =>
      new Response(
        JSON.stringify({ code: 'validation_error', message: 'نامعتبر', details: { target: 'equals_baseline' } }),
        { status: 400 },
      ),
    );
    const error = await apiRequest('/x').catch((e: unknown) => e);
    expect(error).toBeInstanceOf(ApiError);
    expect((error as ApiError).status).toBe(400);
    expect((error as ApiError).code).toBe('validation_error');
    expect((error as ApiError).fieldCodes()).toEqual({ target: 'equals_baseline' });
  });

  it('reports network failure without leaking internals', async () => {
    mockFetch(async () => {
      throw new TypeError('Failed to fetch');
    });
    const error = (await apiRequest('/x').catch((e: unknown) => e)) as ApiError;
    expect(error.code).toBe('network_error');
    expect(error.status).toBe(0);
  });

  it('handles non-JSON error responses (e.g. proxy 502)', async () => {
    mockFetch(async () => new Response('<html>Bad gateway</html>', { status: 502 }));
    const error = (await apiRequest('/x').catch((e: unknown) => e)) as ApiError;
    expect(error.status).toBe(502);
    expect(error.code).toBe('unexpected_response');
  });
});
