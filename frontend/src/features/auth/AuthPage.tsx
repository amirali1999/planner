import { useState, type FormEvent } from 'react';
import { Link, Navigate, useNavigate, useSearchParams } from 'react-router';
import { ApiError } from '../../shared/api/client';
import { ErrorMessage, Loading } from '../../shared/ui/AsyncState';
import type { FieldErrors } from '../../shared/ui/fieldErrors';
import { TextField } from '../../shared/ui/TextField';
import { usePageTitle } from '../../shared/ui/usePageTitle';
import { authApi } from './api';
import { safeNext } from './RequireAuth';
import { useSession } from './useSession';

const PASSWORD_HINT = 'دست‌کم ۸ نویسه؛ عبارت بلند و به‌یادماندنی بهتر از رمز کوتاه و پیچیده است.';

/** Login and registration share one accessible form; `mode` decides the endpoint and extra fields. */
export function AuthPage({ mode }: { mode: 'login' | 'register' }) {
  const isRegister = mode === 'register';
  usePageTitle(isRegister ? 'ساخت حساب' : 'ورود');
  const session = useSession();
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const next = safeNext(params.get('next'));
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [displayName, setDisplayName] = useState('');
  const [errors, setErrors] = useState<FieldErrors>({});
  const [serverError, setServerError] = useState<unknown>(null);
  const [submitting, setSubmitting] = useState(false);

  if (session.status === 'unknown') return <Loading />;
  if (session.status === 'authenticated' && !submitting) return <Navigate to={next} replace />;

  async function submit(event: FormEvent) {
    event.preventDefault();
    if (submitting) return;
    const local: FieldErrors = {};
    if (!email.trim()) local.email = 'required';
    if (!password) local.password = 'required';
    else if (isRegister && [...password].length < 8) local.password = 'too_short';
    setErrors(local);
    setServerError(null);
    if (Object.keys(local).length) return;
    setSubmitting(true);
    try {
      if (isRegister) await authApi.register({ email: email.trim(), password, displayName: displayName.trim() || undefined });
      else await authApi.login({ email: email.trim(), password });
      navigate(next, { replace: true });
    } catch (error) {
      if (error instanceof ApiError) setErrors(error.fieldCodes());
      setServerError(error);
      setSubmitting(false);
    }
  }

  return (
    <main id="main" className="auth" aria-labelledby="auth-heading">
      <form className="card form auth__form" onSubmit={submit} noValidate>
        <h1 id="auth-heading">{isRegister ? 'ساخت حساب کاربری' : 'ورود به برنامه‌ریز'}</h1>
        {isRegister && (
          <TextField label="نام نمایشی (اختیاری)" value={displayName} onChange={setDisplayName} maxLength={100} autoComplete="nickname" />
        )}
        <TextField
          label="ایمیل"
          type="email"
          dir="ltr"
          value={email}
          onChange={setEmail}
          required
          maxLength={254}
          autoComplete={isRegister ? 'email' : 'username'}
          errorCode={errors.email}
        />
        <TextField
          label="رمز عبور"
          type="password"
          dir="ltr"
          value={password}
          onChange={setPassword}
          required
          maxLength={128}
          autoComplete={isRegister ? 'new-password' : 'current-password'}
          hint={isRegister ? PASSWORD_HINT : undefined}
          errorCode={errors.password}
        />
        {serverError != null && <ErrorMessage error={serverError} />}
        <div className="form__actions">
          <button type="submit" className="button" disabled={submitting}>
            {submitting ? 'لطفاً صبر کنید…' : isRegister ? 'ساخت حساب و ورود' : 'ورود'}
          </button>
        </div>
        <p className="meta">
          {isRegister ? (
            <>
              حساب دارید؟ <Link to={`/login?next=${encodeURIComponent(next)}`}>وارد شوید</Link>
            </>
          ) : (
            <>
              حساب ندارید؟ <Link to={`/register?next=${encodeURIComponent(next)}`}>یک حساب بسازید</Link>
            </>
          )}
        </p>
      </form>
    </main>
  );
}
