import { useEffect } from 'react';
import { Button, Card, Form, Input, Typography, message } from 'antd';
import { Link, useNavigate } from 'react-router-dom';
import { useAppDispatch, useAppSelector } from '../app/hooks';
import { clearError, register } from '../features/auth/authSlice';

interface RegisterForm {
  email: string;
  nickname?: string;
  password: string;
  confirm: string;
}

export default function RegisterPage() {
  const dispatch = useAppDispatch();
  const navigate = useNavigate();
  const { user, error } = useAppSelector((s) => s.auth);

  useEffect(() => {
    dispatch(clearError());
  }, [dispatch]);

  useEffect(() => {
    if (user) {
      navigate('/', { replace: true });
    }
  }, [user, navigate]);

  const onFinish = async (values: RegisterForm) => {
    const ok = await dispatch(
      register({ email: values.email, password: values.password, nickname: values.nickname }),
    )
      .unwrap()
      .then(() => true)
      .catch(() => false);
    if (ok) {
      message.success('Account created — welcome to StickyBeak!');
    }
  };

  return (
    <div className="flex justify-center py-10">
      <Card className="w-full max-w-sm shadow-sm">
        <Typography.Title level={3} className="!mt-0">
          Create account
        </Typography.Title>
        <Form layout="vertical" onFinish={onFinish} requiredMark={false}>
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
          <Form.Item name="nickname" label="Nickname (optional)">
            <Input placeholder="How should we call you?" maxLength={32} />
          </Form.Item>
          <Form.Item
            name="password"
            label="Password"
            rules={[
              { required: true, message: 'Password is required' },
              { min: 8, message: 'At least 8 characters' },
            ]}
          >
            <Input.Password placeholder="At least 8 characters" autoComplete="new-password" />
          </Form.Item>
          <Form.Item
            name="confirm"
            label="Confirm password"
            dependencies={['password']}
            rules={[
              { required: true, message: 'Please confirm your password' },
              ({ getFieldValue }) => ({
                validator: (_, value) =>
                  !value || getFieldValue('password') === value
                    ? Promise.resolve()
                    : Promise.reject(new Error('Passwords do not match')),
              }),
            ]}
          >
            <Input.Password placeholder="Repeat password" autoComplete="new-password" />
          </Form.Item>
          {error && <p className="text-red-500 text-sm -mt-2 mb-4">{error}</p>}
          <Button type="primary" htmlType="submit" block>
            Register
          </Button>
        </Form>
        <p className="mt-4 text-sm text-gray-500">
          Already have an account? <Link to="/login">Sign in</Link>
        </p>
      </Card>
    </div>
  );
}
