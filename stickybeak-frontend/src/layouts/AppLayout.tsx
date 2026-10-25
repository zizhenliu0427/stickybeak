import { useEffect, useState } from 'react';
import { Link, Outlet, useLocation, useNavigate } from 'react-router-dom';
import { Avatar, Badge, Button, Drawer, Dropdown, Empty, Segmented, ConfigProvider, theme as antdTheme } from 'antd';
import {
  LogoutOutlined,
  ShoppingCartOutlined,
  UserOutlined,
  OrderedListOutlined,
  DashboardOutlined,
  SunOutlined,
  MoonOutlined,
  DesktopOutlined,
} from '@ant-design/icons';
import { useAppDispatch, useAppSelector } from '../app/hooks';
import { logout } from '../features/auth/authSlice';
import { clearCart, mergeCartOnLogin, selectCartCount, selectCartTotalCents } from '../features/cart/cartSlice';
import { fetchLiveExchangeRate, setCurrency } from '../features/currency/currencySlice';
import type { Currency } from '../features/currency/currencySlice';
import { setLocale } from '../features/locale/localeSlice';
import type { Locale } from '../lib/i18n';
import { setTheme, syncSystemTheme } from '../features/theme/themeSlice';
import type { ThemeMode } from '../features/theme/themeSlice';
import { GDCUP_XHS, PADDYS_MARKET, SYDNEY_SOUVENIRS_XHS } from '../lib/brand';
import { useI18n } from '../lib/i18n';
import { productName } from '../lib/productCopy';
import Price from '../components/Price';
import OfflineBanner from '../components/OfflineBanner';
import AgentChat from '../components/AgentChat';

export default function AppLayout() {
  const cartCount = useAppSelector(selectCartCount);
  const cartTotalCents = useAppSelector(selectCartTotalCents);
  const cartItems = useAppSelector((s) => s.cart.items);
  const currency = useAppSelector((s) => s.currency.currency);
  const locale = useAppSelector((s) => s.locale.locale);
  const themeMode = useAppSelector((s) => s.theme.mode);
  const isDark = useAppSelector((s) => s.theme.isDark);
  const user = useAppSelector((s) => s.auth.user);
  const dispatch = useAppDispatch();
  const navigate = useNavigate();
  const location = useLocation();
  const [cartOpen, setCartOpen] = useState(false);
  const { t } = useI18n();
  const immersive = location.pathname === '/' || location.pathname === '/products';

  const sectionLinks = [
    { id: 'craft', label: t('nav.craft') },
    { id: 'story', label: t('nav.story') },
    { id: 'visit', label: t('nav.about') },
  ];

  const goSection = (id: string) => {
    if (location.pathname === '/') {
      document.getElementById(id)?.scrollIntoView({ behavior: 'smooth' });
      return;
    }
    navigate({ pathname: '/', hash: id });
  };

  // 拉取实时汇率（登录态探测在 main.tsx 启动时做，管理后台不经过这个布局）
  useEffect(() => {
    dispatch(fetchLiveExchangeRate());
  }, [dispatch]);

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
          colorPrimary: '#1c1916',
          colorText: isDark ? '#f3efe6' : '#1c1916',
          borderRadius: 0,
          fontFamily: 'Outfit, PingFang SC, Noto Sans SC, Microsoft YaHei, sans-serif',
        },
      }}
    >
      <div className={`flex min-h-screen flex-col overflow-x-hidden ${isDark ? 'dark bg-[#141311] text-[#f3efe6]' : 'bg-[#f6f1e8] text-[#1c1916]'}`}>
        <OfflineBanner />
        <header className="sticky top-0 z-30 border-b border-black/10 bg-[rgba(246,241,232,0.94)] backdrop-blur-md dark:border-white/10 dark:bg-[rgba(20,19,17,0.94)]">
          <div className="relative mx-auto flex h-[4.5rem] items-center px-5 sm:px-8 lg:px-12">
            <Link to="/" className="font-display text-[1.65rem] leading-none tracking-[0.14em]">
              {t('nav.brand')}
            </Link>
            {/* 绝对居中：1024–1279px 会压到右侧按钮组，所以 xl 才放进顶栏，更窄时用下方横向栏 */}
            <nav className="absolute left-1/2 hidden -translate-x-1/2 items-center gap-8 text-[13px] tracking-wide xl:flex">
              <Link to="/products" className="hover:opacity-60">
                {t('nav.series')}
              </Link>
              {sectionLinks.map((item) => (
                <button
                  key={item.id}
                  type="button"
                  onClick={() => goSection(item.id)}
                  className="hover:opacity-60"
                >
                  {item.label}
                </button>
              ))}
            </nav>
            <div className="ml-auto flex items-center gap-2 sm:gap-4">
              <Dropdown
                popupRender={() => (
                  <div className="w-64 space-y-4 border border-black/10 bg-[#f6f1e8] p-4 text-[#1c1916] shadow-sm dark:border-white/10 dark:bg-[#1c1a17] dark:text-[#f3efe6]">
                    <div className="flex items-center justify-between gap-3">
                      <span className="text-xs tracking-wide">{t('mobile.language')}</span>
                      <Segmented
                        size="small"
                        value={locale}
                        options={[
                          { label: 'EN', value: 'en-AU' },
                          { label: '中文', value: 'zh-CN' },
                        ]}
                        onChange={(v) => dispatch(setLocale(v as Locale))}
                      />
                    </div>
                    <div className="flex items-center justify-between gap-3">
                      <span className="text-xs tracking-wide">{t('mobile.currency')}</span>
                      <Segmented
                        size="small"
                        value={currency}
                        options={['AUD', 'CNY']}
                        onChange={(v) => dispatch(setCurrency(v as Currency))}
                      />
                    </div>
                    <div className="flex items-center justify-between gap-3">
                      <span className="text-xs tracking-wide">{t('mobile.theme')}</span>
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
                    </div>
                  </div>
                )}
              >
                <button type="button" className="text-[11px] tracking-[0.16em]">
                  {locale === 'zh-CN' ? '中文' : 'EN'} · {currency}
                </button>
              </Dropdown>

              <button
                onClick={() => setCartOpen(true)}
                aria-label={t('nav.trolley')}
                className="flex h-11 w-11 cursor-pointer items-center justify-center"
              >
                <Badge count={cartCount} size="small" color="#1c1916">
                  <ShoppingCartOutlined style={{ fontSize: 18 }} />
                </Badge>
              </button>

              {user ? (
                <Dropdown
                  menu={{
                    items: [
                      ...(user.roles && user.roles.some((r) => r === 'admin' || r === 'sysadmin')
                        ? [{ key: 'admin', icon: <DashboardOutlined />, label: t('admin.portal') }]
                        : []),
                      { key: 'profile', icon: <UserOutlined />, label: t('nav.profile') },
                      { key: 'orders', icon: <OrderedListOutlined />, label: t('nav.myOrders') },
                      { key: 'logout', icon: <LogoutOutlined />, label: t('nav.signOut') },
                    ],
                    onClick: ({ key }) => {
                      if (key === 'admin') {
                        navigate('/admin');
                      } else if (key === 'profile') {
                        navigate('/profile');
                      } else if (key === 'orders') {
                        navigate('/orders');
                      } else if (key === 'logout') {
                        onLogout();
                      }
                    },
                  }}
                >
                  <a className="flex min-h-[44px] items-center gap-2 p-1" onClick={(e) => e.preventDefault()}>
                    <Avatar size="small" icon={<UserOutlined />} src={user.avatarUrl ?? undefined} />
                    <span className="hidden text-sm lg:inline">{user.nickname}</span>
                  </a>
                </Dropdown>
              ) : (
                <Link to="/login" className="flex min-h-[44px] items-center px-1 text-[12px] tracking-[0.14em]">
                  {t('nav.signIn')}
                </Link>
              )}
              <Link
                to="/products"
                className="btn-ghost btn-ghost-ink hidden px-4 py-2 text-[12px] tracking-[0.16em] sm:inline-flex"
              >
                {t('nav.exploreCta')}
              </Link>
            </div>
          </div>
          <nav className="flex gap-6 overflow-x-auto border-t border-black/5 px-5 py-2.5 text-[13px] xl:hidden dark:border-white/10">
            <Link to="/products" className="shrink-0">
              {t('nav.series')}
            </Link>
            {sectionLinks.map((item) => (
              <button key={item.id} type="button" onClick={() => goSection(item.id)} className="shrink-0">
                {item.label}
              </button>
            ))}
          </nav>
        </header>

        <main
          className={
            immersive
              ? 'w-full flex-1'
              : 'mx-auto w-full flex-1 px-4 py-10 sm:px-6'
          }
          style={immersive ? undefined : { maxWidth: 'var(--container-max)' }}
        >
          <Outlet />
        </main>

        <footer className="bg-[#161412] text-[#f4efe6]">
          <div className="mx-auto grid max-w-[1440px] gap-12 px-6 py-16 sm:px-10 md:grid-cols-4 lg:px-16">
            <div>
              <p className="font-display text-3xl tracking-[0.12em]">{t('nav.brand')}</p>
              <p className="mt-3 text-sm text-[#f4efe6]/70">{t('footer.tagline')}</p>
              <p className="mt-2 max-w-xs text-sm leading-6 text-[#f4efe6]/55">{t('footer.blurb')}</p>
            </div>
            <div>
              <p className="text-[11px] tracking-[0.22em] text-[#f4efe6]/45">{t('footer.explore')}</p>
              <ul className="mt-4 space-y-3 text-sm">
                <li>
                  <Link to="/products" className="hover:opacity-70">
                    {t('nav.series')}
                  </Link>
                </li>
                {sectionLinks.map((item) => (
                  <li key={item.id}>
                    <button type="button" onClick={() => goSection(item.id)} className="hover:opacity-70">
                      {item.label}
                    </button>
                  </li>
                ))}
              </ul>
            </div>
            <div>
              <p className="text-[11px] tracking-[0.22em] text-[#f4efe6]/45">{t('footer.shopCol')}</p>
              <ul className="mt-4 space-y-3 text-sm">
                <li>
                  <Link to="/cart" className="hover:opacity-70">
                    {t('nav.trolley')}
                  </Link>
                </li>
                <li>
                  <Link to="/orders" className="hover:opacity-70">
                    {t('nav.myOrders')}
                  </Link>
                </li>
              </ul>
            </div>
            <div>
              <p className="text-[11px] tracking-[0.22em] text-[#f4efe6]/45">{t('footer.contact')}</p>
              <ul className="mt-4 space-y-3 text-sm">
                <li>
                  <a href={GDCUP_XHS.profileUrl} target="_blank" rel="noopener noreferrer" className="hover:opacity-70">
                    {t('footer.xhs')}
                  </a>
                </li>
                <li>
                  <a
                    href={SYDNEY_SOUVENIRS_XHS.profileUrl}
                    target="_blank"
                    rel="noopener noreferrer"
                    className="hover:opacity-70"
                  >
                    {t('footer.partnerXhs')}
                  </a>
                </li>
                <li>
                  <a href={PADDYS_MARKET.mapsUrl} target="_blank" rel="noopener noreferrer" className="hover:opacity-70">
                    {t('footer.offline')}
                  </a>
                </li>
              </ul>
            </div>
          </div>
          <div className="mx-auto flex max-w-[1440px] flex-col gap-2 border-t border-white/10 px-6 py-5 text-[11px] tracking-wide text-[#f4efe6]/45 sm:flex-row sm:items-center sm:justify-between sm:px-10 lg:px-16">
            <p>{t('footer.copyright', { year: new Date().getFullYear() })}</p>
            <p>{t('footer.signoff')}</p>
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
                    alt={productName(item.slug, item.name, locale)}
                    className="h-14 w-14 rounded-lg border border-sand-200 object-cover dark:border-stone-700"
                  />
                  <div className="flex-1">
                    <p className="line-clamp-1 text-sm text-gray-800 dark:text-stone-200">
                      {productName(item.slug, item.name, locale)}
                    </p>
                    <p className="mt-1 text-xs text-gray-400 dark:text-stone-400">
                      × {item.qty} · <Price cents={item.priceCents * item.qty} />
                    </p>
                  </div>
                </li>
              ))}
            </ul>
          )}
        </Drawer>

        {/* AI 购物助手（右下角浮动按钮 + 抽屉） */}
        <AgentChat />
      </div>
    </ConfigProvider>
  );
}
