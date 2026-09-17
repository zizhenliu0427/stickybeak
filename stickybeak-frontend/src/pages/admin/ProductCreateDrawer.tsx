import { useEffect, useState } from 'react';
import {
  Button,
  Drawer,
  Form,
  Input,
  InputNumber,
  Select,
  Switch,
  Upload,
  message,
} from 'antd';
import type { UploadFile } from 'antd';
import { InboxOutlined } from '@ant-design/icons';
import {
  createProduct,
  getAdminCategories,
  getUploadUrl,
  uploadToPresigned,
  type AdminCategory,
} from '../../lib/admin';

interface Props {
  open: boolean;
  onClose: () => void;
  onCreated: (id: number) => void;
}

interface FormValues {
  name: string;
  slug?: string;
  categoryId: number;
  price: number;
  stock: number;
  description?: string;
  tags?: string[];
  featured?: boolean;
}

/** 管理端新建商品抽屉：MinIO 预签名直传图片 + 表单提交 */
export default function ProductCreateDrawer({ open, onClose, onCreated }: Props) {
  const [form] = Form.useForm<FormValues>();
  const [categories, setCategories] = useState<AdminCategory[]>([]);
  const [fileList, setFileList] = useState<UploadFile[]>([]);
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    if (open) {
      getAdminCategories().then(setCategories).catch(() => setCategories([]));
    }
  }, [open]);

  const reset = () => {
    form.resetFields();
    setFileList([]);
  };

  /** 自定义上传：拿预签名 URL → PUT 直传 MinIO → 记录公网 URL */
  const handleUpload = async (file: File, uploadFile: UploadFile) => {
    try {
      const presigned = await getUploadUrl(file.name, file.type || 'image/jpeg');
      await uploadToPresigned(presigned.uploadUrl, file);
      uploadFile.url = presigned.publicUrl;
      setFileList((prev) =>
        prev.map((f) => (f.uid === uploadFile.uid ? { ...f, status: 'done', url: presigned.publicUrl } : f)),
      );
      message.success(`${file.name} uploaded`);
    } catch (e) {
      setFileList((prev) => prev.map((f) => (f.uid === uploadFile.uid ? { ...f, status: 'error' } : f)));
      message.error(`${file.name} upload failed: ${e instanceof Error ? e.message : e}`);
    }
  };

  const onSubmit = async () => {
    const values = await form.validateFields();
    if (fileList.some((f) => f.status === 'uploading')) {
      message.warning('Images still uploading, please wait');
      return;
    }
    const images = fileList
      .filter((f) => f.status === 'done' && f.url)
      .map((f) => f.url as string);
    if (images.length === 0) {
      message.warning('Please upload at least one product image');
      return;
    }
    setSubmitting(true);
    try {
      const { id } = await createProduct({
        ...values,
        images,
        featured: Boolean(values.featured),
      });
      message.success(`Product #${id} created and listed`);
      reset();
      onCreated(id);
      onClose();
    } catch (e) {
      message.error(e instanceof Error ? e.message : 'create failed');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Drawer
      title="New Product"
      width={520}
      open={open}
      onClose={() => {
        reset();
        onClose();
      }}
      destroyOnHidden
      footer={
        <div className="flex justify-end gap-2">
          <Button onClick={onClose}>Cancel</Button>
          <Button type="primary" loading={submitting} onClick={onSubmit}>
            Create &amp; List
          </Button>
        </div>
      }
    >
      <Form form={form} layout="vertical" initialValues={{ stock: 50, featured: false }}>
        <Form.Item name="name" label="Product name" rules={[{ required: true, message: 'Required' }]}>
          <Input placeholder="e.g. 悉尼火车 T9 线路牌冰箱贴" maxLength={128} showCount />
        </Form.Item>

        <Form.Item
          name="slug"
          label="URL slug (optional)"
          tooltip="Leave blank to auto-generate p-{id}"
        >
          <Input placeholder="sydney-t9-train-sign" maxLength={160} />
        </Form.Item>

        <div className="grid grid-cols-2 gap-x-4">
          <Form.Item name="categoryId" label="Category" rules={[{ required: true, message: 'Required' }]}>
            <Select
              placeholder="Select category"
              options={categories.map((c) => ({ value: c.id, label: c.name }))}
              showSearch
              optionFilterProp="label"
            />
          </Form.Item>
          <Form.Item name="featured" label="Featured (homepage)" valuePropName="checked">
            <Switch />
          </Form.Item>
        </div>

        <div className="grid grid-cols-2 gap-x-4">
          <Form.Item
            name="price"
            label="Price (AUD)"
            rules={[{ required: true, message: 'Required' }]}
          >
            <InputNumber min={0.01} precision={2} className="w-full" prefix="$" />
          </Form.Item>
          <Form.Item name="stock" label="Stock" rules={[{ required: true, message: 'Required' }]}>
            <InputNumber min={0} precision={0} className="w-full" />
          </Form.Item>
        </div>

        <Form.Item name="description" label="Description">
          <Input.TextArea rows={4} maxLength={2000} placeholder="商品介绍，支持换行" />
        </Form.Item>

        <Form.Item name="tags" label="Tags (free input)">
          <Select mode="tags" placeholder="e.g. T9, 悉尼火车" open={false} tokenSeparators={[',', '，']} />
        </Form.Item>

        <Form.Item label="Images (first = cover)" required>
          <Upload.Dragger
            multiple
            accept="image/jpeg,image/png,image/webp,image/gif"
            fileList={fileList}
            beforeUpload={(file) => {
              const uf: UploadFile = {
                uid: file.uid,
                name: file.name,
                size: file.size,
                type: file.type,
                status: 'uploading',
              };
              setFileList((prev) => [...prev, uf]);
              handleUpload(file, uf);
              return false; // 阻止 antd 默认上传，走预签名直传
            }}
            onRemove={(file) => setFileList((prev) => prev.filter((f) => f.uid !== file.uid))}
          >
            <p className="ant-upload-drag-icon">
              <InboxOutlined />
            </p>
            <p className="ant-upload-text">Click or drag images here</p>
            <p className="ant-upload-hint">jpeg / png / webp / gif，直传对象存储（MinIO）</p>
          </Upload.Dragger>
        </Form.Item>
      </Form>
    </Drawer>
  );
}
