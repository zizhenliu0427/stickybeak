import { useEffect } from 'react';
import { Link, Outlet, useNavigate } from 'react-router-dom';
import { Avatar, Badge, Dropdown, Segmented } from 'antd';
import { LogoutOutlined, ShoppingCartOutlined, UserOutlined } from '@ant-design/icons';
import { useAppDispatch, useAppSelector } from '../app/hooks';
import { fetchMe, logout } from '../features/auth/authSlice';
import { selectCartCount } from '../features/cart/cartSlice';
import { setCurrency } from '../features/currency/currencySlice';
import type { Currency } from '../features/currency/currencySlice';

export default function AppLayout() {
  const cartCount = useAppSelector(selectCartCount);
  const currency = useAppSelector((s) => s.currency.currency);
  const { user, initialized } = useAppSelector((s) => s.auth);
  const dispatch = useAppDispatch();
  const navigate = useNavigate();

  // 应用启动探测登录态（HttpOnly cookie，刷新页面不丢登录）
  useEffect(() => {
    if (!initialized) {
      dispatch(fetchMe());
    }
  }, [dispatch, initialized]);

  const onLogout = async () => {
    await dispatch(logout());
    navigate('/');
  };

  return (
    <div className="min-h-screen flex flex-col">
      <header className="sticky top-0 z-10 bg-white/90 backdrop-blur border-b">
        <div className="mx-auto flex h-14 items-center gap-6 px-4" style={{ maxWidth: 'var(--container-max)' }}>
          <Link to="/" className="text-xl font-bold tracking-tight">
            🧲 StickyBeak
          </Link>
          <nav className="flex items-center gap-4 text-sm">
            <Link to="/products" className="hover:underline">
              Shop
            </Link>
          </nav>
          <div className="ml-auto flex items-center gap-4">
            <Segmented
              size="small"
              value={currency}
              options={['AUD', 'CNY']}
              onChange={(v) => dispatch(setCurrency(v as Currency))}
            />
            <Link to="/cart" aria-label="cart">
              <Badge count={cartCount} size="small">
                <ShoppingCartOutlined style={{ fontSize: 20 }} />
              </Badge>
            </Link>
            {user ? (
              <Dropdown
                menu={{
                  items: [
                    { key: 'profile', icon: <UserOutlined />, label: 'Profile' },
                    { key: 'logout', icon: <LogoutOutlined />, label: 'Sign out' },
                  ],
                  onClick: ({ key }) => {
                    if (key === 'profile') {
                      navigate('/profile');
                    } else if (key === 'logout') {
                      onLogout();
                    }
                  },
                }}
              >
                <a className="flex items-center gap-2" onClick={(e) => e.preventDefault()}>
                  <Avatar size="small" icon={<UserOutlined />} src={user.avatarUrl ?? undefined} />
                  <span className="text-sm">{user.nickname}</span>
                </a>
              </Dropdown>
            ) : (
              <Link to="/login" className="text-sm hover:underline">
                Sign in
              </Link>
            )}
          </div>
        </div>
      </header>
      <main className="flex-1 mx-auto w-full px-4 py-6" style={{ maxWidth: 'var(--container-max)' }}>
        <Outlet />
      </main>
      <footer className="border-t py-6 text-center text-xs text-gray-500">
        StickyBeak — Aussie-themed fridge magnets · v0.1.0 Sprint 1
      </footer>
    </div>
  );
}
