import { useEffect, useState } from 'react';
import { Link, Outlet, useNavigate } from 'react-router-dom';
import { Avatar, Badge, Button, Drawer, Dropdown, Empty, Segmented } from 'antd';
import { LogoutOutlined, ShoppingCartOutlined, UserOutlined } from '@ant-design/icons';
import { useAppDispatch, useAppSelector } from '../app/hooks';
import { fetchMe, logout } from '../features/auth/authSlice';
import { selectCartCount, selectCartTotalCents } from '../features/cart/cartSlice';
import { setCurrency } from '../features/currency/currencySlice';
import type { Currency } from '../features/currency/currencySlice';
import Price from '../components/Price';

export default function AppLayout() {
  const cartCount = useAppSelector(selectCartCount);
  const cartTotalCents = useAppSelector(selectCartTotalCents);
  const cartItems = useAppSelector((s) => s.cart.items);
  const currency = useAppSelector((s) => s.currency.currency);
  const { user, initialized } = useAppSelector((s) => s.auth);
  const dispatch = useAppDispatch();
  const navigate = useNavigate();
  const [cartOpen, setCartOpen] = useState(false);

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
    <div className="flex min-h-screen flex-col">
      <header className="sticky top-0 z-20 border-b border-sand-200 bg-sand-50/90 backdrop-blur">
        <div className="mx-auto flex h-14 items-center gap-6 px-4" style={{ maxWidth: 'var(--container-max)' }}>
          <Link to="/" className="text-xl font-bold tracking-tight text-brand-800">
            🧲 StickyBeak
          </Link>
          <nav className="flex items-center gap-4 text-sm text-gray-700">
            <Link to="/products" className="hover:text-brand-700">
              Shop
            </Link>
            <Link to="/products?category=bird" className="hover:text-brand-700">
              小鸟系列
            </Link>
            <Link to="/products?category=bus-sign" className="hover:text-brand-700">
              大学路牌
            </Link>
          </nav>
          <div className="ml-auto flex items-center gap-4">
            <Segmented
              size="small"
              value={currency}
              options={['AUD', 'CNY']}
              onChange={(v) => dispatch(setCurrency(v as Currency))}
            />
            <button onClick={() => setCartOpen(true)} aria-label="cart" className="cursor-pointer">
              <Badge count={cartCount} size="small">
                <ShoppingCartOutlined style={{ fontSize: 20 }} />
              </Badge>
            </button>
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

      <main className="mx-auto w-full flex-1 px-4 py-6" style={{ maxWidth: 'var(--container-max)' }}>
        <Outlet />
      </main>

      <footer className="border-t border-sand-200 bg-white py-8 text-center text-xs text-gray-500">
        <div className="space-y-1">
          <p className="font-medium text-gray-600">StickyBeak — Aussie-themed fridge magnets</p>
          <p>公交站牌 · 悉尼火车 · 超市特价 · 抢食小鸟 · 数据素材：小红书 GDCUP（已授权）</p>
        </div>
      </footer>

      {/* 迷你购物车抽屉 */}
      <Drawer
        title="购物车"
        open={cartOpen}
        onClose={() => setCartOpen(false)}
        width={380}
        footer={
          cartItems.length > 0 ? (
            <div className="space-y-3">
              <div className="flex justify-between text-sm">
                <span className="text-gray-500">小计</span>
                <Price cents={cartTotalCents} className="font-bold text-gray-900" />
              </div>
              <Button
                type="primary"
                block
                onClick={() => {
                  setCartOpen(false);
                  navigate('/cart');
                }}
              >
                查看购物车并结账
              </Button>
            </div>
          ) : undefined
        }
      >
        {cartItems.length === 0 ? (
          <Empty description="购物车是空的">
            <Button
              onClick={() => {
                setCartOpen(false);
                navigate('/products');
              }}
            >
              去逛逛
            </Button>
          </Empty>
        ) : (
          <ul className="space-y-4">
            {cartItems.map((item) => (
              <li key={item.productId} className="flex gap-3">
                <img
                  src={item.imageUrl}
                  alt={item.name}
                  className="h-14 w-14 rounded-lg border border-sand-200 object-cover"
                />
                <div className="flex-1">
                  <p className="line-clamp-1 text-sm text-gray-800">{item.name}</p>
                  <p className="mt-1 text-xs text-gray-400">
                    × {item.qty} · <Price cents={item.priceCents * item.qty} />
                  </p>
                </div>
              </li>
            ))}
          </ul>
        )}
      </Drawer>
    </div>
  );
}
