export type Locale = 'en-AU' | 'zh-CN';

export const translations = {
  'en-AU': {
    // Nav
    'nav.brand': 'StickyBeak',
    'nav.shop': 'Catalogue',
    'nav.bird': 'Feathered Mates',
    'nav.busSign': 'Uni Bus Stops',
    'nav.trolley': 'Trolley',
    'nav.profile': 'Profile',
    'nav.myOrders': 'My Orders',
    'nav.signOut': 'Sign out',
    'nav.signIn': 'Sign in',

    // Common & Trolley
    'common.trolley': 'Trolley',
    'common.subtotal': 'Subtotal',
    'common.viewTrolley': 'View Trolley & Checkout',
    'common.emptyTrolley': 'Your trolley is currently empty, mate!',
    'common.haveABrowse': 'Have a browse',
    'common.all': 'All',
    'common.loading': 'Loading...',
    'common.stock': 'in stock',
    'common.sold': 'sold',

    // Hero
    'hero.badge': "G'DAY, MATE!",
    'hero.title': 'Slap Aussie Memories\nStraight onto Your Fridge',
    'hero.desc': 'Handcrafted fridge magnets capturing the daily quirks of Down Under — bus stops, Sydney trains, supermarket specials, and cheeky hot-chip thieving seagulls.',
    'hero.cta': 'Browse Full Catalogue',

    // Home Sections
    'home.categories': 'Shop by Series',
    'home.categoriesSubtitle': 'From uni bus routes to suburban bottle-os, pick your favourite piece of Australia.',
    'home.featured': 'Popular Picks',
    'home.featuredSubtitle': 'Top rated magnets loved by locals and travelers alike.',
    'home.viewAll': 'View all magnets',

    // Categories
    'cat.bus-sign': 'Uni Bus Stops & Signs',
    'cat.supermarket': 'Supermarket Specials',
    'cat.train': 'Trains & Trams',
    'cat.bird': 'Feathered Mates & Birds',
    'cat.booze': 'Bottle-O & Booze',
    'cat.phonecase': 'Phone Cases & Goodies',

    // Products Page
    'products.title': 'Full Magnet Catalogue',
    'products.subtitle': 'Authentic Aussie vibes handcrafted into high quality magnets.',
    'products.searchPlaceholder': 'Search by name, campus, station...',
    'products.filterCategory': 'Category',
    'products.filterPrice': 'Price Range',
    'products.filterTags': 'Tags',
    'products.clearFilters': 'Clear all filters',
    'products.sort': 'Sort by',
    'products.sortNew': 'New Arrivals',
    'products.sortSales': 'Top Sellers',
    'products.sortPriceAsc': 'Price: Low to High',
    'products.sortPriceDesc': 'Price: High to Low',
    'products.noResults': 'No magnets found matching your filters.',

    // Product Detail
    'detail.back': 'Back to catalogue',
    'detail.addToTrolley': 'Add to Trolley',
    'detail.tags': 'Tags',
    'detail.related': 'You Might Also Fancy',
    'detail.addedSuccess': 'Added to your trolley!',
    'detail.soldOut': 'Sold Out',

    // Cart Page
    'cart.title': 'Your Shopping Trolley',
    'cart.emptyTitle': 'Your trolley is empty',
    'cart.emptyDesc': 'Looks like you haven’t chucked any magnets in your trolley yet.',
    'cart.emptyCta': 'Explore the Catalogue',
    'cart.checkout': 'Proceed to Checkout',
    'cart.total': 'Total',
    'cart.clear': 'Clear Trolley',
    'cart.itemCount': '{count} items',

    // Wishlist
    'wishlist.title': 'Wishlist',
    'wishlist.addSuccess': 'Saved to wishlist!',
    'wishlist.removeSuccess': 'Removed from wishlist',
    'wishlist.moveToTrolley': 'Move to trolley',
    'wishlist.empty': 'No items on your wishlist yet',
    'wishlist.requireLogin': 'Please sign in to save items to wishlist',

    // Footer
    'footer.brand': 'StickyBeak — Aussie-themed fridge magnets',
    'footer.desc': 'Uni bus stops · Sydney trains · Supermarket specials · Cheeky birds · Source: RED GDCUP (Authorized)',
    'footer.copyright': '© {year} StickyBeak. Handcrafted with heart down under.',

    // Checkout
    'checkout.title': 'Checkout',
    'checkout.shippingAddress': 'Delivery Address',
    'checkout.addAddress': 'Add New Address',
    'checkout.paymentMethod': 'Payment Method',
    'checkout.card': 'Credit / Debit Card (Stripe)',
    'checkout.alipay': 'Alipay (支付宝)',
    'checkout.wechatPay': 'WeChat Pay (微信支付)',
    'checkout.orderSummary': 'Order Summary',
    'checkout.freeShipping': 'FREE Delivery (Orders over $49)',
    'checkout.shippingFee': 'AU Delivery Fee',
    'checkout.currency': 'Payment Currency',
    'checkout.rateNotice': 'Current exchange rate: 1 AUD ≈ {rate} CNY',
    'checkout.remark': 'Order Remarks / Delivery Instructions',
    'checkout.remarkPlaceholder': 'e.g. Leave by the front porch, mate',
    'checkout.payNow': 'Pay Now via Stripe',
    'checkout.processing': 'Redirecting to payment gateway...',
    'checkout.selectAddressPrompt': 'Please select or add a delivery address',
    // Checkout Success
    'checkout.successTitle': 'Order Paid Successfully!',
    'checkout.successDesc': 'Thank you! Your payment has been confirmed. A confirmation receipt has been sent to your email.',
    'checkout.polling': 'Confirming your payment with the bank...',
    'checkout.orderNo': 'Order Number',
    'checkout.amountPaid': 'Amount Paid',
    'checkout.paidAt': 'Payment Time',
    'checkout.continueShopping': 'Continue Shopping',
    'checkout.viewOrders': 'View My Account',
    // Checkout Cancel
    'checkout.cancelTitle': 'Payment Cancelled',
    'checkout.cancelDesc': 'Your checkout session was cancelled. No money was taken from your account.',
    'checkout.returnToCart': 'Back to Trolley',
    'checkout.retry': 'Retry Checkout',

    // Orders page
    'orders.title': 'My Orders',
    'orders.tabAll': 'All',
    'orders.tabPending': 'Pending Payment',
    'orders.tabPaid': 'Paid',
    'orders.tabProcessing': 'Processing',
    'orders.tabShipped': 'Shipped',
    'orders.tabCompleted': 'Completed',
    'orders.tabCancelled': 'Cancelled',
    'orders.orderNo': 'Order No',
    'orders.createdAt': 'Placed At',
    'orders.total': 'Total',
    'orders.countdown': 'Pay within {time}',
    'orders.expired': 'Payment expired',
    'orders.cancelBtn': 'Cancel Order',
    'orders.cancelConfirm': 'Are you sure you want to cancel this order? Held stock will be released.',
    'orders.cancelSuccess': 'Order cancelled successfully',
    'orders.payNow': 'Pay Now',
    'orders.empty': 'No orders found in this status',
    'orders.backToShop': 'Browse Magnets',

    // Theme & Lang
    'theme.light': 'Light',
    'theme.dark': 'Dark',
    'theme.system': 'System Auto',
  },
  'zh-CN': {
    // Nav
    'nav.brand': 'StickyBeak',
    'nav.shop': '全部商品',
    'nav.bird': '小鸟系列',
    'nav.busSign': '大学路牌',
    'nav.trolley': '购物车',
    'nav.profile': '个人中心',
    'nav.myOrders': '我的订单',
    'nav.signOut': '退出登录',
    'nav.signIn': '登录',

    // Common & Trolley
    'common.trolley': '购物车',
    'common.subtotal': '小计',
    'common.viewTrolley': '查看购物车并结账',
    'common.emptyTrolley': '购物车是空的',
    'common.haveABrowse': '去逛逛',
    'common.all': '全部',
    'common.loading': '加载中...',
    'common.stock': '库存',
    'common.sold': '已售',

    // Hero
    'hero.badge': "G'DAY, MATE!",
    'hero.title': '把澳洲日常\n贴上你的冰箱',
    'hero.desc': '公交站牌、悉尼火车、超市小票、抢食的海鸥——GDCUP 手作冰箱贴，每一枚都是土澳生活的切片。',
    'hero.cta': '逛逛全部商品',

    // Home Sections
    'home.categories': '按系列探索',
    'home.categoriesSubtitle': '从高校站牌到街角超市，找到属于你的土澳独家记忆。',
    'home.featured': '热卖精选',
    'home.featuredSubtitle': '最受欢迎的招牌磁贴，留子与本地友人都爱不释手。',
    'home.viewAll': '查看全部磁贴',

    // Categories
    'cat.bus-sign': '大学公交路牌',
    'cat.supermarket': '超市系列',
    'cat.train': '火车电车',
    'cat.bird': '小鸟路牌',
    'cat.booze': '酒鬼系列',
    'cat.phonecase': '手机壳周边',

    // Products Page
    'products.title': '全部冰箱贴商品',
    'products.subtitle': '精选 265+ 款地道土澳特色磁贴，还原真实澳洲烟火气。',
    'products.searchPlaceholder': '搜索商品名称、学校、车站...',
    'products.filterCategory': '商品分类',
    'products.filterPrice': '价格区间',
    'products.filterTags': '标签筛选',
    'products.clearFilters': '清空所有筛选',
    'products.sort': '排序方式',
    'products.sortNew': '最新上架',
    'products.sortSales': '销量优先',
    'products.sortPriceAsc': '价格从低到高',
    'products.sortPriceDesc': '价格从高到低',
    'products.noResults': '未找到符合条件的磁贴商品。',

    // Product Detail
    'detail.back': '返回商品目录',
    'detail.addToTrolley': '加入购物车',
    'detail.tags': '相关标签',
    'detail.related': '你可能也喜欢',
    'detail.addedSuccess': '已加入购物车！',
    'detail.soldOut': '已售罄',

    // Cart Page
    'cart.title': '我的购物车',
    'cart.emptyTitle': '购物车还是空的',
    'cart.emptyDesc': '快去挑选几枚心仪的磁贴装点你的冰箱吧。',
    'cart.emptyCta': '去商品目录逛逛',
    'cart.checkout': '结算订单',
    'cart.total': '合计',
    'cart.clear': '清空购物车',
    'cart.itemCount': '共 {count} 件商品',

    // Wishlist
    'wishlist.title': '我的心愿单',
    'wishlist.addSuccess': '已加入心愿单！',
    'wishlist.removeSuccess': '已从心愿单移除',
    'wishlist.moveToTrolley': '移入购物车',
    'wishlist.empty': '心愿单还是空的',
    'wishlist.requireLogin': '请先登录后再收藏商品',

    // Footer
    'footer.brand': 'StickyBeak — 澳洲主题冰箱贴电商平台',
    'footer.desc': '公交站牌 · 悉尼火车 · 超市特价 · 抢食小鸟 · 数据素材：小红书 GDCUP（已授权）',
    'footer.copyright': '© {year} StickyBeak. 土澳日常用心手作。',

    // Checkout
    'checkout.title': '订单结算',
    'checkout.shippingAddress': '收货地址',
    'checkout.addAddress': '新增收货地址',
    'checkout.paymentMethod': '支付方式',
    'checkout.card': '国际信用卡 / 借记卡 (Stripe)',
    'checkout.alipay': '支付宝 (Alipay)',
    'checkout.wechatPay': '微信支付 (WeChat Pay)',
    'checkout.orderSummary': '订单明细',
    'checkout.freeShipping': '满 $49 包邮',
    'checkout.shippingFee': '澳洲境内运费',
    'checkout.currency': '支付结算币种',
    'checkout.rateNotice': '实时参考汇率：1 AUD ≈ {rate} CNY',
    'checkout.remark': '订单备注 / 配送说明',
    'checkout.remarkPlaceholder': '例如：放门口即可，谢谢',
    'checkout.payNow': '立即支付 (跳转 Stripe)',
    'checkout.processing': '正在跳转安全收银台...',
    'checkout.selectAddressPrompt': '请选择或填写收货地址',
    // Checkout Success
    'checkout.successTitle': '订单支付成功！',
    'checkout.successDesc': '感谢您的购买！支付已成功确认，订单确认信已发送至您的邮箱。',
    'checkout.polling': '正在与收单行确认支付结果...',
    'checkout.orderNo': '订单编号',
    'checkout.amountPaid': '实付金额',
    'checkout.paidAt': '支付时间',
    'checkout.continueShopping': '继续购物',
    'checkout.viewOrders': '个人中心',
    // Checkout Cancel
    'checkout.cancelTitle': '支付已取消',
    'checkout.cancelDesc': '您已取消本次结算，账户未产生扣款。',
    'checkout.returnToCart': '返回购物车',
    'checkout.retry': '重新结账',

    // Orders page
    'orders.title': '我的订单',
    'orders.tabAll': '全部',
    'orders.tabPending': '待支付',
    'orders.tabPaid': '已支付',
    'orders.tabProcessing': '备货中',
    'orders.tabShipped': '已发货',
    'orders.tabCompleted': '已完成',
    'orders.tabCancelled': '已取消',
    'orders.orderNo': '订单编号',
    'orders.createdAt': '下单时间',
    'orders.total': '订单总计',
    'orders.countdown': '支付倒计时 {time}',
    'orders.expired': '已超时关闭',
    'orders.cancelBtn': '取消订单',
    'orders.cancelConfirm': '确定要取消此订单吗？预占库存将自动释放。',
    'orders.cancelSuccess': '订单已成功取消',
    'orders.payNow': '立即支付',
    'orders.empty': '暂无相关状态的订单',
    'orders.backToShop': '去挑选冰箱贴',

    // Theme & Lang
    'theme.light': '浅色',
    'theme.dark': '深色',
    'theme.system': '跟随系统',
  },
} as const;

export type TranslationKey = keyof typeof translations['en-AU'];

export function t(locale: Locale, key: TranslationKey, params?: Record<string, string | number>): string {
  const dict = translations[locale] || translations['en-AU'];
  let text: string = dict[key] || translations['en-AU'][key] || key;
  if (params) {
    for (const [k, v] of Object.entries(params)) {
      text = text.replace(new RegExp(`\\{${k}\\}`, 'g'), String(v));
    }
  }
  return text;
}

import { useAppSelector } from '../app/hooks';

export function useI18n() {
  const locale = useAppSelector((s) => s.locale.locale);
  return {
    locale,
    t: (key: TranslationKey, params?: Record<string, string | number>) => t(locale, key, params),
  };
}
