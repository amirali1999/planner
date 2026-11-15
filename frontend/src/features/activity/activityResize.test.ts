import { describe, expect, it } from 'vitest';
import { resizedActivityEnd } from './activityResize';

describe('activity resize boundaries', () => {
  it('lengthens and shortens in five-minute steps without changing the original minute offset', () => {
    expect(resizedActivityEnd(600, 631, 13)).toBe(646);
    expect(resizedActivityEnd(600, 631, -13)).toBe(616);
  });
  it('keeps at least one minute and stops at midnight or the next activity', () => {
    expect(resizedActivityEnd(600, 630, -100)).toBe(601);
    expect(resizedActivityEnd(1380, 1410, 100)).toBe(1440);
    expect(resizedActivityEnd(600, 630, 100, 652)).toBe(652);
  });
});
