import { useEffect, useState } from 'react';
import { Alert, Button, Card, Form, InputNumber, Select, Space, Typography, message } from 'antd';
import { api, errorMessage } from '../lib/api';
import { useI18n } from '../lib/i18n';

interface Preferences {
  category: string | null;
  max_price_aud: number | null;
}
interface Memory {
  preferences: Preferences;
  updated_at: number;
  expires_at: number;
}
const empty: Preferences = { category: null, max_price_aud: null };
const categories = [
  ['bus-sign', '公交站牌', 'Bus signs'], ['supermarket', '超市', 'Supermarkets'],
  ['train', '火车', 'Trains'], ['tram', '电车', 'Trams'], ['bird', '鸟类', 'Birds'],
  ['road-sign', '路牌', 'Road signs'], ['collections', '套装', 'Collections'],
  ['achievement', '成就徽章', 'Achievements'], ['slogan', '标语', 'Slogans'], ['phonecase', '手机壳', 'Phone cases'],
];

export default function AssistantPreferencesCard() {
  const { locale } = useI18n();
  const zh = locale === 'zh-CN';
  const [form] = Form.useForm<Preferences>();
  const [memory, setMemory] = useState<Memory | null>(null);
  const [busy, setBusy] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    let active = true;
    api.get<{ memory: Memory | null }>('/agent/preferences').then(({ data }) => {
      if (active) {
        setMemory(data.memory);
        form.setFieldsValue(data.memory?.preferences ?? empty);
      }
    }).catch((e: unknown) => { if (active) setError(errorMessage(e)); })
      .finally(() => { if (active) setBusy(false); });
    return () => { active = false; };
  }, [form]);

  const save = async (values: Preferences) => {
    setBusy(true);
    try {
      const { data } = await api.put<{ memory: Memory }>('/agent/preferences', {
        category: values.category ?? null, max_price_aud: values.max_price_aud ?? null,
      });
      setMemory(data.memory);
      message.success(zh ? '偏好已保存，下次回答起生效' : 'Saved. Applies from your next answer.');
    } catch (e) { message.error(errorMessage(e)); }
    finally { setBusy(false); }
  };
  const forget = async () => {
    setBusy(true);
    try {
      await api.delete('/agent/preferences');
      setMemory(null);
      form.setFieldsValue(empty);
      message.success(zh ? '已清除保存的偏好' : 'Saved preferences cleared');
    } catch (e) { message.error(errorMessage(e)); }
    finally { setBusy(false); }
  };

  return <Card title={zh ? '助手购物偏好' : 'Assistant shopping preferences'}>
    <Typography.Paragraph type="secondary">
      {zh ? '保存后，新对话也能沿用；本次聊天的明确要求优先。清除偏好不会删除聊天记录。' :
        'Use these defaults across chats. Your current request takes priority. Clearing preferences does not delete chat history.'}
    </Typography.Paragraph>
    {error && <Alert type="error" showIcon message={error} />}
    <Form form={form} layout="vertical" onFinish={save} disabled={busy || !!error} initialValues={empty}>
      <Form.Item name="category" label={zh ? '喜欢的类别' : 'Preferred category'}>
        <Select allowClear placeholder={zh ? '不限' : 'Any category'} options={categories.map(([value, cn, en]) => ({
          value, label: zh ? cn : en,
        }))} />
      </Form.Item>
      <Form.Item name="max_price_aud" label={zh ? '单件预算上限（AUD）' : 'Budget per item (AUD)'}>
        <InputNumber min={0.01} max={10000} precision={2} style={{ width: '100%' }} />
      </Form.Item>
      <Space wrap>
        <Button type="primary" htmlType="submit" loading={busy}>{zh ? '保存偏好' : 'Save preferences'}</Button>
        <Button onClick={forget} disabled={busy || !!error || !memory}>{zh ? '清除偏好' : 'Forget preferences'}</Button>
      </Space>
    </Form>
    {memory && <Typography.Paragraph type="secondary" className="!mb-0 !mt-3">
      {zh ? '自动过期：' : 'Expires: '}{new Date(memory.expires_at * 1000).toLocaleDateString(locale)}
    </Typography.Paragraph>}
  </Card>;
}
