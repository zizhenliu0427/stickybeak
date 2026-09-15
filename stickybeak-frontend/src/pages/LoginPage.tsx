import { useEffect } from 'react';
import { Button, Card, Form, Input, Typography, message } from 'antd';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { useAppDispatch, useAppSelector } from '../app/hooks';
import { clearError, login } from '../features/auth/authSlice';

interface LoginForm {
  email: string;
  password: string;
}

export default function LoginPage() {
  const dispatch = useAppDispatch();
  const navigate = useNavigate();
  const location = useLocation();
  const { user, error } = useAppSelector((s) => s.auth);
  const [form] = Form.useForm<LoginForm>();

  // 目标页：守卫带来的 from，否则首页
  const from = (location.state as { from?: string } | null)?.from ?? '/';

  useEffect(() => {
    dispatch(clearError());
  }, [dispatch]);

  useEffect(() => {
    if (user) {
      navigate(from, { replace: true });
    }
  }, [user, from, navigate]);

  const onFinish = async (values: LoginForm) => {
    const ok = await dispatch(login(values)).unwrap().then(() => true).catch(() => false);
    if (ok) {
      message.success('Welcome back!');
    }
  };

  return (
    <div className="flex justify-center py-10">
      <Card className="w-full max-w-sm shadow-sm">
        <Typography.Title level={3} className="!mt-0">
          Sign in
        </Typography.Title>
        <Form form={form} layout="vertical" onFinish={onFinish} requiredMark={false}>
          <Form.Item
            name="email"
            label="Email"
            rules={[
              { required: true, message: 'Email is required' },
              { type: 'email', message: 'Invalid email' },
            ]}
          >
            <Input placeholder="you@example.com" autoComplete="email" />
          </Form.Item>
          <Form.Item
            name="password"
            label="Password"
            rules={[{ required: true, message: 'Password is required' }]}
          >
            <Input.Password placeholder="••••••••" autoComplete="current-password" />
          </Form.Item>
          {error && <p className="text-red-500 text-sm -mt-2 mb-4">{error}</p>}
          <Button type="primary" htmlType="submit" block>
            Sign in
          </Button>
        </Form>
        <p className="mt-4 text-sm text-gray-500">
          No account yet? <Link to="/register">Register</Link>
        </p>
      </Card>
    </div>
  );
}
