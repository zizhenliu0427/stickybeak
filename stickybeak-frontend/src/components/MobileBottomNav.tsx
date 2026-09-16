import { useState } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { Avatar, Badge, Button, Drawer, Segmented } from 'antd';
import {
  HomeOutlined,
  AppstoreOutlined,
  ShoppingCartOutlined,
  OrderedListOutlined,
  UserOutlined,
  DashboardOutlined,
  LogoutOutlined,
  SunOutlined,
  MoonOutlined,
  DesktopOutlined,
} from '@ant-design/icons';
import { useAppDispatch, useAppSelector } from '../app/hooks';
import { selectCartCount, clearCart } from '../features/cart/cartSlice';
import { logout } from '../features/auth/authSlice';
import { setLocale } from '../features/locale/localeSlice';
import type { Locale } from '../lib/i18n';
import { setCurrency } from '../features/currency/currencySlice';
import type { Currency } from '../features/currency/currencySlice';
import { setTheme } from '../features/theme/themeSlice';
import type { ThemeMode } from '../features/theme/themeSlice';
import { useI18n } from '../lib/i18n';

export default function MobileBottomNav() {
  const location = useLocation();
  const navigate = useNavigate();
  const dispatch = useAppDispatch();
  const { t } = useI18n();

  const cartCount = useAppSelector(selectCartCount);
  const { user } = useAppSelector((s) => s.auth);
  const locale = useAppSelector((s) => s.locale.locale);
  const currency = useAppSelector((s) => s.currency.currency);
  const themeMode = useAppSelector((s) => s.theme.mode);

  const [drawerOpen, setDrawerOpen] = useState(false);

  const isActive = (path: string) => {
    if (path === '/') return location.pathname === '/';
    return location.pathname.startsWith(path);
  };

  const onLogout = async () => {
    await dispatch(logout());
    dispatch(clearCart());
    setDrawerOpen(false);
    navigate('/');
  };

  const navItems = [
    {
      key: 'home',
      label: t('mobile.home'),
      icon: <HomeOutlined className="text-lg" />,
      path: '/',
      onClick: () => navigate('/'),
    },
    {
      key: 'shop',
      label: t('mobile.catalogue'),
      icon: <AppstoreOutlined className="text-lg" />,
      path: '/products',
      onClick: () => navigate('/products'),
    },
    {
      key: 'cart',
      label: t('mobile.trolley'),
      icon: (
        <Badge count={cartCount} size="small" offset={[4, -2]}>
          <ShoppingCartOutlined className="text-lg" />
        </Badge>
      ),
      path: '/cart',
      onClick: () => navigate('/cart'),
    },
    {
      key: 'orders',
      label: t('mobile.orders'),
      icon: <OrderedListOutlined className="text-lg" />,
      path: '/orders',
      onClick: () => navigate('/orders'),
    },
    {
      key: 'account',
      label: t('mobile.account'),
      icon: <UserOutlined className="text-lg" />,
      path: '/account',
      onClick: () => setDrawerOpen(true),
    },
  ];

  return (
    <>
      {/* 沉浸式移动端底部导航栏（仅在 < sm 屏幕可见，触控高度 56px 满足 >= 44px 标准） */}
      <nav
        aria-label="Mobile Navigation"
        className="fixed bottom-0 left-0 right-0 z-40 border-t border-sand-200 bg-white/95 pb-[env(safe-area-inset-bottom)] backdrop-blur sm:hidden dark:border-stone-800 dark:bg-stone-900/95"
      >
        <div className="flex h-14 items-center justify-around">
          {navItems.map((item) => {
            const active = item.key === 'account' ? drawerOpen : isActive(item.path);
            return (
              <button
                key={item.key}
                type="button"
                onClick={item.onClick}
                className={`flex flex-1 flex-col items-center justify-center py-1 transition-colors ${
                  active
                    ? 'text-brand-700 dark:text-brand-400 font-semibold'
                    : 'text-gray-500 hover:text-gray-900 dark:text-stone-400 dark:hover:text-stone-200'
                }`}
              >
                <div className="flex h-6 items-center justify-center">{item.icon}</div>
                <span className="text-[11px] leading-tight tracking-tight">{item.label}</span>
              </button>
            );
          })}
        </div>
      </nav>

      {/* 移动端个人中心与偏好设置抽屉 */}
      <Drawer
        title={t('mobile.settings')}
        placement="bottom"
        open={drawerOpen}
        onClose={() => setDrawerOpen(false)}
        height="auto"
        className="rounded-t-2xl"
      >
        <div className="space-y-6 pb-6 pt-2">
          {/* 用户信息卡片 */}
          <div className="flex items-center gap-3 rounded-xl bg-sand-50 p-4 dark:bg-stone-800/60">
            <Avatar size={48} icon={<UserOutlined />} src={user?.avatarUrl ?? undefined} />
            <div className="flex-1 min-w-0">
              {user ? (
                <>
                  <p className="truncate font-semibold text-gray-900 dark:text-stone-100">{user.nickname}</p>
                  <p className="truncate text-xs text-gray-400 dark:text-stone-400">{user.email}</p>
                </>
              ) : (
                <>
                  <p className="font-semibold text-gray-800 dark:text-stone-200">{t('nav.signIn')}</p>
                  <p className="text-xs text-gray-400 dark:text-stone-400">
                    {locale === 'en-AU' ? 'Sign in for order tracking and checkout' : '登录后即可结算并同步订单'}
                  </p>
                </>
              )}
            </div>
            {user ? (
              <Button danger size="small" icon={<LogoutOutlined />} onClick={onLogout}>
                {t('nav.signOut')}
              </Button>
            ) : (
              <Button
                type="primary"
                size="small"
                onClick={() => {
                  setDrawerOpen(false);
                  navigate('/login');
                }}
              >
                {t('nav.signIn')}
              </Button>
            )}
          </div>

          {/* 管理员入口 */}
          {user?.roles?.some((r) => r === 'admin' || r === 'sysadmin') && (
            <Button
              block
              icon={<DashboardOutlined />}
              onClick={() => {
                setDrawerOpen(false);
                navigate('/admin');
              }}
              className="border-brand-500 text-brand-700 dark:border-brand-400 dark:text-brand-300"
            >
              {t('admin.portal')}
            </Button>
          )}

          {/* 系统偏好配置（语言 / 币种 / 主题） */}
          <div className="space-y-4 rounded-xl border border-sand-200 p-4 dark:border-stone-800">
            <div className="flex items-center justify-between">
              <span className="text-sm font-medium text-gray-700 dark:text-stone-300">{t('mobile.language')}</span>
              <Segmented
                size="middle"
                value={locale}
                options={[
                  { label: '🇦🇺 EN', value: 'en-AU' },
                  { label: '🇨🇳 中文', value: 'zh-CN' },
                ]}
                onChange={(v) => dispatch(setLocale(v as Locale))}
              />
            </div>

            <div className="flex items-center justify-between">
              <span className="text-sm font-medium text-gray-700 dark:text-stone-300">{t('mobile.currency')}</span>
              <Segmented
                size="middle"
                value={currency}
                options={['AUD', 'CNY']}
                onChange={(v) => dispatch(setCurrency(v as Currency))}
              />
            </div>

            <div className="flex items-center justify-between">
              <span className="text-sm font-medium text-gray-700 dark:text-stone-300">{t('mobile.theme')}</span>
              <Segmented
                size="middle"
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
        </div>
      </Drawer>
    </>
  );
}
