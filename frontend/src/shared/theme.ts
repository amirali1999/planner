/** Colour theme of this device (a per-device convenience, so localStorage is fine). Default: dark (the design). */
export type Theme = 'dark' | 'light';
const KEY = 'planner.theme';

export function storedTheme(): Theme {
  try {
    const v = localStorage.getItem(KEY);
    return v === 'light' || v === 'dark' ? v : 'dark';
  } catch {
    return 'dark';
  }
}

export function applyTheme(theme: Theme) {
  document.documentElement.dataset.theme = theme;
  document.documentElement.style.colorScheme = theme;
  try {
    localStorage.setItem(KEY, theme);
  } catch {
    // private mode: the choice just isn't remembered
  }
}
