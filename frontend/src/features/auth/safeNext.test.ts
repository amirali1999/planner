import { describe, expect, it } from 'vitest';
import { safeNext } from './RequireAuth';

describe('safeNext (no open redirect after login)', () => {
  it.each([
    ['/goals/tree?status=doing', '/goals/tree?status=doing'],
    [null, '/dashboard'],
    ['https://evil.example', '/dashboard'],
    ['//evil.example/x', '/dashboard'],
    ['/login?next=/x', '/dashboard'],
    ['/register', '/dashboard'],
    ['javascript:alert(1)', '/dashboard'],
  ])('%s → %s', (raw, expected) => {
    expect(safeNext(raw)).toBe(expected);
  });
});
