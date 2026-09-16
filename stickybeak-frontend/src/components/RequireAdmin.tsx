import { Navigate, Outlet, useLocation } from 'react-router-dom';
import { Spin } from 'antd';
import { useAppSelector } from '../app/hooks';

/**
 * Route guard: requires authenticated user with admin or sysadmin role.
 */
export default function RequireAdmin() {
  const { user, initialized } = useAppSelector((s) => s.auth);
  const location = useLocation();

  if (!initialized) {
    return (
      <div className="flex justify-center py-20">
        <Spin size="large" />
      </div>
    );
  }

  if (!user) {
    return <Navigate to="/login" state={{ from: location.pathname }} replace />;
  }

  const isAdmin = user.roles && user.roles.some((r) => r === 'admin' || r === 'sysadmin');
  if (!isAdmin) {
    return <Navigate to="/" replace />;
  }

  return <Outlet />;
}
