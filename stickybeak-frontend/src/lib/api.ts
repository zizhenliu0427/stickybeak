import axios from 'axios';
import type { AxiosError, AxiosRequestConfig, AxiosResponse } from 'axios';

/** Unified backend response wrapper, mirrors stickybeak-common Result. */
export interface ApiResult<T> {
  code: number;
  message: string;
  data: T;
}

/** Request config extras understood by the interceptors. */
interface RequestFlags {
  _retried?: boolean;
  /** skip the 401->refresh->retry dance (used by the refresh call itself) */
  skipAuthRetry?: boolean;
}

/** Called when the refresh token is also dead — the app should clear local user state. */
let onSessionExpired: () => void = () => {};
export function setSessionExpiredHandler(fn: () => void) {
  onSessionExpired = fn;
}

/**
 * Unified API layer.
 * - baseURL /api (Vite dev proxy -> gateway :8080)
 * - cookies carry the JWT (HttpOnly), so requests always send credentials
 * - on 401: try refresh once, then retry the original request;
 *   if refresh fails, notify via onSessionExpired (route guards handle redirects)
 */
export const api = axios.create({
  baseURL: '/api',
  timeout: 15000,
  withCredentials: true,
});

let refreshing: Promise<void> | null = null;

/**
 * 服务端 5xx 时把网关返回的 X-Trace-Id 拼进错误信息：页面直接把错误弹给用户，
 * 用户报障时带上这个 ID 就能在 SkyWalking 里查到整条链路。
 */
export function attachTraceId<T extends AxiosError>(error: T): T {
  const status = error.response?.status;
  const traceId = error.response?.headers?.['x-trace-id'];
  if (status && status >= 500 && traceId && !error.message.includes(String(traceId))) {
    error.message = `${error.message} (Trace ID: ${traceId})`;
  }
  return error;
}

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
    const original = error.config as (typeof error.config & RequestFlags) | undefined;
    if (status === 401 && original && !original._retried && !original.skipAuthRetry) {
      original._retried = true;
      try {
        refreshing =
          refreshing ??
          api
            .post('/auth/refresh', undefined, { skipAuthRetry: true } as AxiosRequestConfig)
            .then(() => undefined);
        await refreshing;
        refreshing = null;
        return api(original);
      } catch {
        refreshing = null;
        onSessionExpired();
      }
    }
    return Promise.reject(attachTraceId(error));
  },
);

/** Pull the human-readable message out of any API failure. */
export function errorMessage(err: unknown, fallback = 'unknown error'): string {
  if (axios.isAxiosError(err)) {
    const data = err.response?.data as ApiResult<unknown> | undefined;
    const traceId = err.response?.headers?.['x-trace-id'];
    if (data?.message) {
      return (err.response?.status ?? 0) >= 500 && traceId ? `${data.message} (Trace ID: ${traceId})` : data.message;
    }
    return err.message;
  }
  return err instanceof Error && err.message ? err.message : fallback;
}

export async function get<T>(url: string, params?: Record<string, unknown>): Promise<T> {
  const resp = await api.get<ApiResult<T>>(url, { params });
  return resp.data.data;
}

export async function post<T>(url: string, body?: unknown): Promise<T> {
  const resp = await api.post<ApiResult<T>>(url, body);
  return resp.data.data;
}

export async function patch<T>(url: string, body?: unknown): Promise<T> {
  const resp = await api.patch<ApiResult<T>>(url, body);
  return resp.data.data;
}

export async function put<T>(url: string, body?: unknown): Promise<T> {
  const resp = await api.put<ApiResult<T>>(url, body);
  return resp.data.data;
}

export async function del<T>(url: string): Promise<T> {
  const resp = await api.delete<ApiResult<T>>(url);
  return resp.data.data;
}
