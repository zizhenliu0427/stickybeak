import { render, screen } from '@testing-library/react';
import { Provider } from 'react-redux';
import { configureStore } from '@reduxjs/toolkit';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import authReducer from '../features/auth/authSlice';
import type { AuthUser } from '../features/auth/authSlice';
import RequireAuth from '../components/RequireAuth';

/**
 * Issue 1.4 验收：
 * - 未登录访问 /profile → 跳 /login
 * - 登录态就绪前显示加载（防闪跳）
 * - 已登录 → 渲染受保护内容
 *
 * 用经典 MemoryRouter 而非 createMemoryRouter：data router 导航内部 new Request()
 * 会撞 jsdom 与 Node undici 的 AbortSignal 不兼容问题。
 */
function renderGuarded(authState: { user: AuthUser | null; initialized: boolean }) {
  const store = configureStore({
    reducer: { auth: authReducer },
    preloadedState: { auth: { ...authState, error: null } },
  });
  render(
    <Provider store={store}>
      <MemoryRouter initialEntries={['/profile']}>
        <Routes>
          <Route element={<RequireAuth />}>
            <Route path="/profile" element={<div>secret content</div>} />
          </Route>
          <Route path="/login" element={<div>login page</div>} />
        </Routes>
      </MemoryRouter>
    </Provider>,
  );
}

const fakeUser: AuthUser = {
  id: 1,
  email: 'a@b.com',
  nickname: 'A',
  phone: null,
  avatarUrl: null,
  roles: ['customer'],
};

describe('RequireAuth', () => {
  it('未登录访问受保护路由 → 跳登录页', async () => {
    renderGuarded({ user: null, initialized: true });
    expect(await screen.findByText('login page')).toBeInTheDocument();
    expect(screen.queryByText('secret content')).not.toBeInTheDocument();
  });

  it('已登录 → 渲染子路由', async () => {
    renderGuarded({ user: fakeUser, initialized: true });
    expect(await screen.findByText('secret content')).toBeInTheDocument();
  });

  it('未初始化（探测中）→ 不渲染子路由也不跳登录', () => {
    renderGuarded({ user: null, initialized: false });
    expect(screen.queryByText('secret content')).not.toBeInTheDocument();
    expect(screen.queryByText('login page')).not.toBeInTheDocument();
  });
});
