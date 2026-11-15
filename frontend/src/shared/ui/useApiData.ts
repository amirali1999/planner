import { useCallback, useEffect, useRef, useState } from 'react';

export type ApiData<T> =
  | { status: 'loading'; data?: T; error?: undefined }
  | { status: 'error'; data?: T; error: unknown }
  | { status: 'success'; data: T; error?: undefined };

/**
 * Loads data from the API, aborting on unmount/dependency change. {@code reload()} refetches and keeps
 * the previous data visible meanwhile (no flashing empty state after a check-in).
 */
export function useApiData<T>(load: (signal: AbortSignal) => Promise<T>, deps: readonly unknown[]) {
  const [state, setState] = useState<ApiData<T>>({ status: 'loading' });
  const [version, setVersion] = useState(0);
  const loadRef = useRef(load);
  loadRef.current = load;

  useEffect(() => {
    const controller = new AbortController();
    setState((previous) => ({ status: 'loading', data: previous.data }));
    loadRef
      .current(controller.signal)
      .then((data) => {
        if (!controller.signal.aborted) setState({ status: 'success', data });
      })
      .catch((error: unknown) => {
        if (!controller.signal.aborted) setState((previous) => ({ status: 'error', error, data: previous.data }));
      });
    return () => controller.abort();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [...deps, version]);

  const reload = useCallback(() => setVersion((v) => v + 1), []);
  return { ...state, reload };
}
