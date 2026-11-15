import { renderToStaticMarkup } from 'react-dom/server';
import { describe, expect, it } from 'vitest';
import { checkCapture, codePointLength, RAW_TEXT_MAX } from './inboxModel';
import { InboxItemRow } from './InboxItemRow';
import type { InboxItem } from './types';

describe('inbox capture rules', () => {
  it('counts code points like the backend (emoji = 1)', () => {
    expect(codePointLength('😀ا')).toBe(2);
    expect('😀'.length).toBe(2); // UTF-16 would over-count
  });
  it('trims only outer whitespace and keeps inner text as typed', () => {
    expect(checkCapture('  یک\nدو  ')).toEqual({ ok: true, text: 'یک\nدو' });
    expect(checkCapture(' \n ')).toEqual({ ok: false, code: 'required' });
    expect(checkCapture('x'.repeat(RAW_TEXT_MAX))).toEqual({ ok: true, text: 'x'.repeat(RAW_TEXT_MAX) });
    expect(checkCapture('x'.repeat(RAW_TEXT_MAX + 1))).toEqual({ ok: false, code: 'too_long' });
  });
});

describe('InboxItemRow', () => {
  it('renders markup in a note as text, never as HTML', () => {
    const item: InboxItem = {
      id: 'i1', rawText: '<script>alert(1)</script><img src=x onerror="alert(2)">', status: 'unprocessed', decision: null,
      createdEntityType: null, createdEntityId: null, processedAt: null, capturedAt: '2026-11-08T12:00:00Z',
      createdAt: '2026-11-08T12:00:00Z', updatedAt: '2026-11-08T12:00:00Z',
    };
    const html = renderToStaticMarkup(<InboxItemRow item={item} />);
    expect(html).toContain('&lt;script&gt;alert(1)&lt;/script&gt;');
    expect(html).not.toContain('<script');
    expect(html).not.toContain('<img');
    expect(html).toContain('پردازش‌نشده');
  });
});
