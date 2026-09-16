import { useEffect, useState } from 'react';
import {
  Table,
  Card,
  Input,
  Button,
  Space,
  Switch,
  Modal,
  Form,
  InputNumber,
  Tag,
  Tabs,
  Badge,
  Typography,
  message,
  Tooltip,
} from 'antd';
import {
  SearchOutlined,
  EditOutlined,
  PlusOutlined,
  AlertOutlined,
  ReloadOutlined,
} from '@ant-design/icons';
import {
  getAdminProducts,
  updateProductStatus,
  updateProductStock,
  updateProductPrice,
  getStockAlerts,
  type AdminProduct,
  type AdminProductQueryParams,
  type StockAlertSummary,
} from '../../lib/admin';
import { useI18n } from '../../lib/i18n';

const { Title, Text } = Typography;

export default function AdminProductsPage() {
  const { t } = useI18n();

  const [loading, setLoading] = useState(false);
  const [products, setProducts] = useState<AdminProduct[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(10);
  const [keyword, setKeyword] = useState('');
  const [activeTab, setActiveTab] = useState<string>('all'); // all, alerts

  // 库存警报汇总
  const [alertSummary, setAlertSummary] = useState<StockAlertSummary | null>(null);

  // 编辑库存 Modal
  const [stockModalVisible, setStockModalVisible] = useState(false);
  const [stockTargetProduct, setStockTargetProduct] = useState<AdminProduct | null>(null);
  const [stockForm] = Form.useForm();

  // 编辑价格 Modal
  const [priceModalVisible, setPriceModalVisible] = useState(false);
  const [priceTargetProduct, setPriceTargetProduct] = useState<AdminProduct | null>(null);
  const [priceForm] = Form.useForm();

  const fetchProducts = async (
    p = page,
    size = pageSize,
    kw = keyword,
    tab = activeTab
  ) => {
    setLoading(true);
    try {
      const params: AdminProductQueryParams = {
        page: p,
        size,
      };
      if (kw.trim()) {
        params.keyword = kw.trim();
      }
      if (tab === 'alerts') {
        params.stockAlert = 1; // <= 10
      }

      const res = await getAdminProducts(params);
      setProducts(res.records || []);
      setTotal(res.total || 0);
      setPage(res.current || 1);

      // 同步获取预警统计
      const alerts = await getStockAlerts(10);
      setAlertSummary(alerts);
    } catch (err: any) {
      message.error(err?.message || 'Failed to fetch products');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchProducts(1, pageSize, keyword, activeTab);
  }, [activeTab]);

  const handleSearch = () => {
    setPage(1);
    fetchProducts(1, pageSize, keyword, activeTab);
  };

  const handleStatusToggle = async (record: AdminProduct, checked: boolean) => {
    const newStatus = checked ? 0 : 1;
    try {
      await updateProductStatus(record.id, newStatus);
      message.success(`Product is now ${checked ? 'Active (On-shelf)' : 'Unlisted (Off-shelf)'}`);
      setProducts((prev) =>
        prev.map((item) => (item.id === record.id ? { ...item, status: newStatus } : item))
      );
    } catch (err: any) {
      message.error(err?.message || 'Failed to update status');
    }
  };

  const openStockModal = (record: AdminProduct) => {
    setStockTargetProduct(record);
    stockForm.setFieldsValue({ stock: record.stock });
    setStockModalVisible(true);
  };

  const handleStockSubmit = async () => {
    if (!stockTargetProduct) return;
    try {
      const values = await stockForm.validateFields();
      await updateProductStock(stockTargetProduct.id, { stock: values.stock });
      message.success(`Stock for ${stockTargetProduct.name} updated to ${values.stock}`);
      setStockModalVisible(false);
      fetchProducts(page, pageSize, keyword, activeTab);
    } catch (err: any) {
      // validation error
    }
  };

  const handleQuickAddStock = async (delta: number) => {
    if (!stockTargetProduct) return;
    const current = stockForm.getFieldValue('stock') || 0;
    stockForm.setFieldsValue({ stock: current + delta });
  };

  const openPriceModal = (record: AdminProduct) => {
    setPriceTargetProduct(record);
    priceForm.setFieldsValue({ price: record.price });
    setPriceModalVisible(true);
  };

  const handlePriceSubmit = async () => {
    if (!priceTargetProduct) return;
    try {
      const values = await priceForm.validateFields();
      await updateProductPrice(priceTargetProduct.id, values.price);
      message.success(`Price for ${priceTargetProduct.name} updated to $${values.price}`);
      setPriceModalVisible(false);
      fetchProducts(page, pageSize, keyword, activeTab);
    } catch (err: any) {
      // validation error
    }
  };

  const getStockBadge = (record: AdminProduct) => {
    const s = record.stock;
    if (s <= 0) {
      return <Tag color="error">Out of Stock (0)</Tag>;
    }
    if (s <= 3) {
      return <Tag color="orange">Critical ({s})</Tag>;
    }
    if (s <= 10) {
      return <Tag color="warning">Low Stock ({s})</Tag>;
    }
    return <Tag color="success">{s} in stock</Tag>;
  };

  const columns = [
    {
      title: 'Product',
      key: 'product',
      render: (_: any, record: AdminProduct) => (
        <div className="flex items-center gap-3">
          {record.coverImage ? (
            <img src={record.coverImage} alt={record.name} className="w-12 h-12 object-cover rounded shadow-sm" />
          ) : (
            <div className="w-12 h-12 bg-gray-100 dark:bg-gray-800 rounded flex items-center justify-center text-xs text-gray-400">
              No Pic
            </div>
          )}
          <div>
            <p className="font-semibold text-sm max-w-xs truncate">{record.name}</p>
            <p className="text-xs text-gray-500">{record.categoryName || 'Uncategorized'}</p>
          </div>
        </div>
      ),
    },
    {
      title: 'Price (AUD)',
      dataIndex: 'price',
      key: 'price',
      render: (price: number, record: AdminProduct) => (
        <div className="flex items-center gap-2">
          <span className="font-medium">${price.toFixed(2)}</span>
          <Button
            type="text"
            size="small"
            icon={<EditOutlined className="text-gray-400 hover:text-emerald-600" />}
            onClick={() => openPriceModal(record)}
          />
        </div>
      ),
    },
    {
      title: 'Stock Health',
      key: 'stock',
      render: (_: any, record: AdminProduct) => (
        <div className="flex items-center gap-2">
          {getStockBadge(record)}
          <Button
            type="text"
            size="small"
            icon={<PlusOutlined className="text-gray-400 hover:text-emerald-600" />}
            onClick={() => openStockModal(record)}
          />
        </div>
      ),
    },
    {
      title: 'Sales',
      dataIndex: 'sales',
      key: 'sales',
      render: (val: number) => <span className="text-gray-600 font-medium">{val} sold</span>,
    },
    {
      title: 'Status',
      key: 'status',
      render: (_: any, record: AdminProduct) => (
        <Tooltip title={record.status === 0 ? 'Click to unlist (hide from shop)' : 'Click to list (make visible in shop)'}>
          <Switch
            checked={record.status === 0}
            checkedChildren="Listed"
            unCheckedChildren="Unlisted"
            onChange={(checked) => handleStatusToggle(record, checked)}
          />
        </Tooltip>
      ),
    },
    {
      title: 'Actions',
      key: 'actions',
      render: (_: any, record: AdminProduct) => (
        <Space>
          <Button size="small" icon={<EditOutlined />} onClick={() => openStockModal(record)}>
            Adjust Stock
          </Button>
        </Space>
      ),
    },
  ];

  return (
    <div className="space-y-6">
      <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4">
        <div>
          <Title level={3} className="!mb-1">
            {t('admin.products')}
          </Title>
          <Text type="secondary">Manage product catalogue, listing status, prices and inventory thresholds.</Text>
        </div>
        <Button icon={<ReloadOutlined />} onClick={() => fetchProducts()}>
          Refresh
        </Button>
      </div>

      <Card bordered={false} className="shadow-sm border border-gray-100 dark:border-gray-800">
        <Tabs
          activeKey={activeTab}
          onChange={(k) => setActiveTab(k)}
          items={[
            {
              key: 'all',
              label: 'All Products',
            },
            {
              key: 'alerts',
              label: (
                <Badge count={alertSummary?.totalAlerts || 0} offset={[10, 0]} size="small">
                  <span>
                    <AlertOutlined className="mr-1 text-red-500" />
                    Low Stock Alerts
                  </span>
                </Badge>
              ),
            },
          ]}
        />

        {/* 搜索与过滤 */}
        <div className="flex items-center gap-4 my-4">
          <Input
            placeholder="Search product name, slug or description..."
            value={keyword}
            onChange={(e) => setKeyword(e.target.value)}
            onPressEnter={handleSearch}
            prefix={<SearchOutlined />}
            style={{ width: 320 }}
            allowClear
          />
          <Button type="primary" onClick={handleSearch}>
            Search
          </Button>
        </div>

        {/* 商品表格 */}
        <Table
          rowKey="id"
          dataSource={products}
          columns={columns}
          loading={loading}
          pagination={{
            current: page,
            pageSize: pageSize,
            total: total,
            showSizeChanger: true,
            onChange: (p, s) => {
              setPage(p);
              setPageSize(s);
              fetchProducts(p, s, keyword, activeTab);
            },
          }}
        />
      </Card>

      {/* 修改库存 Modal */}
      <Modal
        title={`Adjust Stock: ${stockTargetProduct?.name}`}
        open={stockModalVisible}
        onOk={handleStockSubmit}
        onCancel={() => setStockModalVisible(false)}
        okText="Update Stock"
      >
        <Form form={stockForm} layout="vertical">
          <Form.Item
            name="stock"
            label="Physical Stock Level"
            rules={[{ required: true, message: 'Please enter target stock' }]}
          >
            <InputNumber min={0} max={99999} className="w-full" />
          </Form.Item>

          <div className="flex items-center gap-2 mb-4">
            <span className="text-xs text-gray-500">Quick Add:</span>
            <Button size="small" onClick={() => handleQuickAddStock(10)}>
              +10
            </Button>
            <Button size="small" onClick={() => handleQuickAddStock(50)}>
              +50
            </Button>
            <Button size="small" onClick={() => handleQuickAddStock(100)}>
              +100
            </Button>
          </div>
          <p className="text-xs text-gray-400">
            * Changes are immediately committed to MySQL and synced to Redis Lua pre-deduction cache.
          </p>
        </Form>
      </Modal>

      {/* 修改价格 Modal */}
      <Modal
        title={`Edit Price: ${priceTargetProduct?.name}`}
        open={priceModalVisible}
        onOk={handlePriceSubmit}
        onCancel={() => setPriceModalVisible(false)}
        okText="Update Price"
      >
        <Form form={priceForm} layout="vertical">
          <Form.Item
            name="price"
            label="Base Price in AUD ($)"
            rules={[{ required: true, message: 'Please enter valid price' }]}
          >
            <InputNumber min={0.01} step={0.1} precision={2} className="w-full" />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}
