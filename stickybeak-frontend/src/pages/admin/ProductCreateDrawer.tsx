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
  onCreated: (id: string) => void;
}

interface FormValues {
  name: string;
  slug?: string;
  categoryId: string;
  price: number;
  stock: number;
  description?: string;
  tags?: string[];
  featured?: boolean;
}

type MediaUploadFile = UploadFile & { mediaType?: 'image' | 'video' };

const IMAGE_MAX_BYTES = 8 * 1024 * 1024;
const VIDEO_MAX_BYTES = 80 * 1024 * 1024;

function guessContentType(file: File): string {
  if (file.type) return file.type;
  const n = file.name.toLowerCase();
  if (n.endsWith('.mp4')) return 'video/mp4';
  if (n.endsWith('.mov')) return 'video/quicktime';
  if (n.endsWith('.webm')) return 'video/webm';
  if (n.endsWith('.png')) return 'image/png';
  if (n.endsWith('.webp')) return 'image/webp';
  if (n.endsWith('.gif')) return 'image/gif';
  return 'image/jpeg';
}

/** 管理端新建商品抽屉：MinIO 预签名直传图片 + 表单提交 */
export default function ProductCreateDrawer({ open, onClose, onCreated }: Props) {
  const [form] = Form.useForm<FormValues>();
  const [categories, setCategories] = useState<AdminCategory[]>([]);
  const [fileList, setFileList] = useState<MediaUploadFile[]>([]);
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

  /** 自定义上传：拿预签名 URL → PUT 直传 MinIO → 记录公网 URL 与媒体类型 */
  const handleUpload = async (file: File, uploadFile: MediaUploadFile) => {
    try {
      const contentType = guessContentType(file);
      const presigned = await getUploadUrl(file.name, contentType);
      await uploadToPresigned(presigned.uploadUrl, file);
      const mediaType = contentType.startsWith('video/') ? 'video' : 'image';
      setFileList((prev) =>
        prev.map((f) =>
          f.uid === uploadFile.uid
            ? { ...f, status: 'done', url: presigned.publicUrl, mediaType }
            : f,
        ),
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
    const media = fileList
      .filter((f) => f.status === 'done' && f.url)
      .map((f) => ({
        url: f.url as string,
        mediaType: (f.mediaType ?? 'image') as 'image' | 'video',
      }));
    if (media.length === 0) {
      message.warning('Please upload at least one product image or video');
      return;
    }
    setSubmitting(true);
    try {
      const { id } = await createProduct({
        ...values,
        media,
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

        <Form.Item label="Media (first image = cover)" required>
          <Upload.Dragger
            multiple
            accept="image/jpeg,image/png,image/webp,image/gif,video/mp4,video/quicktime,video/webm"
            fileList={fileList}
            beforeUpload={(file) => {
              const contentType = guessContentType(file);
              const isVideo = contentType.startsWith('video/');
              const isImage = contentType.startsWith('image/');
              if (!isVideo && !isImage) {
                message.error('Only jpeg/png/webp/gif images or mp4/mov/webm videos');
                return Upload.LIST_IGNORE;
              }
              const max = isVideo ? VIDEO_MAX_BYTES : IMAGE_MAX_BYTES;
              if (file.size > max) {
                message.error(`${file.name} exceeds ${isVideo ? '80MB' : '8MB'} limit`);
                return Upload.LIST_IGNORE;
              }
              const uf: MediaUploadFile = {
                uid: file.uid,
                name: file.name,
                size: file.size,
                type: contentType,
                status: 'uploading',
                mediaType: isVideo ? 'video' : 'image',
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
            <p className="ant-upload-text">Click or drag images / videos here</p>
            <p className="ant-upload-hint">图片 jpeg/png/webp/gif · 视频 mp4/mov/webm，直传对象存储（MinIO）</p>
          </Upload.Dragger>
        </Form.Item>
      </Form>
    </Drawer>
  );
}
