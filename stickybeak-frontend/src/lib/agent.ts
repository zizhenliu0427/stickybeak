import { api } from './api';

/**
 * AI 助手接口（stickybeak-agent，经网关 /api/agent/**）。
 * 回答是 SSE 流：EventSource 只能发 GET，这里用 fetch + ReadableStream 读 POST 的流式响应。
 */

export interface AgentSource {
  n: number;
  title: string;
  section: string;
  source: string;
  score?: number | null;
}

export interface AgentAction {
  id: string;
  tool: 'add_to_cart' | 'cancel_order' | string;
  productId?: number;
  name?: string;
  priceAud?: string;
  qty?: number;
  image?: string | null;
  orderNo?: string;
  status?: string;
  payAmount?: string;
  currency?: string;
  notFound?: boolean;
}

export interface AgentConfirm {
  type: 'confirm';
  actions: AgentAction[];
}

export type AgentEvent =
  | { event: 'meta'; data: { threadId: string } }
  | { event: 'token'; data: { text: string } }
  | { event: 'tool'; data: { name: string } }
  | { event: 'sources'; data: { items: AgentSource[] } }
  | { event: 'confirm'; data: AgentConfirm }
  | { event: 'error'; data: { message: string; code: string } }
  | { event: 'done'; data: { firstTokenMs?: number | null; totalMs?: number } };

export interface ThreadHistory {
  threadId: string;
  messages: { role: 'user' | 'assistant'; text: string; sources?: AgentSource[] }[];
  pendingConfirm: AgentConfirm | null;
}

export class AgentHttpError extends Error {
  constructor(
    public status: number,
    message: string,
  ) {
    super(message);
  }
}

/**
 * 把收到的文本按 SSE 规则切成事件。返回解析出的事件和剩下的不完整部分（下次拼上新数据再解析）。
 * 只处理本服务用到的字段：event、data（单行 JSON）；注释行（": ping"）忽略。
 */
export function parseSse(buffer: string): { events: AgentEvent[]; rest: string } {
  const normalized = buffer.replace(/\r\n/g, '\n');
  const blocks = normalized.split('\n\n');
  const rest = blocks.pop() ?? '';
  const events: AgentEvent[] = [];
  for (const block of blocks) {
    let event = 'message';
    const data: string[] = [];
    for (const line of block.split('\n')) {
      if (line.startsWith('event:')) event = line.slice(6).trim();
      else if (line.startsWith('data:')) data.push(line.slice(5).trimStart());
    }
    if (data.length === 0) continue;
    try {
      events.push({ event, data: JSON.parse(data.join('\n')) } as AgentEvent);
    } catch {
      // 忽略无法解析的事件，不中断整段回答
    }
  }
  return { events, rest };
}

async function stream(path: string, body: unknown, onEvent: (e: AgentEvent) => void, signal?: AbortSignal) {
  const resp = await fetch(`/api${path}`, {
    method: 'POST',
    credentials: 'include',
    headers: { 'Content-Type': 'application/json', Accept: 'text/event-stream' },
    body: JSON.stringify(body),
    signal,
  });
  if (!resp.ok || !resp.body) {
    let message = resp.statusText;
    try {
      message = (await resp.json()).message ?? message;
    } catch {
      // 非 JSON 错误体
    }
    throw new AgentHttpError(resp.status, message);
  }
  const reader = resp.body.getReader();
  const decoder = new TextDecoder();
  let buffer = '';
  for (;;) {
    const { done, value } = await reader.read();
    if (done) break;
    buffer += decoder.decode(value, { stream: true });
    const parsed = parseSse(buffer);
    buffer = parsed.rest;
    parsed.events.forEach(onEvent);
  }
  parseSse(buffer + '\n\n').events.forEach(onEvent);
}

/** 401 时先刷新一次登录态（access token 15 分钟过期），再重试。 */
async function withRefresh(run: () => Promise<void>) {
  try {
    await run();
  } catch (e) {
    if (e instanceof AgentHttpError && e.status === 401) {
      await api.post('/auth/refresh');
      await run();
      return;
    }
    throw e;
  }
}

export function sendMessage(
  message: string,
  threadId: string | null,
  onEvent: (e: AgentEvent) => void,
  signal?: AbortSignal,
) {
  return withRefresh(() => stream('/agent/chat', { message, threadId }, onEvent, signal));
}

export function resolveConfirm(
  threadId: string,
  approved: boolean,
  onEvent: (e: AgentEvent) => void,
  signal?: AbortSignal,
) {
  return withRefresh(() => stream(`/agent/chat/${threadId}/resume`, { approved }, onEvent, signal));
}

export async function fetchThread(threadId: string): Promise<ThreadHistory> {
  const resp = await api.get<ThreadHistory>(`/agent/threads/${threadId}`);
  return resp.data;
}

export type MessageSegment =
  | { type: 'text'; text: string }
  | { type: 'bold'; text: string }
  | { type: 'product'; text: string; slug: string };

/**
 * 助手回答里只认两种标记：**加粗** 与指向站内商品页的 [名称](/products/slug)。
 * 其他链接（包括工具结果里被注入的外链）一律当普通文本显示，不渲染成可点击的链接。
 */
export function messageSegments(text: string): MessageSegment[] {
  const pattern = /\[([^\]\n]+)\]\(\/products\/([a-z0-9-]+)\)|\*\*([^*\n]+)\*\*/g;
  const segments: MessageSegment[] = [];
  let last = 0;
  for (const match of text.matchAll(pattern)) {
    const index = match.index ?? 0;
    if (index > last) segments.push({ type: 'text', text: text.slice(last, index) });
    if (match[2]) {
      segments.push({ type: 'product', text: match[1], slug: match[2] });
    } else {
      segments.push({ type: 'bold', text: match[3] });
    }
    last = index + match[0].length;
  }
  if (last < text.length) segments.push({ type: 'text', text: text.slice(last) });
  return segments;
}
