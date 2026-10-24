import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { Provider } from 'react-redux';
import { RouterProvider } from 'react-router-dom';
import { ConfigProvider } from 'antd';
import { store } from './app/store';
import { router } from './router';
import { setSessionExpiredHandler } from './lib/api';
import { clearUser, fetchMe } from './features/auth/authSlice';
import { registerServiceWorker } from './lib/pwa';
import './index.css';

// 注册 PWA Service Worker
registerServiceWorker();

// refresh 也失败 = 会话彻底过期，清掉本地用户态（路由守卫负责跳登录）
setSessionExpiredHandler(() => store.dispatch(clearUser()));

// 启动时探测一次登录态。之前放在商城布局 AppLayout 里，直接打开 / 刷新 /admin/* 时
// 没人发起探测，RequireAdmin 一直停在加载状态
store.dispatch(fetchMe());

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <Provider store={store}>
      <ConfigProvider>
        <RouterProvider router={router} />
      </ConfigProvider>
    </Provider>
  </StrictMode>,
);
