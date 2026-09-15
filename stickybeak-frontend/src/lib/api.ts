import axios from 'axios';
import type { AxiosError, AxiosResponse } from 'axios';

/** Unified backend response wrapper, mirrors stickybeak-common Result. */
export interface ApiResult<T> {
  code: number;
  message: string;
  data: T;
}

/**
 * Unified API layer.
 * - baseURL /api (Vite dev proxy -> gateway :8080)
 * - cookies carry the JWT (HttpOnly), so requests always send credentials
 * - on 401: try refresh once, then retry the original request
 */
export const api = axios.create({
  baseURL: '/api',
  timeout: 15000,
  withCredentials: true,
});

let refreshing: Promise<void> | null = null;

api.interceptors.response.use(
  (resp: AxiosResponse<ApiResult<unknown>>) => {
    const body = resp.data;
    if (body && typeof body === 'object' && 'code' in body && body.code !== 0) {
      return Promise.reject(new Error(body.message || `business error ${body.code}`));
    }
    return resp;
  },
  async (error: AxiosError) => {
    const status = error.response?.status;
    const original = error.config;
    if (status === 401 && original && !(original as { _retried?: boolean })._retried) {
      (original as { _retried?: boolean })._retried = true;
      try {
        refreshing = refreshing ?? api.post('/auth/refresh').then(() => undefined);
        await refreshing;
        refreshing = null;
        return api(original);
      } catch {
        refreshing = null;
        window.location.href = '/login';
      }
    }
    return Promise.reject(error);
  },
);

export async function get<T>(url: string, params?: Record<string, unknown>): Promise<T> {
  const resp = await api.get<ApiResult<T>>(url, { params });
  return resp.data.data;
}

export async function post<T>(url: string, body?: unknown): Promise<T> {
  const resp = await api.post<ApiResult<T>>(url, body);
  return resp.data.data;
}
