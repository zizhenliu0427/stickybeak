import { useEffect, useState } from 'react';
import {
  Table,
  Card,
  Input,
  Select,
  Tag,
  Button,
  Space,
  Drawer,
  Modal,
  Form,
  Popconfirm,
  message,
  Typography,
  Descriptions,
  Divider,
} from 'antd';
import {
  SearchOutlined,
  EyeOutlined,
  SendOutlined,
  CheckCircleOutlined,
  CloseCircleOutlined,
  SyncOutlined,
  ReloadOutlined,
} from '@ant-design/icons';
import {
  getAdminOrders,
  updateAdminOrderStatus,
  type AdminOrder,
  type AdminOrderQueryParams,
} from '../../lib/admin';
import { formatPrice } from '../../lib/format';
import { useI18n } from '../../lib/i18n';

const { Title, Text } = Typography;

export default function AdminOrdersPage() {
  const { t } = useI18n();

  const [loading, setLoading] = useState(false);
  const [orders, setOrders] = useState<AdminOrder[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(10);
  const [searchOrderNo, setSearchOrderNo] = useState('');
  const [filterStatus, setFilterStatus] = useState<string>('all');

  // 详情抽屉状态
  const [selectedOrder, setSelectedOrder] = useState<AdminOrder | null>(null);
  const [drawerVisible, setDrawerVisible] = useState(false);

  // 发货 Modal 状态
  const [shipModalVisible, setShipModalVisible] = useState(false);
  const [targetOrderNo, setTargetOrderNo] = useState('');
  const [shipForm] = Form.useForm();

  const fetchOrders = async (
    targetPage = page,
    targetSize = pageSize,
    targetStatus = filterStatus,
    targetNo = searchOrderNo
  ) => {
    setLoading(true);
    try {
      const params: AdminOrderQueryParams = {
        page: targetPage,
        size: targetSize,
      };
      if (targetStatus && targetStatus !== 'all') {
        params.status = targetStatus;
      }
      if (targetNo.trim()) {
        params.orderNo = targetNo.trim();
      }

      const res = await getAdminOrders(params);
      setOrders(res.records || []);
      setTotal(res.total || 0);
      setPage(res.current || 1);
    } catch (err: any) {
      message.error(err?.message || 'Failed to fetch orders');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchOrders(1, pageSize, filterStatus, searchOrderNo);
  }, [filterStatus]);

  const handleSearch = () => {
    setPage(1);
    fetchOrders(1, pageSize, filterStatus, searchOrderNo);
  };

  const handleStatusTransition = async (orderNo: string, nextStatus: string, trackingNo?: string) => {
    try {
      await updateAdminOrderStatus(orderNo, {
        status: nextStatus,
        trackingNo,
      });
      message.success(`Order ${orderNo} transitioned to ${nextStatus}`);
      if (drawerVisible && selectedOrder?.orderNo === orderNo) {
        setSelectedOrder((prev) => (prev ? { ...prev, status: nextStatus } : null));
      }
      fetchOrders(page, pageSize, filterStatus, searchOrderNo);
    } catch (err: any) {
      message.error(err?.message || 'Failed to update order status');
    }
  };

  const handleShipSubmit = async () => {
    try {
      const values = await shipForm.validateFields();
      await handleStatusTransition(targetOrderNo, 'shipped', values.trackingNo);
      setShipModalVisible(false);
      shipForm.resetFields();
    } catch (err: any) {
      // Form validation error
    }
  };

  const parseAddress = (jsonStr: string) => {
    try {
      return JSON.parse(jsonStr);
    } catch {
      return null;
    }
  };

  const getStatusBadge = (status: string) => {
    switch (status.toLowerCase()) {
      case 'pending':
        return <Tag color="warning">Pending Payment</Tag>;
      case 'paid':
        return <Tag color="blue">Paid</Tag>;
      case 'processing':
        return <Tag color="processing">Processing</Tag>;
      case 'shipped':
        return <Tag color="cyan">Shipped</Tag>;
      case 'completed':
        return <Tag color="success">Completed</Tag>;
      case 'cancelled':
        return <Tag color="default">Cancelled</Tag>;
      default:
        return <Tag>{status}</Tag>;
    }
  };

  const columns = [
    {
      title: 'Order No',
      dataIndex: 'orderNo',
      key: 'orderNo',
      render: (text: string) => <span className="font-mono font-medium">{text}</span>,
    },
    {
      title: 'Placed At',
      dataIndex: 'createTime',
      key: 'createTime',
      render: (val: string) => <span className="text-gray-500 text-xs">{val}</span>,
    },
    {
      title: 'Status',
      dataIndex: 'status',
      key: 'status',
      render: (status: string) => getStatusBadge(status),
    },
    {
      title: 'Total Amount',
      dataIndex: 'payAmount',
      key: 'payAmount',
      render: (val: number, record: AdminOrder) => (
        <span className="font-semibold">
          {formatPrice(Math.round(val * 100), (record.currency as any) || 'AUD')}
        </span>
      ),
    },
    {
      title: 'Items',
      dataIndex: 'items',
      key: 'items',
      render: (items: any[]) => <span>{items?.length || 0} item(s)</span>,
    },
    {
      title: 'Actions',
      key: 'actions',
      render: (_: any, record: AdminOrder) => {
        const allowed = record.allowedNextStatuses || [];
        return (
          <Space size="small">
            <Button
              size="small"
              icon={<EyeOutlined />}
              onClick={() => {
                setSelectedOrder(record);
                setDrawerVisible(true);
              }}
            >
              Details
            </Button>

            {allowed.includes('processing') && (
              <Button
                size="small"
                type="primary"
                icon={<SyncOutlined />}
                onClick={() => handleStatusTransition(record.orderNo, 'processing')}
              >
                Process
              </Button>
            )}

            {allowed.includes('shipped') && (
              <Button
                size="small"
                type="primary"
                icon={<SendOutlined />}
                onClick={() => {
                  setTargetOrderNo(record.orderNo);
                  shipForm.resetFields();
                  setShipModalVisible(true);
                }}
              >
                Ship
              </Button>
            )}

            {allowed.includes('completed') && (
              <Button
                size="small"
                type="dashed"
                icon={<CheckCircleOutlined className="text-emerald-600" />}
                onClick={() => handleStatusTransition(record.orderNo, 'completed')}
              >
                Complete
              </Button>
            )}

            {allowed.includes('cancelled') && (
              <Popconfirm
                title="Cancel Order"
                description="Are you sure you want to cancel this order? Stock will be automatically refunded."
                onConfirm={() => handleStatusTransition(record.orderNo, 'cancelled')}
                okText="Yes, Cancel"
                cancelText="No"
              >
                <Button size="small" danger icon={<CloseCircleOutlined />}>
                  Cancel
                </Button>
              </Popconfirm>
            )}
          </Space>
        );
      },
    },
  ];

  return (
    <div className="space-y-6">
      <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4">
        <div>
          <Title level={3} className="!mb-1">
            {t('admin.orders')}
          </Title>
          <Text type="secondary">Inspect, dispatch and manage customer orders across all states.</Text>
        </div>
        <Button icon={<ReloadOutlined />} onClick={() => fetchOrders()}>
          Refresh
        </Button>
      </div>

      <Card bordered={false} className="shadow-sm border border-gray-100 dark:border-gray-800">
        {/* 筛选与搜索 */}
        <div className="flex flex-wrap items-center gap-4 mb-4">
          <Input
            placeholder="Search Order No (e.g. SO2026...)"
            value={searchOrderNo}
            onChange={(e) => setSearchOrderNo(e.target.value)}
            onPressEnter={handleSearch}
            prefix={<SearchOutlined />}
            style={{ width: 280 }}
            allowClear
          />

          <Select
            value={filterStatus}
            onChange={(val) => setFilterStatus(val)}
            style={{ width: 180 }}
            options={[
              { value: 'all', label: 'All Statuses' },
              { value: 'pending', label: 'Pending Payment' },
              { value: 'paid', label: 'Paid' },
              { value: 'processing', label: 'Processing' },
              { value: 'shipped', label: 'Shipped' },
              { value: 'completed', label: 'Completed' },
              { value: 'cancelled', label: 'Cancelled' },
            ]}
          />

          <Button type="primary" onClick={handleSearch}>
            Search
          </Button>
        </div>

        {/* 订单表格 */}
        <Table
          rowKey="orderNo"
          dataSource={orders}
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
              fetchOrders(p, s);
            },
          }}
        />
      </Card>

      {/* 订单详情抽屉 */}
      <Drawer
        title={`Order Details: ${selectedOrder?.orderNo || ''}`}
        placement="right"
        width={600}
        onClose={() => setDrawerVisible(false)}
        open={drawerVisible}
      >
        {selectedOrder && (
          <div className="space-y-6">
            <Descriptions title="Order Info" bordered column={1} size="small">
              <Descriptions.Item label="Order No">{selectedOrder.orderNo}</Descriptions.Item>
              <Descriptions.Item label="Status">
                {getStatusBadge(selectedOrder.status)}
              </Descriptions.Item>
              <Descriptions.Item label="User ID">{selectedOrder.userId}</Descriptions.Item>
              <Descriptions.Item label="Total Pay Amount">
                {formatPrice(Math.round(selectedOrder.payAmount * 100), (selectedOrder.currency as any) || 'AUD')}
              </Descriptions.Item>
              <Descriptions.Item label="Placed At">{selectedOrder.createTime}</Descriptions.Item>
              <Descriptions.Item label="Paid At">{selectedOrder.payTime || '—'}</Descriptions.Item>
              <Descriptions.Item label="Shipped At">{selectedOrder.shipTime || '—'}</Descriptions.Item>
              <Descriptions.Item label="Completed At">{selectedOrder.completeTime || '—'}</Descriptions.Item>
              <Descriptions.Item label="Remark">{selectedOrder.remark || 'None'}</Descriptions.Item>
            </Descriptions>

            <Divider />

            {/* 收货地址快照 */}
            <div>
              <h4 className="font-semibold mb-2">Shipping Address Snapshot</h4>
              {(() => {
                const addr = parseAddress(selectedOrder.addressSnapshot);
                if (!addr) return <Text type="secondary">No address available</Text>;
                return (
                  <div className="bg-gray-50 dark:bg-gray-800 p-4 rounded text-sm space-y-1">
                    <p className="font-medium">
                      {addr.receiver} ({addr.phone})
                    </p>
                    <p className="text-gray-600 dark:text-gray-300">
                      {addr.detail}, {addr.city} {addr.state} {addr.postcode}, {addr.country || 'Australia'}
                    </p>
                  </div>
                );
              })()}
            </div>

            <Divider />

            {/* 购买商品列表 */}
            <div>
              <h4 className="font-semibold mb-3">Order Items Snapshot</h4>
              <div className="space-y-3">
                {selectedOrder.items?.map((item) => (
                  <div
                    key={item.id}
                    className="flex items-center justify-between border-b pb-3 border-gray-100 dark:border-gray-800"
                  >
                    <div className="flex items-center gap-3">
                      {item.productImage ? (
                        <img
                          src={item.productImage}
                          alt={item.productName}
                          className="w-12 h-12 object-cover rounded shadow-sm"
                        />
                      ) : (
                        <div className="w-12 h-12 bg-gray-100 dark:bg-gray-800 rounded flex items-center justify-center text-xs text-gray-400">
                          No Pic
                        </div>
                      )}
                      <div>
                        <p className="font-medium text-sm">{item.productName}</p>
                        <p className="text-xs text-gray-500">Qty: {item.qty}</p>
                      </div>
                    </div>
                    <span className="font-medium">
                      {formatPrice(Math.round(item.price * item.qty * 100), (selectedOrder.currency as any) || 'AUD')}
                    </span>
                  </div>
                ))}
              </div>
            </div>
          </div>
        )}
      </Drawer>

      {/* 发货物流单号 Modal */}
      <Modal
        title={`Ship Order: ${targetOrderNo}`}
        open={shipModalVisible}
        onOk={handleShipSubmit}
        onCancel={() => setShipModalVisible(false)}
        okText="Confirm Dispatch"
      >
        <Form form={shipForm} layout="vertical">
          <Form.Item
            name="trackingNo"
            label="Tracking / Consignment Number"
            rules={[{ required: true, message: 'Please enter tracking number' }]}
          >
            <Input placeholder="e.g. AUSPOST-77889900" />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}
