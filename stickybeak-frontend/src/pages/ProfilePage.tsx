import { useEffect, useState } from 'react';
import {
  Avatar,
  Button,
  Card,
  Checkbox,
  Empty,
  Form,
  Input,
  Modal,
  Popconfirm,
  Space,
  Tag,
  Typography,
  message,
} from 'antd';
import { DeleteOutlined, EditOutlined, PlusOutlined, UserOutlined } from '@ant-design/icons';
import { useAppDispatch, useAppSelector } from '../app/hooks';
import {
  createAddress,
  deleteAddress,
  fetchAddresses,
  updateAddress,
  updateProfile,
} from '../features/auth/authSlice';
import type { Address, AddressPayload } from '../features/auth/authSlice';
import McpTokenCard from '../components/McpTokenCard';
import { useI18n } from '../lib/i18n';

const AU_STATES = ['NSW', 'VIC', 'QLD', 'WA', 'SA', 'TAS', 'ACT', 'NT'];

export default function ProfilePage() {
  const dispatch = useAppDispatch();
  const user = useAppSelector((s) => s.auth.user);
  const [addresses, setAddresses] = useState<Address[]>([]);
  const [loadingAddr, setLoadingAddr] = useState(true);
  const [profileOpen, setProfileOpen] = useState(false);
  const [addrOpen, setAddrOpen] = useState(false);
  const [editing, setEditing] = useState<Address | null>(null);
  const [profileForm] = Form.useForm();
  const [addrForm] = Form.useForm();
  const { t } = useI18n();

  const loadAddresses = () => {
    setLoadingAddr(true);
    dispatch(fetchAddresses())
      .unwrap()
      .then(setAddresses)
      .catch(() => setAddresses([]))
      .finally(() => setLoadingAddr(false));
  };

  useEffect(loadAddresses, [dispatch]);

  if (!user) {
    return null; // RequireAuth 保证不会到这
  }

  const openCreate = () => {
    setEditing(null);
    addrForm.resetFields();
    setAddrOpen(true);
  };

  const openEdit = (addr: Address) => {
    setEditing(addr);
    addrForm.setFieldsValue(addr);
    setAddrOpen(true);
  };

  const submitProfile = async () => {
    const values = await profileForm.validateFields();
    try {
      await dispatch(updateProfile(values)).unwrap();
      message.success(t('profile.updated'));
      setProfileOpen(false);
    } catch (e) {
      message.error(String(e));
    }
  };

  const submitAddress = async () => {
    const values = (await addrForm.validateFields()) as AddressPayload;
    try {
      if (editing) {
        await dispatch(updateAddress({ id: editing.id, body: values })).unwrap();
        message.success(t('profile.addressUpdated'));
      } else {
        await dispatch(createAddress(values)).unwrap();
        message.success(t('profile.addressAdded'));
      }
      setAddrOpen(false);
      loadAddresses();
    } catch (e) {
      message.error(String(e));
    }
  };

  const removeAddress = async (id: string) => {
    try {
      await dispatch(deleteAddress(id)).unwrap();
      message.success(t('profile.addressDeleted'));
      loadAddresses();
    } catch (e) {
      message.error(String(e));
    }
  };

  return (
    <div className="mx-auto max-w-3xl space-y-6">
      <Card>
        <div className="flex items-center gap-4">
          <Avatar size={64} icon={<UserOutlined />} src={user.avatarUrl ?? undefined} />
          <div className="flex-1">
            <Typography.Title level={4} className="!m-0">
              {user.nickname}
            </Typography.Title>
            <div className="text-gray-500">{user.email}</div>
            <Space size={4} className="mt-1">
              {user.roles.map((r) => (
                <Tag key={r} color={r === 'customer' ? 'blue' : 'gold'}>
                  {r}
                </Tag>
              ))}
            </Space>
          </div>
          <Button
            icon={<EditOutlined />}
            onClick={() => {
              profileForm.setFieldsValue({ nickname: user.nickname, phone: user.phone ?? '' });
              setProfileOpen(true);
            }}
          >
            {t('profile.edit')}
          </Button>
        </div>
      </Card>

      <Card
        title={t('profile.addresses')}
        extra={
          <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>
            {t('profile.addAddress')}
          </Button>
        }
        loading={loadingAddr}
      >
        {addresses.length === 0 ? (
          <Empty description={t('profile.noAddress')} />
        ) : (
          <ul className="divide-y">
            {addresses.map((a) => (
              <li key={a.id} className="py-3 flex items-start gap-3">
                <div className="flex-1">
                  <div className="font-medium">
                    {a.receiver} · {a.phone}
                    {a.isDefault && (
                      <Tag color="green" className="ml-2">
                        {t('profile.default')}
                      </Tag>
                    )}
                  </div>
                  <div className="text-gray-500 text-sm">
                    {a.detail}, {a.city}, {a.state} {a.postcode}, {a.country}
                  </div>
                </div>
                <Space>
                  <Button size="small" icon={<EditOutlined />} onClick={() => openEdit(a)} />
                  <Popconfirm title={t('profile.deleteConfirm')} onConfirm={() => removeAddress(a.id)}>
                    <Button size="small" danger icon={<DeleteOutlined />} />
                  </Popconfirm>
                </Space>
              </li>
            ))}
          </ul>
        )}
      </Card>

      <McpTokenCard />

      {/* 编辑资料 */}
      <Modal
        title={t('profile.editTitle')}
        open={profileOpen}
        onOk={submitProfile}
        onCancel={() => setProfileOpen(false)}
        okText={t('profile.save')}
      >
        <Form form={profileForm} layout="vertical">
          <Form.Item name="nickname" label={t('profile.nickname')} rules={[{ max: 32 }]}>
            <Input />
          </Form.Item>
          <Form.Item name="phone" label={t('profile.phone')} rules={[{ max: 20 }]}>
            <Input />
          </Form.Item>
        </Form>
      </Modal>

      {/* 新建/编辑地址 */}
      <Modal
        title={editing ? t('profile.editAddress') : t('profile.addAddress')}
        open={addrOpen}
        onOk={submitAddress}
        onCancel={() => setAddrOpen(false)}
        okText={t('profile.save')}
        destroyOnHidden
      >
        <Form form={addrForm} layout="vertical" initialValues={{ country: 'Australia' }}>
          <div className="grid grid-cols-2 gap-x-4">
            <Form.Item name="receiver" label={t('profile.receiver')} rules={[{ required: true }]}>
              <Input />
            </Form.Item>
            <Form.Item name="phone" label={t('profile.phone')} rules={[{ required: true }]}>
              <Input />
            </Form.Item>
          </div>
          <Form.Item name="detail" label={t('profile.street')} rules={[{ required: true }]}>
            <Input placeholder={t('profile.streetPlaceholder')} />
          </Form.Item>
          <div className="grid grid-cols-3 gap-x-4">
            <Form.Item name="city" label={t('profile.city')} rules={[{ required: true }]}>
              <Input />
            </Form.Item>
            <Form.Item
              name="state"
              label={t('profile.state')}
              rules={[
                { required: true },
                {
                  validator: (_, v) =>
                    !v || AU_STATES.includes(String(v).toUpperCase())
                      ? Promise.resolve()
                      : Promise.reject(new Error(t('profile.stateInvalid', { states: AU_STATES.join('/') }))),
                },
              ]}
            >
              <Input placeholder="NSW" />
            </Form.Item>
            <Form.Item
              name="postcode"
              label={t('profile.postcode')}
              rules={[{ required: true }, { pattern: /^\d{4}$/, message: t('profile.postcodeInvalid') }]}
            >
              <Input placeholder="2000" maxLength={4} />
            </Form.Item>
          </div>
          <Form.Item name="isDefault" valuePropName="checked">
            <Checkbox>{t('profile.setDefault')}</Checkbox>
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}
