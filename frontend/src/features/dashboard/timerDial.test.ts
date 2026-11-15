import { describe, expect, it } from 'vitest';
import { clampMinutes, DEG_PER_MIN, dragMinutes, MAX_DEG, mmss, polar, ticksAround } from './timerDial';

describe('timer dial', () => {
  it('centres the current minute and keeps ticks on the half circle', () => {
    const t = ticksAround(20);
    expect(t.find((x) => x.minute === 20)?.angle).toBe(0);
    expect(t.find((x) => x.minute === 25)?.angle).toBe(5 * DEG_PER_MIN);
    expect(t.find((x) => x.minute === 15)?.angle).toBe(-5 * DEG_PER_MIN);
    expect(t.every((x) => Math.abs(x.angle) <= MAX_DEG)).toBe(true);
    expect(t.filter((x) => x.major).map((x) => x.minute)).toEqual([15, 20, 25]); // about ±9 minutes visible
  });
  it('slides smoothly for fractional values and never draws below 0', () => {
    const t = ticksAround(2.5);
    expect(t.find((x) => x.minute === 2)?.angle).toBeCloseTo(-0.5 * DEG_PER_MIN);
    expect(Math.min(...t.map((x) => x.minute))).toBe(0);
  });
  it('drags like a clock face: left = more minutes, clamped to 1..120', () => {
    expect(dragMinutes(20, -40, 20)).toBe(22);
    expect(dragMinutes(20, 60, 20)).toBe(17);
    expect(dragMinutes(2, 500, 20)).toBe(1);
    expect(dragMinutes(118, -500, 20)).toBe(120);
    expect(clampMinutes(0)).toBe(1);
  });
  it('places points and formats mm:ss', () => {
    const top = polar(100, 100, 50, 0);
    expect([top.x, top.y]).toEqual([100, 50]);
    expect(polar(100, 100, 50, 90).x).toBeCloseTo(150);
    expect(mmss(1500)).toBe('25:00');
    expect(mmss(61.4)).toBe('01:01');
    expect(mmss(-3)).toBe('00:00');
  });
});
