/** Same limit and counting as the backend: Unicode code points (an emoji is one character). */
export const RAW_TEXT_MAX = 4000;

export function codePointLength(text: string): number {
  return [...text].length;
}

export type CaptureCheck = { ok: true; text: string } | { ok: false; code: 'required' | 'too_long' };

/** Trims only outer whitespace; the text itself is kept exactly as typed. */
export function checkCapture(raw: string): CaptureCheck {
  const text = raw.trim();
  if (!text) return { ok: false, code: 'required' };
  if (codePointLength(text) > RAW_TEXT_MAX) return { ok: false, code: 'too_long' };
  return { ok: true, text };
}
