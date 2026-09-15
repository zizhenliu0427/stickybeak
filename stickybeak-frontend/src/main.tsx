import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { Provider } from 'react-redux';
import { RouterProvider } from 'react-router-dom';
import { ConfigProvider } from 'antd';
import { store } from './app/store';
import { router } from './router';
import { setSessionExpiredHandler } from './lib/api';
import { clearUser } from './features/auth/authSlice';
import './index.css';

// refresh 也失败 = 会话彻底过期，清掉本地用户态（路由守卫负责跳登录）
setSessionExpiredHandler(() => store.dispatch(clearUser()));

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <Provider store={store}>
      <ConfigProvider>
        <RouterProvider router={router} />
      </ConfigProvider>
    </Provider>
  </StrictMode>,
);
