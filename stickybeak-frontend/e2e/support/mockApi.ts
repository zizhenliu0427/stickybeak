import type { Page, Route } from '@playwright/test';

/**
 * 浏览器端的后端替身：拦截页面发往 /api/** 的请求，按真实接口的响应结构返回。
 * 这样 UI 旅程可以在 CI 里只靠 Vite dev server 运行；需要真实后端的全链路验证见 scripts/test-sprint8-e2e.js。
 */

const IMAGE =
  'data:image/svg+xml;utf8,' +
  encodeURIComponent('<svg xmlns="http://www.w3.org/2000/svg" width="400" height="400"><rect width="400" height="400" fill="#f4e9d8"/></svg>');

export interface MockProduct {
  id: number;
  slug: string;
  name: string;
  description: string;
  category: string;
  categoryName: string;
  priceCents: number;
  stock: number;
  sales: number;
  tags: string[];
  images: string[];
  sourceNoteId: string;
  featured: boolean;
  highlightedName?: string | null;
}

export const PRODUCTS: MockProduct[] = [
  {
    id: 101,
    slug: 'sydney-opera-house-magnet',
    name: 'Sydney Opera House Magnet',
    description: 'Hand-painted sails on the harbour.',
    category: 'landmarks',
    categoryName: 'Landmarks',
    priceCents: 1990,
    stock: 50,
    sales: 120,
    tags: ['Sydney'],
    images: [IMAGE],
    sourceNoteId: 'n-101',
    featured: true,
  },
  {
    id: 102,
    slug: 'unsw-bus-sign-magnet',
    name: 'UNSW Bus Sign Magnet',
    description: 'The 891 express, now on your fridge.',
    category: 'bus-sign',
    categoryName: 'Bus Signs',
    priceCents: 1500,
    stock: 8,
    sales: 64,
    tags: ['UNSW'],
    images: [IMAGE],
    sourceNoteId: 'n-102',
    featured: true,
  },
  {
    id: 103,
    slug: 'kookaburra-magnet',
    name: 'Kookaburra Magnet',
    description: 'Laughing all the way to breakfast.',
    category: 'wildlife',
    categoryName: 'Wildlife',
    priceCents: 1290,
    stock: 30,
    sales: 15,
    tags: ['Birds'],
    images: [IMAGE],
    sourceNoteId: 'n-103',
    featured: false,
  },
];

export const BUYER = {
  id: 7,
  email: 'buyer@example.com',
  nickname: 'Aussie Buyer',
  phone: null,
  avatarUrl: null,
  roles: ['customer'],
};

export const ADMIN = { ...BUYER, id: 1, email: 'admin@example.com', nickname: 'Admin', roles: ['admin'] };

const ADDRESS = {
  id: 31,
  receiver: 'Jane Doe',
  phone: '0400111222',
  country: 'AU',
  state: 'NSW',
  city: 'Sydney',
  postcode: '2000',
  detail: '1 Martin Place',
  isDefault: true,
};

interface CartLine {
  productId: number;
  qty: number;
}

export interface MockOrder {
  orderNo: string;
  status: string;
  payAmount: number;
  totalAmount: number;
  currency: string;
  createTime: string;
  items: { id: number; productId: number; productName: string; productImage: string; price: number; qty: number }[];
  allowedNextStatuses?: string[];
}

export interface MockBackend {
  /** 当前登录用户；null 表示游客 */
  user: typeof BUYER | typeof ADMIN | null;
  cart: CartLine[];
  orders: MockOrder[];
  checkoutRequests: Record<string, unknown>[];
  refundRequests: string[];
  /** 没有被模拟的接口，测试结束时断言为空，防止页面悄悄调用了新接口 */
  unmatched: string[];
}

export function order(orderNo: string, status: string, product = PRODUCTS[0], qty = 1): MockOrder {
  const amount = (product.priceCents * qty) / 100;
  return {
    orderNo,
    status,
    payAmount: amount,
    totalAmount: amount,
    currency: 'AUD',
    createTime: '2026-10-20T10:00:00',
    items: [{ id: 1, productId: product.id, productName: product.name, productImage: IMAGE, price: product.priceCents / 100, qty }],
  };
}

const ok = (data: unknown) => ({ code: 0, message: 'success', data });

function cartVO(backend: MockBackend) {
  const items = backend.cart.map((line, index) => {
    const p = PRODUCTS.find((x) => x.id === line.productId)!;
    return {
      id: index + 1,
      productId: p.id,
      slug: p.slug,
      name: p.name,
      imageUrl: IMAGE,
      priceCents: p.priceCents,
      qty: line.qty,
      stock: p.stock,
    };
  });
  return {
    id: 1,
    items,
    totalCents: items.reduce((sum, i) => sum + i.priceCents * i.qty, 0),
    itemCount: items.reduce((sum, i) => sum + i.qty, 0),
  };
}

function addToCart(backend: MockBackend, productId: number, qty: number) {
  const line = backend.cart.find((l) => l.productId === productId);
  if (line) {
    line.qty += qty;
  } else {
    backend.cart.push({ productId, qty });
  }
}

/** 模拟 ES 搜索：名称命中时返回 <em> 高亮 */
function searchProducts(q: string | null) {
  if (!q) {
    return PRODUCTS.map((p) => ({ ...p, highlightedName: null }));
  }
  const keyword = q.toLowerCase();
  return PRODUCTS.filter((p) => p.name.toLowerCase().includes(keyword) || p.description.toLowerCase().includes(keyword)).map(
    (p) => {
      const at = p.name.toLowerCase().indexOf(keyword);
      const highlightedName =
        at < 0 ? null : `${p.name.slice(0, at)}<em>${p.name.slice(at, at + keyword.length)}</em>${p.name.slice(at + keyword.length)}`;
      return { ...p, highlightedName };
    },
  );
}

export async function mockBackend(page: Page, initial: Partial<MockBackend> = {}): Promise<MockBackend> {
  const backend: MockBackend = {
    user: null,
    cart: [],
    orders: [],
    checkoutRequests: [],
    refundRequests: [],
    unmatched: [],
    ...initial,
  };

  // 固定英文界面，避免随运行环境的浏览器语言变化
  await page.addInitScript(() => {
    window.localStorage.setItem('sb_locale', 'en-AU');
  });

  await page.route(
    (url) => url.pathname.startsWith('/api/'),
    async (route: Route) => {
      try {
        await handle(route);
      } catch (e) {
        backend.unmatched.push(`mock error: ${String(e)}`);
        await route.fulfill({ status: 599, body: String(e) });
      }
    },
  );

  async function handle(route: Route) {
      const request = route.request();
      const url = new URL(request.url());
      const path = url.pathname.replace(/^\/api/, '');
      const method = request.method();
      const body = request.postDataJSON?.() ?? null;
      const json = (data: unknown, status = 200) =>
        route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(data) });
      const unauthorized = () => json({ code: 401, message: 'Unauthorized', data: null }, 401);

      // ---- auth ----
      if (method === 'GET' && path === '/users/me') {
        return backend.user ? json(ok(backend.user)) : unauthorized();
      }
      if (method === 'POST' && path === '/auth/refresh') {
        return unauthorized();
      }
      if (method === 'POST' && path === '/auth/login') {
        backend.user = body?.email === ADMIN.email ? ADMIN : BUYER;
        return json(ok({ user: backend.user, accessExpiresIn: 900 }));
      }
      if (method === 'POST' && path === '/auth/logout') {
        backend.user = null;
        return json(ok(null));
      }
      if (method === 'GET' && path === '/users/me/addresses') {
        return backend.user ? json(ok([ADDRESS])) : unauthorized();
      }

      // ---- catalog ----
      if (method === 'GET' && path === '/payments/exchange-rate') {
        return json(ok({ baseCurrency: 'AUD', quoteCurrency: 'CNY', rate: 4.7, fetchedAt: '2026-10-20T10:00:00' }));
      }
      if (method === 'GET' && path === '/categories') {
        return json(
          ok([
            { slug: 'landmarks', name: 'Landmarks', sortOrder: 1 },
            { slug: 'bus-sign', name: 'Bus Signs', sortOrder: 2 },
            { slug: 'wildlife', name: 'Wildlife', sortOrder: 3 },
          ]),
        );
      }
      if (method === 'GET' && path === '/products/featured') {
        return json(ok(PRODUCTS.filter((p) => p.featured)));
      }
      if (method === 'GET' && path === '/products') {
        const items = searchProducts(url.searchParams.get('q'));
        return json(ok({ items, total: items.length, page: 1, size: 12 }));
      }
      const related = path.match(/^\/products\/([^/]+)\/related$/);
      if (method === 'GET' && related) {
        return json(ok(PRODUCTS.filter((p) => p.slug !== related[1]).slice(0, 2)));
      }
      const detail = path.match(/^\/products\/([^/]+)$/);
      if (method === 'GET' && detail) {
        const product = PRODUCTS.find((p) => p.slug === detail[1]);
        return product ? json(ok(product)) : json({ code: 404, message: 'Product not found', data: null }, 404);
      }

      // ---- cart & wishlist ----
      if (path === '/cart' && method === 'GET') {
        return json(ok(cartVO(backend)));
      }
      if (path === '/cart/items' && method === 'POST') {
        addToCart(backend, body.productId, body.qty);
        return json(ok(cartVO(backend)));
      }
      if (path === '/cart/merge' && method === 'POST') {
        for (const item of body.items ?? []) {
          addToCart(backend, item.productId, item.qty);
        }
        return json(ok(cartVO(backend)));
      }
      if (path === '/wishlist' && method === 'GET') {
        return json(ok([]));
      }

      // ---- orders ----
      if (method === 'POST' && path === '/orders/checkout') {
        backend.checkoutRequests.push(body);
        const created = order(`SO20261024${String(1000 + backend.orders.length)}`, 'pending');
        backend.orders.unshift(created);
        // Stripe 托管收银台的替身：直接回到支付成功页（真实流程由 webhook 把订单推进到 paid）
        return json(
          ok({
            orderNo: created.orderNo,
            totalAmount: created.totalAmount,
            payAmount: created.payAmount,
            currency: 'AUD',
            checkoutUrl: `${url.origin}/checkout/success?order_no=${created.orderNo}&session_id=cs_test_e2e`,
            sessionId: 'cs_test_e2e',
          }),
        );
      }
      if (method === 'GET' && path === '/orders/my') {
        return backend.user ? json(ok(backend.orders)) : unauthorized();
      }
      if (method === 'GET' && path === '/orders/admin/page') {
        return json(ok({ records: backend.orders, total: backend.orders.length, current: 1, size: 10 }));
      }
      const refund = path.match(/^\/orders\/admin\/([^/]+)\/refund$/);
      if (method === 'POST' && refund) {
        backend.refundRequests.push(refund[1]);
        const target = backend.orders.find((o) => o.orderNo === refund[1]);
        if (target) {
          target.status = 'refunding';
          target.allowedNextStatuses = [];
        }
        return json(ok(null));
      }
      const single = path.match(/^\/orders\/([^/]+)$/);
      if (method === 'GET' && single) {
        const found = backend.orders.find((o) => o.orderNo === single[1]);
        // 支付成功页轮询订单状态：webhook 已经处理完
        return found ? json(ok({ ...found, status: 'paid' })) : json({ code: 404, message: 'Order not found', data: null }, 404);
      }

      backend.unmatched.push(`${method} ${url.pathname}`);
      return json({ code: 404, message: `No mock for ${method} ${url.pathname}`, data: null }, 404);
  }

  return backend;
}
