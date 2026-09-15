import { Navigate, Outlet, useLocation } from 'react-router-dom';
import { useAppSelector } from '../app/hooks';

/** Route guard: redirects unauthenticated users to /login. */
export default function RequireAuth() {
  const { user, initialized } = useAppSelector((s) => s.auth);
  const location = useLocation();

  if (!initialized) {
    return null; // TODO(Sprint 1): probe /users/me, show a splash while resolving
  }
  if (!user) {
    return <Navigate to="/login" state={{ from: location.pathname }} replace />;
  }
  return <Outlet />;
}
