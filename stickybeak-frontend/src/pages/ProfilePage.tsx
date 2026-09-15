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
      message.success('Profile updated');
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
        message.success('Address updated');
      } else {
        await dispatch(createAddress(values)).unwrap();
        message.success('Address added');
      }
      setAddrOpen(false);
      loadAddresses();
    } catch (e) {
      message.error(String(e));
    }
  };

  const removeAddress = async (id: number) => {
    try {
      await dispatch(deleteAddress(id)).unwrap();
      message.success('Address deleted');
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
            Edit
          </Button>
        </div>
      </Card>

      <Card
        title="Addresses"
        extra={
          <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>
            Add address
          </Button>
        }
        loading={loadingAddr}
      >
        {addresses.length === 0 ? (
          <Empty description="No address yet — add one for checkout" />
        ) : (
          <ul className="divide-y">
            {addresses.map((a) => (
              <li key={a.id} className="py-3 flex items-start gap-3">
                <div className="flex-1">
                  <div className="font-medium">
                    {a.receiver} · {a.phone}
                    {a.isDefault && (
                      <Tag color="green" className="ml-2">
                        default
                      </Tag>
                    )}
                  </div>
                  <div className="text-gray-500 text-sm">
                    {a.detail}, {a.city}, {a.state} {a.postcode}, {a.country}
                  </div>
                </div>
                <Space>
                  <Button size="small" icon={<EditOutlined />} onClick={() => openEdit(a)} />
                  <Popconfirm title="Delete this address?" onConfirm={() => removeAddress(a.id)}>
                    <Button size="small" danger icon={<DeleteOutlined />} />
                  </Popconfirm>
                </Space>
              </li>
            ))}
          </ul>
        )}
      </Card>

      {/* 编辑资料 */}
      <Modal
        title="Edit profile"
        open={profileOpen}
        onOk={submitProfile}
        onCancel={() => setProfileOpen(false)}
        okText="Save"
      >
        <Form form={profileForm} layout="vertical">
          <Form.Item name="nickname" label="Nickname" rules={[{ max: 32 }]}>
            <Input />
          </Form.Item>
          <Form.Item name="phone" label="Phone" rules={[{ max: 20 }]}>
            <Input />
          </Form.Item>
        </Form>
      </Modal>

      {/* 新建/编辑地址 */}
      <Modal
        title={editing ? 'Edit address' : 'Add address'}
        open={addrOpen}
        onOk={submitAddress}
        onCancel={() => setAddrOpen(false)}
        okText="Save"
        destroyOnHidden
      >
        <Form form={addrForm} layout="vertical" initialValues={{ country: 'Australia' }}>
          <div className="grid grid-cols-2 gap-x-4">
            <Form.Item name="receiver" label="Receiver" rules={[{ required: true }]}>
              <Input />
            </Form.Item>
            <Form.Item name="phone" label="Phone" rules={[{ required: true }]}>
              <Input />
            </Form.Item>
          </div>
          <Form.Item name="detail" label="Street address" rules={[{ required: true }]}>
            <Input placeholder="Unit / street number / street name" />
          </Form.Item>
          <div className="grid grid-cols-3 gap-x-4">
            <Form.Item name="city" label="City" rules={[{ required: true }]}>
              <Input />
            </Form.Item>
            <Form.Item
              name="state"
              label="State"
              rules={[
                { required: true },
                {
                  validator: (_, v) =>
                    !v || AU_STATES.includes(String(v).toUpperCase())
                      ? Promise.resolve()
                      : Promise.reject(new Error(`One of ${AU_STATES.join('/')}`)),
                },
              ]}
            >
              <Input placeholder="NSW" />
            </Form.Item>
            <Form.Item
              name="postcode"
              label="Postcode"
              rules={[{ required: true }, { pattern: /^\d{4}$/, message: '4 digits' }]}
            >
              <Input placeholder="2000" maxLength={4} />
            </Form.Item>
          </div>
          <Form.Item name="isDefault" valuePropName="checked">
            <Checkbox>Set as default address</Checkbox>
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}
