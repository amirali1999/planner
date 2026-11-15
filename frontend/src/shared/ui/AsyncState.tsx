import type { ReactNode } from 'react';
import { ApiError } from '../api/client';

export function Loading({ label = 'در حال بارگذاری…' }: { label?: string }) {
  return (
    <p className="state state--loading" role="status" aria-live="polite">
      {label}
    </p>
  );
}

export function ErrorMessage({ error, onRetry }: { error: unknown; onRetry?: () => void }) {
  const message =
    error instanceof ApiError ? error.message : 'خطای غیرمنتظره رخ داد. لطفاً دوباره تلاش کنید.';
  return (
    <div className="state state--error" role="alert">
      <p>{message}</p>
      {onRetry && (
        <button type="button" className="button button--secondary" onClick={onRetry}>
          تلاش دوباره
        </button>
      )}
    </div>
  );
}

export function EmptyState({ title, children }: { title: string; children?: ReactNode }) {
  return (
    <div className="state state--empty">
      <p className="state__title">{title}</p>
      {children}
    </div>
  );
}
