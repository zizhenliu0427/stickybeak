import cartReducer, { addItem, setQty, removeItem, selectCartTotalCents } from '../features/cart/cartSlice';
import type { CartItem } from '../features/cart/cartSlice';

const sample: CartItem = {
  productId: 1,
  slug: 'sydney-t9-train-sign',
  name: '悉尼火车 T9 线路牌冰箱贴',
  imageUrl: '/mock-products/sydney-t9-train-sign/01.jpg',
  priceCents: 1090,
  qty: 1,
  stock: 3,
};

describe('cartSlice', () => {
  it('加购：同商品数量累加', () => {
    let state = cartReducer(undefined, addItem(sample));
    state = cartReducer(state, addItem({ ...sample, qty: 1 }));
    expect(state.items).toHaveLength(1);
    expect(state.items[0].qty).toBe(2);
  });

  it('加购：数量封顶库存', () => {
    let state = cartReducer(undefined, addItem({ ...sample, qty: 3 }));
    state = cartReducer(state, addItem({ ...sample, qty: 5 }));
    expect(state.items[0].qty).toBe(3);
  });

  it('setQty：下限 1 上限库存', () => {
    let state = cartReducer(undefined, addItem(sample));
    state = cartReducer(state, setQty({ productId: 1, qty: 99 }));
    expect(state.items[0].qty).toBe(3);
    state = cartReducer(state, setQty({ productId: 1, qty: 0 }));
    expect(state.items[0].qty).toBe(1);
  });

  it('小计 = 单价 × 数量求和', () => {
    let state = cartReducer(undefined, addItem({ ...sample, qty: 2 }));
    state = cartReducer(
      state,
      addItem({ ...sample, productId: 2, slug: 'greedy-birds', priceCents: 890, qty: 1 }),
    );
    expect(selectCartTotalCents({ cart: state })).toBe(1090 * 2 + 890);
    state = cartReducer(state, removeItem(1));
    expect(selectCartTotalCents({ cart: state })).toBe(890);
  });

  it('clearCart 清空购物车', () => {
    let state = cartReducer(undefined, addItem(sample));
    expect(state.items).toHaveLength(1);
    state = cartReducer(state, { type: 'cart/clearCart' });
    expect(state.items).toHaveLength(0);
  });
});
