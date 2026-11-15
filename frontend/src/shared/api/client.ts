/** Typed fetch client for the local backend. All paths are relative to /api/v1 (proxied by Vite in dev). */
import { currentAccessToken, refreshSession } from './session';

export const API_BASE = '/api/v1';

/** Backend error body: { code, message, details? } (docs/API_CONTRACT.md). */
export interface ApiErrorBody {
  code: string;
  message: string;
  details?: Record<string, unknown>;
}

export class ApiError extends Error {
  readonly status: number;
  readonly code: string;
  readonly details: Record<string, unknown>;

  constructor(status: number, code: string, message: string, details?: Record<string, unknown>) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.code = code;
    this.details = details ?? {};
  }

  /** Field-level machine codes, e.g. { periodStart: 'invalid_value' }. */
  fieldCodes(): Record<string, string> {
    const out: Record<string, string> = {};
    for (const [field, value] of Object.entries(this.details)) {
      if (typeof value === 'string') out[field] = value;
    }
    return out;
  }
}

const NETWORK_MESSAGE = 'ارتباط با سرور برقرار نشد. مطمئن شوید Backend روی رایانه اجرا شده است.';
const UNEXPECTED_MESSAGE = 'پاسخ نامعتبر از سرور دریافت شد.';

function isErrorBody(value: unknown): value is ApiErrorBody {
  return (
    typeof value === 'object' &&
    value !== null &&
    typeof (value as ApiErrorBody).code === 'string' &&
    typeof (value as ApiErrorBody).message === 'string'
  );
}

export interface RequestOptions {
  method?: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE';
  body?: unknown;
  signal?: AbortSignal;
}

export async function apiRequest<T>(path: string, options: RequestOptions = {}): Promise<T> {
  return (await apiRequestWithHeaders<T>(path, options)).data;
}

/** Same as {@link apiRequest} but also returns the response headers (e.g. `X-Total-Count` of a paged list). */
async function send(path: string, method: string, body: unknown, signal: AbortSignal | undefined): Promise<Response> {
  const headers: Record<string, string> = { Accept: 'application/json' };
  if (body !== undefined) headers['Content-Type'] = 'application/json';
  const token = currentAccessToken();
  if (token) headers.Authorization = `Bearer ${token}`;
  try {
    return await fetch(API_BASE + path, {
      method,
      signal,
      credentials: 'same-origin',
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
    });
  } catch (cause) {
    if (cause instanceof DOMException && cause.name === 'AbortError') throw cause;
    throw new ApiError(0, 'network_error', NETWORK_MESSAGE);
  }
}

/**
 * Sends the in-memory access token as `Authorization: Bearer …`. On 401 from a data endpoint it refreshes the
 * session once (refresh cookie) and repeats the request; if that is impossible the session ends (login screen).
 */
export async function apiRequestWithHeaders<T>(path: string, options: RequestOptions = {}): Promise<{ data: T; headers: Headers }> {
  const { method = 'GET', body, signal } = options;
  let response = await send(path, method, body, signal);
  if (response.status === 401 && !path.startsWith('/auth/')) {
    if (await refreshSession()) {
      response = await send(path, method, body, signal);
    }
  }

  const text = await response.text();
  let payload: unknown = undefined;
  if (text) {
    try {
      payload = JSON.parse(text);
    } catch {
      payload = undefined;
    }
  }

  if (!response.ok) {
    if (isErrorBody(payload)) {
      throw new ApiError(response.status, payload.code, payload.message, payload.details);
    }
    throw new ApiError(response.status, 'unexpected_response', UNEXPECTED_MESSAGE);
  }
  return { data: payload as T, headers: response.headers };
}
