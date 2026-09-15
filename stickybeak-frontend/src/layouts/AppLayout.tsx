import { Link, Outlet } from 'react-router-dom';
import { Badge, Segmented } from 'antd';
import { ShoppingCartOutlined } from '@ant-design/icons';
import { useAppDispatch, useAppSelector } from '../app/hooks';
import { selectCartCount } from '../features/cart/cartSlice';
import { setCurrency } from '../features/currency/currencySlice';
import type { Currency } from '../features/currency/currencySlice';

export default function AppLayout() {
  const cartCount = useAppSelector(selectCartCount);
  const currency = useAppSelector((s) => s.currency.currency);
  const dispatch = useAppDispatch();

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
            <Link to="/login" className="text-sm hover:underline">
              Sign in
            </Link>
          </div>
        </div>
      </header>
      <main className="flex-1 mx-auto w-full px-4 py-6" style={{ maxWidth: 'var(--container-max)' }}>
        <Outlet />
      </main>
      <footer className="border-t py-6 text-center text-xs text-gray-500">
        StickyBeak — Aussie-themed fridge magnets · Sprint 0 skeleton
      </footer>
    </div>
  );
}
