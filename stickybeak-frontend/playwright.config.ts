import { defineConfig, devices } from '@playwright/test';

/**
 * UI 旅程测试：起 Vite dev server，后端接口在浏览器里用 page.route 模拟（e2e/support/mockApi.ts），
 * 所以 CI 不需要启动整套微服务。全链路接口验证见 scripts/test-sprint8-e2e.js。
 */
export default defineConfig({
  testDir: './e2e',
  timeout: 30 * 1000,
  expect: {
    timeout: 5000,
  },
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: 0,
  workers: process.env.CI ? 2 : undefined,
  reporter: process.env.CI ? [['list'], ['html', { open: 'never' }]] : 'list',
  use: {
    baseURL: process.env.PLAYWRIGHT_TEST_BASE_URL || 'http://localhost:5173',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    // PWA 的 Service Worker 会自己转发 /api 请求（网络优先 + 缓存），page.route 拦截不到，测试里不注册
    serviceWorkers: 'block',
  },
  webServer: process.env.PLAYWRIGHT_TEST_BASE_URL
    ? undefined
    : {
        command: 'npm run dev -- --port 5173 --strictPort',
        url: 'http://localhost:5173',
        reuseExistingServer: !process.env.CI,
        timeout: 60 * 1000,
      },
  projects: [
    {
      name: 'desktop-chromium',
      use: { ...devices['Desktop Chrome'] },
    },
    {
      // 移动端布局（底部导航、抽屉购物车）；管理后台只面向桌面
      name: 'mobile-chromium',
      use: { ...devices['Pixel 7'] },
      testIgnore: /admin-.*\.spec\.ts/,
    },
  ],
});
