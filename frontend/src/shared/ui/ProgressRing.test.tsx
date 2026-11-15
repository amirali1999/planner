import { renderToStaticMarkup } from 'react-dom/server';
import { describe, expect, it } from 'vitest';
import { ProgressRing } from './ProgressRing';

describe('ProgressRing', () => {
  it('draws the value arc and names it for assistive tech', () => {
    const html = renderToStaticMarkup(<ProgressRing value={40} label="پیشرفت" showValue />);
    expect(html).toContain('role="img"');
    expect(html).toContain('aria-label="پیشرفت: ۴۰٪"');
    expect(html).toContain('ring__value');
    expect(html).toContain('۴۰٪');
  });

  it('shows an unmeasured value as an empty dashed ring, never 0%', () => {
    const html = renderToStaticMarkup(<ProgressRing value={null} label="پیشرفت" showValue />);
    expect(html).toContain('سنجیده نشده');
    expect(html).toContain('ring__track--empty');
    expect(html).not.toContain('ring__value');
    expect(html).not.toContain('۰٪');
  });

  it('keeps a measured 0% distinct and clamps out-of-range values', () => {
    expect(renderToStaticMarkup(<ProgressRing value={0} label="x" showValue />)).toContain('۰٪');
    expect(renderToStaticMarkup(<ProgressRing value={150} label="x" />)).toMatch(/stroke-dasharray="([\d.]+) \1"/);
  });
});
