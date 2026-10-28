import { useEffect, useState } from 'react';
import { Alert, Button, Card, Input, Popconfirm, Space, Typography, message } from 'antd';
import { ApiOutlined, CopyOutlined } from '@ant-design/icons';
import { getMcpToken, issueMcpToken, mcpClientConfig, mcpEndpoint, revokeMcpToken } from '../lib/mcp';
import type { McpToken } from '../lib/mcp';
import { useI18n } from '../lib/i18n';

/** 个人中心：生成 / 撤销给外部 AI 客户端用的 MCP 令牌，并给出客户端配置。 */
export default function McpTokenCard() {
  const { t, locale } = useI18n();
  const [status, setStatus] = useState<McpToken | null>(null);
  const [issued, setIssued] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    getMcpToken()
      .then(setStatus)
      .catch(() => setStatus({ active: false, token: null, expiresAt: null }));
  }, []);

  const expires = status?.expiresAt ? new Date(status.expiresAt).toLocaleDateString(locale) : '';

  const issue = async () => {
    setBusy(true);
    try {
      const result = await issueMcpToken();
      setIssued(result.token);
      setStatus({ ...result, token: null });
    } catch (e) {
      message.error(String(e));
    } finally {
      setBusy(false);
    }
  };

  const revoke = async () => {
    setBusy(true);
    try {
      await revokeMcpToken();
      setIssued(null);
      setStatus({ active: false, token: null, expiresAt: null });
      message.success(t('mcp.revoked'));
    } catch (e) {
      message.error(String(e));
    } finally {
      setBusy(false);
    }
  };

  const copy = async (text: string) => {
    try {
      await navigator.clipboard.writeText(text);
      message.success(t('mcp.copied'));
    } catch {
      // 浏览器不允许访问剪贴板时，用户可以手动选中复制
    }
  };

  return (
    <Card
      title={
        <span>
          <ApiOutlined className="mr-2" />
          {t('mcp.title')}
        </span>
      }
      loading={status === null}
    >
      <Typography.Paragraph className="text-gray-600 dark:text-stone-300">{t('mcp.description')}</Typography.Paragraph>
      <Typography.Paragraph>
        {t('mcp.endpoint')}: <Typography.Text code>{mcpEndpoint()}</Typography.Text>
      </Typography.Paragraph>
      <Typography.Paragraph>
        {status?.active ? t('mcp.activeUntil', { date: expires }) : t('mcp.none')}
      </Typography.Paragraph>
      <Space className="mb-3">
        <Button type="primary" loading={busy} onClick={issue}>
          {status?.active ? t('mcp.regenerate') : t('mcp.generate')}
        </Button>
        {status?.active && (
          <Popconfirm title={t('mcp.revokeConfirm')} onConfirm={revoke}>
            <Button danger disabled={busy}>
              {t('mcp.revoke')}
            </Button>
          </Popconfirm>
        )}
      </Space>
      {issued && (
        <div className="space-y-2">
          <Alert type="warning" showIcon message={t('mcp.showOnce')} />
          <Input.TextArea
            readOnly
            value={mcpClientConfig(issued)}
            autoSize={{ minRows: 6, maxRows: 12 }}
            aria-label={t('mcp.config')}
            className="font-mono text-xs"
          />
          <Space>
            <Button icon={<CopyOutlined />} onClick={() => copy(mcpClientConfig(issued))}>
              {t('mcp.copyConfig')}
            </Button>
            <Button icon={<CopyOutlined />} onClick={() => copy(issued)}>
              {t('mcp.copyToken')}
            </Button>
          </Space>
        </div>
      )}
    </Card>
  );
}
