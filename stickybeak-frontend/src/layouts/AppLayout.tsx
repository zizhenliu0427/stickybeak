import { useEffect, useState } from 'react';
import { Link, Outlet, useNavigate } from 'react-router-dom';
import { Avatar, Badge, Button, Drawer, Dropdown, Empty, Segmented, ConfigProvider, theme as antdTheme } from 'antd';
import {
  LogoutOutlined,
  ShoppingCartOutlined,
  UserOutlined,
  SunOutlined,
  MoonOutlined,
  DesktopOutlined,
} from '@ant-design/icons';
import { useAppDispatch, useAppSelector } from '../app/hooks';
import { fetchMe, logout } from '../features/auth/authSlice';
import { clearCart, mergeCartOnLogin, selectCartCount, selectCartTotalCents } from '../features/cart/cartSlice';
import { fetchLiveExchangeRate, setCurrency } from '../features/currency/currencySlice';
import type { Currency } from '../features/currency/currencySlice';
import { setLocale } from '../features/locale/localeSlice';
import type { Locale } from '../lib/i18n';
import { setTheme, syncSystemTheme } from '../features/theme/themeSlice';
import type { ThemeMode } from '../features/theme/themeSlice';
import { useI18n } from '../lib/i18n';
import Price from '../components/Price';

export default function AppLayout() {
  const cartCount = useAppSelector(selectCartCount);
  const cartTotalCents = useAppSelector(selectCartTotalCents);
  const cartItems = useAppSelector((s) => s.cart.items);
  const currency = useAppSelector((s) => s.currency.currency);
  const locale = useAppSelector((s) => s.locale.locale);
  const themeMode = useAppSelector((s) => s.theme.mode);
  const isDark = useAppSelector((s) => s.theme.isDark);
  const { user, initialized } = useAppSelector((s) => s.auth);
  const dispatch = useAppDispatch();
  const navigate = useNavigate();
  const [cartOpen, setCartOpen] = useState(false);
  const { t } = useI18n();

  // 应用启动探测登录态并拉取实时汇率
  useEffect(() => {
    if (!initialized) {
      dispatch(fetchMe());
    }
    dispatch(fetchLiveExchangeRate());
  }, [dispatch, initialized]);

  // 监听操作系统深浅色设置变化（System 模式下即时自适应）
  useEffect(() => {
    if (typeof window === 'undefined' || !window.matchMedia) return;
    const mediaQuery = window.matchMedia('(prefers-color-scheme: dark)');
    const handler = () => dispatch(syncSystemTheme());
    mediaQuery.addEventListener('change', handler);
    return () => mediaQuery.removeEventListener('change', handler);
  }, [dispatch]);

  // 登录后将本地游客购物车与服务端合并，并拉取最新购物车
  useEffect(() => {
    if (user) {
      dispatch(mergeCartOnLogin());
    }
  }, [dispatch, user]);

  const onLogout = async () => {
    await dispatch(logout());
    dispatch(clearCart());
    navigate('/');
  };

  return (
    <ConfigProvider
      theme={{
        algorithm: isDark ? antdTheme.darkAlgorithm : antdTheme.defaultAlgorithm,
        token: {
          colorPrimary: '#3d8066',
          borderRadius: 8,
        },
      }}
    >
      <div className={`flex min-h-screen flex-col ${isDark ? 'dark bg-[#141716] text-[#f5f5f4]' : 'bg-[#faf8f4] text-[#1c1917]'}`}>
        <header className="sticky top-0 z-20 border-b border-sand-200 bg-sand-50/90 backdrop-blur dark:border-stone-800 dark:bg-stone-900/90">
          <div className="mx-auto flex h-14 items-center gap-4 px-4 sm:gap-6" style={{ maxWidth: 'var(--container-max)' }}>
            <Link to="/" className="text-xl font-bold tracking-tight text-brand-800 dark:text-brand-300">
              🧲 {t('nav.brand')}
            </Link>
            <nav className="flex items-center gap-3 text-sm text-gray-700 sm:gap-4 dark:text-stone-300">
              <Link to="/products" className="hover:text-brand-700 dark:hover:text-brand-300">
                {t('nav.shop')}
              </Link>
              <Link to="/products?category=bird" className="hover:text-brand-700 dark:hover:text-brand-300">
                {t('nav.bird')}
              </Link>
              <Link to="/products?category=bus-sign" className="hover:text-brand-700 dark:hover:text-brand-300">
                {t('nav.busSign')}
              </Link>
            </nav>
            <div className="ml-auto flex items-center gap-2 sm:gap-3">
              {/* 语言切换器（澳洲英式英语 en-AU vs 中文 zh-CN） */}
              <Segmented
                size="small"
                value={locale}
                options={[
                  { label: '🇦🇺 EN', value: 'en-AU' },
                  { label: '🇨🇳 中文', value: 'zh-CN' },
                ]}
                onChange={(v) => dispatch(setLocale(v as Locale))}
              />

              {/* 币种切换器（AUD / CNY） */}
              <Segmented
                size="small"
                value={currency}
                options={['AUD', 'CNY']}
                onChange={(v) => dispatch(setCurrency(v as Currency))}
              />

              {/* 深浅色自适应切换器（Light / Dark / System） */}
              <Segmented
                size="small"
                value={themeMode}
                options={[
                  { label: <SunOutlined title={t('theme.light')} />, value: 'light' },
                  { label: <MoonOutlined title={t('theme.dark')} />, value: 'dark' },
                  { label: <DesktopOutlined title={t('theme.system')} />, value: 'system' },
                ]}
                onChange={(v) => dispatch(setTheme(v as ThemeMode))}
              />

              {/* 购物车（Trolley）按钮 */}
              <button
                onClick={() => setCartOpen(true)}
                aria-label={t('nav.trolley')}
                className="cursor-pointer p-1 text-gray-700 hover:text-brand-700 dark:text-stone-300 dark:hover:text-brand-300"
              >
                <Badge count={cartCount} size="small">
                  <ShoppingCartOutlined style={{ fontSize: 20 }} />
                </Badge>
              </button>

              {/* 用户信息 / 登录 */}
              {user ? (
                <Dropdown
                  menu={{
                    items: [
                      { key: 'profile', icon: <UserOutlined />, label: t('nav.profile') },
                      { key: 'logout', icon: <LogoutOutlined />, label: t('nav.signOut') },
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
                    <span className="hidden text-sm sm:inline">{user.nickname}</span>
                  </a>
                </Dropdown>
              ) : (
                <Link to="/login" className="text-sm hover:underline dark:text-stone-300">
                  {t('nav.signIn')}
                </Link>
              )}
            </div>
          </div>
        </header>

        <main className="mx-auto w-full flex-1 px-4 py-6" style={{ maxWidth: 'var(--container-max)' }}>
          <Outlet />
        </main>

        <footer className="border-t border-sand-200 bg-white py-8 text-center text-xs text-gray-500 dark:border-stone-800 dark:bg-stone-900 dark:text-stone-400">
          <div className="space-y-1">
            <p className="font-medium text-gray-600 dark:text-stone-300">{t('footer.brand')}</p>
            <p>{t('footer.desc')}</p>
            <p className="text-gray-400 dark:text-stone-500">{t('footer.copyright', { year: new Date().getFullYear() })}</p>
          </div>
        </footer>

        {/* 迷你购物车（Trolley）抽屉 */}
        <Drawer
          title={t('common.trolley')}
          open={cartOpen}
          onClose={() => setCartOpen(false)}
          width={380}
          footer={
            cartItems.length > 0 ? (
              <div className="space-y-3">
                <div className="flex justify-between text-sm">
                  <span className="text-gray-500 dark:text-stone-400">{t('common.subtotal')}</span>
                  <Price cents={cartTotalCents} className="font-bold text-gray-900 dark:text-stone-100" />
                </div>
                <Button
                  type="primary"
                  block
                  onClick={() => {
                    setCartOpen(false);
                    navigate('/cart');
                  }}
                >
                  {t('common.viewTrolley')}
                </Button>
              </div>
            ) : undefined
          }
        >
          {cartItems.length === 0 ? (
            <Empty description={t('common.emptyTrolley')}>
              <Button
                onClick={() => {
                  setCartOpen(false);
                  navigate('/products');
                }}
              >
                {t('common.haveABrowse')}
              </Button>
            </Empty>
          ) : (
            <ul className="space-y-4">
              {cartItems.map((item) => (
                <li key={item.productId} className="flex gap-3">
                  <img
                    src={item.imageUrl}
                    alt={item.name}
                    className="h-14 w-14 rounded-lg border border-sand-200 object-cover dark:border-stone-700"
                  />
                  <div className="flex-1">
                    <p className="line-clamp-1 text-sm text-gray-800 dark:text-stone-200">{item.name}</p>
                    <p className="mt-1 text-xs text-gray-400 dark:text-stone-400">
                      × {item.qty} · <Price cents={item.priceCents * item.qty} />
                    </p>
                  </div>
                </li>
              ))}
            </ul>
          )}
        </Drawer>
      </div>
    </ConfigProvider>
  );
}
