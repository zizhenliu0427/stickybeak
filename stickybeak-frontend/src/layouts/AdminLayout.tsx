import { useState } from 'react';
import { Outlet, useLocation, useNavigate } from 'react-router-dom';
import { Layout, Menu, Button, Dropdown, Avatar, Tag, theme } from 'antd';
import {
  DashboardOutlined,
  ShoppingOutlined,
  AppstoreOutlined,
  RollbackOutlined,
  MenuFoldOutlined,
  MenuUnfoldOutlined,
  UserOutlined,
  GlobalOutlined,
  SunOutlined,
  MoonOutlined,
  LogoutOutlined,
} from '@ant-design/icons';
import { useAppDispatch, useAppSelector } from '../app/hooks';
import { logout } from '../features/auth/authSlice';
import { setLocale } from '../features/locale/localeSlice';
import { setTheme } from '../features/theme/themeSlice';
import { useI18n } from '../lib/i18n';

const { Header, Sider, Content } = Layout;

export default function AdminLayout() {
  const [collapsed, setCollapsed] = useState(false);
  const location = useLocation();
  const navigate = useNavigate();
  const dispatch = useAppDispatch();
  const { user } = useAppSelector((s) => s.auth);
  const themeMode = useAppSelector((s) => s.theme.mode);
  const { locale, t } = useI18n();

  const {
    token: { colorBgContainer, borderRadiusLG },
  } = theme.useToken();

  const selectedKey = location.pathname.startsWith('/admin/orders')
    ? '/admin/orders'
    : location.pathname.startsWith('/admin/products')
    ? '/admin/products'
    : '/admin/dashboard';

  const menuItems = [
    {
      key: '/admin/dashboard',
      icon: <DashboardOutlined />,
      label: t('admin.dashboard'),
    },
    {
      key: '/admin/orders',
      icon: <ShoppingOutlined />,
      label: t('admin.orders'),
    },
    {
      key: '/admin/products',
      icon: <AppstoreOutlined />,
      label: t('admin.products'),
    },
    {
      type: 'divider' as const,
    },
    {
      key: '/',
      icon: <RollbackOutlined />,
      label: t('admin.backToStore'),
    },
  ];

  const handleMenuClick = ({ key }: { key: string }) => {
    if (key === '/') {
      navigate('/');
    } else {
      navigate(key);
    }
  };

  const handleLogout = async () => {
    await dispatch(logout());
    navigate('/login');
  };

  const langItems = [
    {
      key: 'en-AU',
      label: 'English (AU)',
      onClick: () => dispatch(setLocale('en-AU')),
    },
    {
      key: 'zh-CN',
      label: '简体中文',
      onClick: () => dispatch(setLocale('zh-CN')),
    },
  ];

  const userMenuItems = [
    {
      key: 'back-to-shop',
      icon: <RollbackOutlined />,
      label: t('admin.backToStore'),
      onClick: () => navigate('/'),
    },
    {
      type: 'divider' as const,
    },
    {
      key: 'logout',
      icon: <LogoutOutlined />,
      label: t('nav.signOut'),
      danger: true,
      onClick: handleLogout,
    },
  ];

  return (
    <Layout className="min-h-screen">
      <Sider
        trigger={null}
        collapsible
        collapsed={collapsed}
        theme={themeMode === 'dark' ? 'dark' : 'light'}
        className="border-r border-gray-200 dark:border-gray-800"
      >
        <div className="flex items-center justify-center h-16 px-4 border-b border-gray-200 dark:border-gray-800">
          <span className="text-xl font-bold text-emerald-600 dark:text-emerald-400 truncate">
            {collapsed ? 'SB' : 'StickyBeak Admin'}
          </span>
        </div>
        <Menu
          theme={themeMode === 'dark' ? 'dark' : 'light'}
          mode="inline"
          selectedKeys={[selectedKey]}
          items={menuItems}
          onClick={handleMenuClick}
          className="border-r-0 mt-2"
        />
      </Sider>

      <Layout>
        <Header
          style={{ background: colorBgContainer }}
          className="px-6 flex items-center justify-between border-b border-gray-200 dark:border-gray-800 sticky top-0 z-10"
        >
          <Button
            type="text"
            icon={collapsed ? <MenuUnfoldOutlined /> : <MenuFoldOutlined />}
            onClick={() => setCollapsed(!collapsed)}
            className="text-lg w-10 h-10 flex items-center justify-center"
          />

          <div className="flex items-center gap-4">
            {/* 语言切换 */}
            <Dropdown menu={{ items: langItems }} placement="bottomRight">
              <Button type="text" icon={<GlobalOutlined />}>
                {locale === 'zh-CN' ? '中文' : 'EN'}
              </Button>
            </Dropdown>

            {/* 深浅色切换 */}
            <Button
              type="text"
              icon={themeMode === 'dark' ? <SunOutlined /> : <MoonOutlined />}
              onClick={() => dispatch(setTheme(themeMode === 'dark' ? 'light' : 'dark'))}
            />

            {/* 用户身份与菜单 */}
            <Dropdown menu={{ items: userMenuItems }} placement="bottomRight">
              <div className="flex items-center gap-2 cursor-pointer hover:opacity-80 transition-opacity">
                <Avatar icon={<UserOutlined />} className="bg-emerald-600" />
                <span className="font-medium text-sm text-gray-700 dark:text-gray-200 hidden sm:inline">
                  {user?.nickname || user?.email || 'Admin'}
                </span>
                <Tag color="green" className="mr-0 hidden md:inline">
                  ADMIN
                </Tag>
              </div>
            </Dropdown>
          </div>
        </Header>

        <Content className="m-6 p-6 min-h-[280px]" style={{ background: colorBgContainer, borderRadius: borderRadiusLG }}>
          <Outlet />
        </Content>
      </Layout>
    </Layout>
  );
}
