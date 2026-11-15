import { useEffect, useRef, useState, type KeyboardEvent, type PointerEvent as ReactPointerEvent } from 'react';
import { Icon } from '../../shared/ui/Icon';
import { pauseTimer, remaining, resetTimer, startTimer, type TimerState } from './dashboardModel';
import { clampMinutes, DEG_PER_MIN, dragMinutes, MAX_DEG, mmss, polar, ticksAround } from './timerDial';

const STORE_KEY = 'planner.focusMinutes';
const DEFAULT_MIN = 25;
// dial geometry (SVG units)
const W = 320;
const H = 96; // a flat arc: only the top of a large circle is shown (owner: lower card)
const CX = W / 2;
const CY = 222;
const R = 200;

function storedMinutes(): number {
  try {
    const v = Number(localStorage.getItem(STORE_KEY));
    return Number.isInteger(v) && v > 0 ? clampMinutes(v) : DEFAULT_MIN;
  } catch {
    return DEFAULT_MIN;
  }
}

/** Eases a displayed number toward `target` (the dial turns like a clock hand instead of jumping). */
function useEased(target: number, instant: boolean): number {
  const [value, setValue] = useState(target);
  const current = useRef(target);
  useEffect(() => {
    if (instant || window.matchMedia?.('(prefers-reduced-motion: reduce)').matches) {
      current.current = target;
      setValue(target);
      return;
    }
    let frame = 0;
    const step = () => {
      const d = target - current.current;
      current.current = Math.abs(d) < 0.01 ? target : current.current + d * 0.2;
      setValue(current.current);
      if (current.current !== target) frame = requestAnimationFrame(step);
    };
    frame = requestAnimationFrame(step);
    return () => cancelAnimationFrame(frame);
  }, [target, instant]);
  return value;
}

/**
 * Focus timer (owner design, English text): the minutes with − / +, and a half-circle dial whose ticks slide under a
 * fixed needle. Drag the dial (left = more), scroll it, or use the arrow keys; while running it turns with the
 * countdown. Client-only: sessions are not stored (no time-log API yet); the chosen length is remembered per device.
 */
export function FocusTimer() {
  const [minutes, setMinutes] = useState(storedMinutes);
  const [timer, setTimer] = useState<TimerState>(() => resetTimer(storedMinutes() * 60));
  const [now, setNow] = useState(() => Date.now());
  const [drag, setDrag] = useState<{ x: number; start: number; value: number; px: number } | null>(null);
  const svg = useRef<SVGSVGElement>(null);
  const repeat = useRef<number | undefined>(undefined);
  const running = timer.startedAt != null;
  const left = remaining(timer, now);
  const started = running || left !== timer.durationSec;
  const finished = left === 0;

  useEffect(() => {
    if (!running) return;
    const id = window.setInterval(() => setNow(Date.now()), 250);
    return () => window.clearInterval(id);
  }, [running]);
  useEffect(() => {
    if (running && finished) setTimer((t) => pauseTimer(t, Date.now()));
  }, [running, finished]);
  useEffect(() => () => window.clearInterval(repeat.current), []);

  function choose(m: number) {
    const v = clampMinutes(Math.round(m));
    setMinutes(v);
    setTimer(resetTimer(v * 60));
    try {
      localStorage.setItem(STORE_KEY, String(v));
    } catch {
      // private mode: not remembered
    }
  }
  const step = (d: number) => setMinutes((m) => {
    const v = clampMinutes(m + d);
    setTimer(resetTimer(v * 60));
    try { localStorage.setItem(STORE_KEY, String(v)); } catch { /* not remembered */ }
    return v;
  });
  /** − / +: one step on press, then repeats while held (like a watch crown). */
  function holdStart(d: number) {
    if (running) return;
    step(d);
    window.clearInterval(repeat.current);
    const t0 = Date.now();
    repeat.current = window.setInterval(() => Date.now() - t0 > 380 && step(d), 90);
  }
  const holdEnd = () => window.clearInterval(repeat.current);

  // dial value: the chosen minutes (eased), the live drag position, or the countdown
  const target = drag ? drag.value : started ? left / 60 : minutes;
  const dial = useEased(target, drag != null || running);

  function onDialDown(e: ReactPointerEvent<SVGSVGElement>) {
    if (running || e.button !== 0) return;
    const box = svg.current?.getBoundingClientRect();
    const scale = box ? box.width / W : 1;
    const px = (R * scale * DEG_PER_MIN * Math.PI) / 180; // arc length of one minute on screen
    e.currentTarget.setPointerCapture(e.pointerId);
    setDrag({ x: e.clientX, start: minutes, value: minutes, px });
  }
  function onDialMove(e: ReactPointerEvent<SVGSVGElement>) {
    if (!drag) return;
    const v = dragMinutes(drag.start, e.clientX - drag.x, drag.px);
    setDrag({ ...drag, value: v });
    if (Math.round(v) !== minutes) choose(v);
  }
  function onDialUp() {
    if (!drag) return;
    choose(drag.value);
    setDrag(null);
  }
  function onDialKey(e: KeyboardEvent<SVGSVGElement>) {
    if (running) return;
    const d = { ArrowRight: 1, ArrowUp: 1, ArrowLeft: -1, ArrowDown: -1, PageUp: 5, PageDown: -5 }[e.key];
    if (d) {
      e.preventDefault();
      step(d);
    } else if (e.key === 'Home' || e.key === 'End') {
      e.preventDefault();
      choose(e.key === 'Home' ? 1 : 120);
    }
  }

  const ticks = ticksAround(dial);
  const arc = (r: number) => {
    const a = polar(CX, CY, r, -(MAX_DEG + 6));
    const b = polar(CX, CY, r, MAX_DEG + 6);
    return `M ${a.x} ${a.y} A ${r} ${r} 0 0 1 ${b.x} ${b.y}`;
  };

  return (
    <section className="dash-card ftimer" dir="ltr" lang="en" aria-label="Focus timer">
      <div className="ftimer__top">
        <button type="button" className="ftimer__step" aria-label="Add a minute" disabled={running}
          onPointerDown={() => holdStart(1)} onPointerUp={holdEnd} onPointerLeave={holdEnd} onPointerCancel={holdEnd}
          onKeyDown={(e) => (e.key === 'Enter' || e.key === ' ') && (e.preventDefault(), step(1))}>
          <Icon name="plus" size={26} />
        </button>
        <div className="ftimer__value" role="timer" aria-live="off">
          {started ? (
            <>
              <span className="ftimer__num ftimer__num--clock">{mmss(left)}</span>
              <span className="ftimer__unit">{finished ? 'done' : running ? 'remaining' : 'paused'}</span>
            </>
          ) : (
            <>
              <span className="ftimer__num">{minutes}</span>
              <span className="ftimer__unit">{minutes === 1 ? 'minute' : 'minutes'}</span>
            </>
          )}
        </div>
        <button type="button" className="ftimer__step" aria-label="Remove a minute" disabled={running}
          onPointerDown={() => holdStart(-1)} onPointerUp={holdEnd} onPointerLeave={holdEnd} onPointerCancel={holdEnd}
          onKeyDown={(e) => (e.key === 'Enter' || e.key === ' ') && (e.preventDefault(), step(-1))}>
          <Icon name="minus" size={26} />
        </button>
      </div>

      <div className="ftimer__dialbox">
        <svg ref={svg} className={`ftimer__dial${drag ? ' ftimer__dial--drag' : ''}${running ? ' ftimer__dial--running' : ''}`}
          viewBox={`0 0 ${W} ${H}`} role="slider" tabIndex={running ? -1 : 0} aria-label="Focus length"
          aria-valuemin={1} aria-valuemax={120} aria-valuenow={minutes} aria-valuetext={`${minutes} minutes`} aria-disabled={running}
          onPointerDown={onDialDown} onPointerMove={onDialMove} onPointerUp={onDialUp} onPointerCancel={onDialUp}
          onWheel={(e) => !running && step(e.deltaY > 0 ? -1 : 1)} onKeyDown={onDialKey}>
          <defs>
            <linearGradient id="ftimer-fill" x1="0" y1="0" x2="0" y2="1">
              <stop offset="0%" stopColor="var(--accent)" stopOpacity="0.16" />
              <stop offset="100%" stopColor="var(--accent)" stopOpacity="0.02" />
            </linearGradient>
            <linearGradient id="ftimer-wave" x1="0" y1="0" x2="0" y2="1">
              <stop offset="0%" stopColor="var(--accent)" stopOpacity="0.35" />
              <stop offset="100%" stopColor="var(--accent)" stopOpacity="0.04" />
            </linearGradient>
          </defs>
          <path d={`${arc(R)} L ${W - 8} ${H} L 8 ${H} Z`} fill="url(#ftimer-fill)" />
          <path d={`M 20 ${H} C 90 ${H - 6}, 110 ${H - 34}, 160 ${H - 34} S 230 ${H - 10}, 300 ${H}`} fill="url(#ftimer-wave)" className="ftimer__wave" />
          <path d={`M 120 ${H} C 170 ${H - 8}, 190 ${H - 22}, 230 ${H - 22} S 290 ${H - 4}, 320 ${H}`} fill="url(#ftimer-wave)" opacity="0.7" className="ftimer__wave ftimer__wave--2" />
          <path d={arc(R)} className="ftimer__arc" fill="none" />
          {ticks.map((t) => {
            const len = t.major ? 16 : 9;
            const a = polar(CX, CY, R - 2, t.angle);
            const b = polar(CX, CY, R - 2 - len, t.angle);
            const label = polar(CX, CY, R - 33, t.angle);
            const fade = 1 - Math.abs(t.angle) / (MAX_DEG + 20);
            return (
              <g key={t.minute} opacity={fade}>
                <line x1={a.x} y1={a.y} x2={b.x} y2={b.y} className={t.major ? 'ftimer__tick ftimer__tick--major' : 'ftimer__tick'} />
                {t.major && <text x={label.x} y={label.y + 6} textAnchor="middle" className="ftimer__label">{t.minute}</text>}
              </g>
            );
          })}
          {/* the fixed needle at the top centre */}
          <line x1={CX} y1={CY - R - 6} x2={CX} y2={CY - R + 22} className="ftimer__needle" />
        </svg>
        <div className="ftimer__actions">
          {finished ? (
            <button type="button" className="ftimer__go" onClick={() => setTimer(resetTimer(minutes * 60))}>
              <Icon name="reset" size={18} /> Again
            </button>
          ) : (
            <button type="button" className="ftimer__go" aria-label={running ? 'Pause' : started ? 'Resume' : 'Start'}
              onClick={() => {
                const t = Date.now();
                setNow(t);
                setTimer((s) => (running ? pauseTimer(s, t) : startTimer(s, t)));
              }}>
              <Icon name={running ? 'pause' : 'play'} size={18} /> {running ? 'Pause' : started ? 'Resume' : 'Start'}
            </button>
          )}
          {started && !finished && (
            <button type="button" className="ftimer__reset" aria-label="Reset" title="Reset" onClick={() => setTimer(resetTimer(minutes * 60))}>
              <Icon name="reset" size={18} />
            </button>
          )}
        </div>
      </div>
      {finished && <p className="visually-hidden" role="status">Focus session finished.</p>}
    </section>
  );
}
