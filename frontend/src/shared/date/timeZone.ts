/**
 * The user's configured IANA timezone decides what "today" is and how timestamps are shown. It is a device
 * preference (localStorage), defaulting to the browser's zone. Date-only values in the API/DB are never rewritten.
 */
import type { IsoDate } from './calendar';

const STORAGE_KEY = 'planner.timeZone';

export function browserTimeZone(): string {
  return Intl.DateTimeFormat().resolvedOptions().timeZone;
}

export function isValidTimeZone(zone: string): boolean {
  try {
    new Intl.DateTimeFormat('en-US', { timeZone: zone });
    return true;
  } catch {
    return false;
  }
}

function storage(): Storage | undefined {
  return typeof localStorage === 'undefined' ? undefined : localStorage;
}

/** The explicitly chosen zone, or null when following the browser. */
export function configuredTimeZone(): string | null {
  const stored = storage()?.getItem(STORAGE_KEY) ?? null;
  return stored && isValidTimeZone(stored) ? stored : null;
}

export function currentTimeZone(): string {
  return configuredTimeZone() ?? browserTimeZone();
}

/** null resets to the browser default. Throws on an unknown zone. */
export function setConfiguredTimeZone(zone: string | null): void {
  if (zone !== null && !isValidTimeZone(zone)) throw new RangeError(`Unknown time zone: ${zone}`);
  if (zone === null) storage()?.removeItem(STORAGE_KEY);
  else storage()?.setItem(STORAGE_KEY, zone);
}

export function allTimeZones(): string[] {
  const zones = typeof Intl.supportedValuesOf === 'function' ? Intl.supportedValuesOf('timeZone') : [];
  return zones.includes('UTC') ? zones : ['UTC', ...zones];
}

interface WallParts {
  y: number;
  m: number;
  d: number;
  hh: number;
  mm: number;
  ss: number;
}

function wallParts(instant: Date, zone: string): WallParts {
  const parts = new Intl.DateTimeFormat('en-US', {
    timeZone: zone,
    calendar: 'gregory',
    numberingSystem: 'latn',
    hourCycle: 'h23',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
  }).formatToParts(instant);
  const get = (type: string) => Number(parts.find((p) => p.type === type)?.value);
  return { y: get('year'), m: get('month'), d: get('day'), hh: get('hour'), mm: get('minute'), ss: get('second') };
}

const pad = (n: number, w = 2) => String(n).padStart(w, '0');

/** Calendar date of {@code instant} in {@code zone}, as Gregorian ISO. No UTC date is ever used. */
export function dateInZone(instant: Date, zone: string): IsoDate {
  const p = wallParts(instant, zone);
  return `${pad(p.y, 4)}-${pad(p.m)}-${pad(p.d)}`;
}

/** "HH:mm" wall-clock time of {@code instant} in {@code zone}. */
export function timeInZone(instant: Date, zone: string): string {
  const p = wallParts(instant, zone);
  return `${pad(p.hh)}:${pad(p.mm)}`;
}

/** Zone offset (ms) at {@code instant}: wall time minus UTC. */
function offsetMs(instant: number, zone: string): number {
  const p = wallParts(new Date(instant), zone);
  return Date.UTC(p.y, p.m - 1, p.d, p.hh, p.mm, p.ss) - Math.floor(instant / 1000) * 1000;
}

/**
 * The UTC instant for a wall-clock date+time in {@code zone} (for timestamps such as check-in measuredAt).
 * Two-pass offset resolution handles DST; a non-existent spring-forward time resolves to the later offset.
 */
export function zonedWallTimeToUtc(date: IsoDate, hour: number, minute: number, zone: string): Date {
  const [y, m, d] = date.split('-').map(Number);
  const guess = Date.UTC(y, m - 1, d, hour, minute);
  const first = guess - offsetMs(guess, zone);
  const second = guess - offsetMs(first, zone);
  return new Date(second);
}
