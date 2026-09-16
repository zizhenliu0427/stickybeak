import { useEffect, useRef, useState } from 'react';
import { Card, Row, Col, Statistic, Radio, Button, Table, Typography, Space, Spin, message } from 'antd';
import {
  DollarOutlined,
  ShoppingCartOutlined,
  InboxOutlined,
  AlertOutlined,
  DownloadOutlined,
  ReloadOutlined,
} from '@ant-design/icons';
import * as echarts from 'echarts';
import {
  getAnalyticsSummary,
  getSalesTrend,
  getTopProducts,
  getStockAlerts,
  type AnalyticsSummary,
  type SalesTrendItem,
  type TopProduct,
  type StockAlertSummary,
} from '../../lib/admin';
import { formatPrice } from '../../lib/format';
import { useI18n } from '../../lib/i18n';
import { useAppSelector } from '../../app/hooks';

const { Title, Text } = Typography;

export default function AdminDashboardPage() {
  const { t } = useI18n();
  const themeMode = useAppSelector((s) => s.theme.mode);

  const [loading, setLoading] = useState(true);
  const [summary, setSummary] = useState<AnalyticsSummary | null>(null);
  const [stockAlerts, setStockAlerts] = useState<StockAlertSummary | null>(null);
  const [topProducts, setTopProducts] = useState<TopProduct[]>([]);
  const [trendDays, setTrendDays] = useState<number>(7);
  const [trendData, setTrendData] = useState<SalesTrendItem[]>([]);

  const chartRef = useRef<HTMLDivElement>(null);
  const chartInstanceRef = useRef<echarts.ECharts | null>(null);

  const fetchDashboardData = async () => {
    setLoading(true);
    try {
      const [sumRes, alertRes, topsRes, trendRes] = await Promise.all([
        getAnalyticsSummary(),
        getStockAlerts(10),
        getTopProducts(10),
        getSalesTrend(trendDays),
      ]);
      setSummary(sumRes);
      setStockAlerts(alertRes);
      setTopProducts(topsRes);
      setTrendData(trendRes);
    } catch (err: any) {
      message.error(err?.message || 'Failed to load dashboard data');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchDashboardData();
  }, [trendDays]);

  // 初始化与更新 ECharts
  useEffect(() => {
    if (!chartRef.current || trendData.length === 0) return;

    if (!chartInstanceRef.current) {
      chartInstanceRef.current = echarts.init(chartRef.current, themeMode === 'dark' ? 'dark' : undefined);
    } else {
      chartInstanceRef.current.dispose();
      chartInstanceRef.current = echarts.init(chartRef.current, themeMode === 'dark' ? 'dark' : undefined);
    }

    const dates = trendData.map((d) => d.date);
    const gmvs = trendData.map((d) => d.gmv);
    const orderCounts = trendData.map((d) => d.orderCount);

    const isDark = themeMode === 'dark';

    const option: echarts.EChartsOption = {
      backgroundColor: 'transparent',
      tooltip: {
        trigger: 'axis',
        axisPointer: { type: 'cross' },
      },
      legend: {
        data: ['GMV (AUD)', 'Orders'],
        textStyle: { color: isDark ? '#e5e7eb' : '#374151' },
      },
      grid: {
        left: '3%',
        right: '4%',
        bottom: '3%',
        containLabel: true,
      },
      xAxis: {
        type: 'category',
        data: dates,
        axisLine: { lineStyle: { color: isDark ? '#4b5563' : '#d1d5db' } },
        axisLabel: { color: isDark ? '#9ca3af' : '#6b7280' },
      },
      yAxis: [
        {
          type: 'value',
          name: 'Revenue (AUD)',
          position: 'left',
          axisLabel: {
            formatter: '${value}',
            color: isDark ? '#9ca3af' : '#6b7280',
          },
          splitLine: { lineStyle: { color: isDark ? '#374151' : '#f3f4f6' } },
        },
        {
          type: 'value',
          name: 'Orders',
          position: 'right',
          axisLabel: {
            formatter: '{value}',
            color: isDark ? '#9ca3af' : '#6b7280',
          },
          splitLine: { show: false },
        },
      ],
      series: [
        {
          name: 'GMV (AUD)',
          type: 'line',
          smooth: true,
          data: gmvs,
          itemStyle: { color: '#059669' },
          areaStyle: {
            color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
              { offset: 0, color: 'rgba(5, 150, 105, 0.4)' },
              { offset: 1, color: 'rgba(5, 150, 105, 0.02)' },
            ]),
          },
        },
        {
          name: 'Orders',
          type: 'bar',
          yAxisIndex: 1,
          data: orderCounts,
          itemStyle: { color: '#3b82f6', borderRadius: [4, 4, 0, 0] },
          barMaxWidth: 30,
        },
      ],
    };

    chartInstanceRef.current.setOption(option);

    const handleResize = () => chartInstanceRef.current?.resize();
    window.addEventListener('resize', handleResize);
    return () => {
      window.removeEventListener('resize', handleResize);
    };
  }, [trendData, themeMode]);

  // 导出 CSV 报表
  const handleExportCsv = () => {
    if (trendData.length === 0) {
      message.warning('No data available to export');
      return;
    }

    const headers = ['Date', 'Orders Count', 'GMV (AUD)'];
    const rows = trendData.map((row) => [row.date, row.orderCount, row.gmv.toFixed(2)]);
    const csvContent =
      'data:text/csv;charset=utf-8,\uFEFF' +
      [headers.join(','), ...rows.map((e) => e.join(','))].join('\n');

    const encodedUri = encodeURI(csvContent);
    const link = document.createElement('a');
    link.setAttribute('href', encodedUri);
    link.setAttribute('download', `stickybeak_sales_report_${trendDays}d.csv`);
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    message.success('Report exported successfully');
  };

  const topColumns = [
    {
      title: '#',
      render: (_: any, __: any, index: number) => (
        <span className={`font-semibold ${index < 3 ? 'text-amber-500 font-bold' : ''}`}>
          {index + 1}
        </span>
      ),
      width: 50,
    },
    {
      title: 'Product',
      dataIndex: 'productName',
      key: 'productName',
      render: (text: string, record: TopProduct) => (
        <div className="flex items-center gap-3">
          {record.productImage ? (
            <img src={record.productImage} alt={text} className="w-10 h-10 object-cover rounded shadow-sm" />
          ) : (
            <div className="w-10 h-10 bg-gray-100 dark:bg-gray-800 rounded flex items-center justify-center text-xs text-gray-400">
              No Pic
            </div>
          )}
          <span className="font-medium truncate max-w-xs">{text}</span>
        </div>
      ),
    },
    {
      title: 'Units Sold',
      dataIndex: 'totalQuantity',
      key: 'totalQuantity',
      sorter: (a: TopProduct, b: TopProduct) => a.totalQuantity - b.totalQuantity,
      render: (val: number) => <span className="font-semibold text-emerald-600 dark:text-emerald-400">{val}</span>,
    },
    {
      title: 'Revenue (AUD)',
      dataIndex: 'totalRevenue',
      key: 'totalRevenue',
      render: (val: number) => formatPrice(Math.round(val * 100), 'AUD'),
    },
  ];

  return (
    <div className="space-y-6">
      {/* 顶部标题与操作栏 */}
      <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4">
        <div>
          <Title level={3} className="!mb-1">
            {t('admin.dashboard')}
          </Title>
          <Text type="secondary">Real-time overview of revenue, orders and stock status.</Text>
        </div>
        <Space>
          <Button icon={<ReloadOutlined />} onClick={fetchDashboardData} loading={loading}>
            Refresh
          </Button>
          <Button type="primary" icon={<DownloadOutlined />} onClick={handleExportCsv}>
            {t('admin.exportCsv')}
          </Button>
        </Space>
      </div>

      {/* 4 块核心 KPI 指标卡片 */}
      <Row gutter={[16, 16]}>
        <Col xs={24} sm={12} lg={6}>
          <Card bordered={false} className="shadow-sm border border-gray-100 dark:border-gray-800">
            <Statistic
              title={t('admin.totalGmv')}
              value={summary ? summary.totalGmv : 0}
              precision={2}
              prefix={<DollarOutlined className="text-emerald-500" />}
              suffix="AUD"
              loading={loading}
            />
            <div className="mt-2 text-xs text-gray-500 flex items-center justify-between">
              <span>{t('admin.todayGmv')}:</span>
              <span className="font-semibold text-emerald-600">
                ${summary ? summary.todayGmv.toFixed(2) : '0.00'} AUD
              </span>
            </div>
          </Card>
        </Col>

        <Col xs={24} sm={12} lg={6}>
          <Card bordered={false} className="shadow-sm border border-gray-100 dark:border-gray-800">
            <Statistic
              title={t('admin.totalOrders')}
              value={summary ? summary.totalOrders : 0}
              prefix={<ShoppingCartOutlined className="text-blue-500" />}
              loading={loading}
            />
            <div className="mt-2 text-xs text-gray-500 flex items-center justify-between">
              <span>Paid Orders:</span>
              <span className="font-semibold">{summary ? summary.paidOrders : 0}</span>
            </div>
          </Card>
        </Col>

        <Col xs={24} sm={12} lg={6}>
          <Card bordered={false} className="shadow-sm border border-gray-100 dark:border-gray-800">
            <Statistic
              title={t('admin.pendingShipment')}
              value={summary ? summary.pendingShipmentOrders : 0}
              prefix={<InboxOutlined className="text-amber-500" />}
              valueStyle={{ color: '#d97706' }}
              loading={loading}
            />
            <div className="mt-2 text-xs text-gray-500 flex items-center justify-between">
              <span>Pending Payment:</span>
              <span>{summary ? summary.pendingPaymentOrders : 0}</span>
            </div>
          </Card>
        </Col>

        <Col xs={24} sm={12} lg={6}>
          <Card bordered={false} className="shadow-sm border border-gray-100 dark:border-gray-800">
            <Statistic
              title={t('admin.stockAlerts')}
              value={stockAlerts ? stockAlerts.totalAlerts : 0}
              prefix={<AlertOutlined className="text-red-500" />}
              valueStyle={{ color: '#ef4444' }}
              loading={loading}
            />
            <div className="mt-2 text-xs text-gray-500 flex items-center justify-between">
              <span>Out of stock: {stockAlerts ? stockAlerts.outOfStockCount : 0}</span>
              <span>Critical: {stockAlerts ? stockAlerts.criticalCount : 0}</span>
            </div>
          </Card>
        </Col>
      </Row>

      {/* 销售走势折线/柱状图 */}
      <Card
        title={t('admin.salesTrend')}
        extra={
          <Radio.Group
            value={trendDays}
            onChange={(e) => setTrendDays(e.target.value)}
            optionType="button"
            buttonStyle="solid"
            size="small"
          >
            <Radio.Button value={7}>7 Days</Radio.Button>
            <Radio.Button value={30}>30 Days</Radio.Button>
            <Radio.Button value={90}>90 Days</Radio.Button>
          </Radio.Group>
        }
        bordered={false}
        className="shadow-sm border border-gray-100 dark:border-gray-800"
      >
        {loading ? (
          <div className="h-80 flex items-center justify-center">
            <Spin size="large" />
          </div>
        ) : (
          <div ref={chartRef} className="w-full h-80" />
        )}
      </Card>

      {/* Top 10 热销排行榜 */}
      <Card
        title={t('admin.topProducts')}
        bordered={false}
        className="shadow-sm border border-gray-100 dark:border-gray-800"
      >
        <Table
          rowKey="productId"
          dataSource={topProducts}
          columns={topColumns}
          pagination={false}
          loading={loading}
        />
      </Card>
    </div>
  );
}
