import { AxiosError, AxiosHeaders } from 'axios';
import { describe, expect, it } from 'vitest';
import { attachTraceId, errorMessage } from '../lib/api';

function httpError(status: number, traceId?: string, body?: unknown) {
  const headers = new AxiosHeaders();
  if (traceId) headers.set('x-trace-id', traceId);
  const config = { headers: new AxiosHeaders() };
  return new AxiosError(`Request failed with status code ${status}`, 'ERR_BAD_RESPONSE', config, null, {
    status,
    statusText: '',
    headers,
    config,
    data: body,
  });
}

describe('trace id on server errors', () => {
  it('appends the gateway trace id to 5xx errors', () => {
    const err = attachTraceId(httpError(500, 'abc.123'));
    expect(String(err)).toContain('Trace ID: abc.123');
  });

  it('leaves client errors and responses without a trace id alone', () => {
    expect(attachTraceId(httpError(404, 'abc.123')).message).toBe('Request failed with status code 404');
    expect(attachTraceId(httpError(502)).message).toBe('Request failed with status code 502');
  });

  it('does not append twice', () => {
    const err = attachTraceId(attachTraceId(httpError(503, 'abc.123')));
    expect(err.message.match(/Trace ID/g)).toHaveLength(1);
  });

  it('errorMessage keeps the backend message and adds the trace id for 5xx', () => {
    const err = httpError(500, 'abc.123', { code: 500, message: 'internal server error', data: null });
    expect(errorMessage(err)).toBe('internal server error (Trace ID: abc.123)');
  });
});
