import { apiRequest } from '../../shared/api/client';
import { acceptTokens, endSession, type SessionUser, type TokenResponse } from '../../shared/api/session';

export interface Credentials {
  email: string;
  password: string;
}

export const authApi = {
  /** Creates the account and signs in; the refresh cookie is set by the server. */
  register: async (body: Credentials & { displayName?: string }) => {
    acceptTokens(await apiRequest<TokenResponse>('/auth/register', { method: 'POST', body }));
  },
  login: async (body: Credentials) => {
    acceptTokens(await apiRequest<TokenResponse>('/auth/login', { method: 'POST', body }));
  },
  /** Revokes this browser's session on the server (and its cookie); always ends the local session. */
  logout: async () => {
    try {
      await apiRequest<void>('/auth/logout', { method: 'POST' });
    } finally {
      endSession();
    }
  },
  me: (signal?: AbortSignal) => apiRequest<SessionUser>('/auth/me', { signal }),
};
