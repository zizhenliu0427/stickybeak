import { Navigate, Outlet, useLocation } from 'react-router-dom';
import { Spin } from 'antd';
import { useAppSelector } from '../app/hooks';

/** Route guard: redirects unauthenticated users to /login. */
export default function RequireAuth() {
  const { user, initialized } = useAppSelector((s) => s.auth);
  const location = useLocation();

  if (!initialized) {
    // 等待 /users/me 探测结果（刷新页面登录态不丢）
    return (
      <div className="flex justify-center py-20">
        <Spin size="large" />
      </div>
    );
  }
  if (!user) {
    return <Navigate to="/login" state={{ from: location.pathname }} replace />;
  }
  return <Outlet />;
}
