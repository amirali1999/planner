import { Link } from 'react-router';
import { usePageTitle } from '../shared/ui/usePageTitle';

export function NotFoundPage() {
  usePageTitle('صفحه پیدا نشد');
  return (
    <section>
      <h1>صفحه پیدا نشد</h1>
      <p>
        <Link to="/goals">بازگشت به اهداف</Link>
      </p>
    </section>
  );
}
