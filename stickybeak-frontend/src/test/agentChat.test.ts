import { describe, expect, it } from 'vitest';
import { parseSse } from '../lib/agent';
import type { AgentEvent } from '../lib/agent';
import { chatReducer, initialChatState } from '../features/agent/chatState';
import type { ChatState } from '../features/agent/chatState';

describe('parseSse', () => {
  it('splits complete events and keeps the partial tail for the next chunk', () => {
    const first = parseSse('event: token\r\ndata: {"text":"你"}\r\n\r\nevent: tok');
    expect(first.events).toEqual([{ event: 'token', data: { text: '你' } }]);

    const second = parseSse(first.rest + 'en\r\ndata: {"text":"好"}\r\n\r\n');
    expect(second.events).toEqual([{ event: 'token', data: { text: '好' } }]);
    expect(second.rest).toBe('');
  });

  it('ignores keep-alive comments and malformed data', () => {
    const { events } = parseSse(': ping - 2026-10-24\n\nevent: token\ndata: not-json\n\nevent: done\ndata: {}\n\n');
    expect(events).toEqual([{ event: 'done', data: {} }]);
  });
});

function apply(state: ChatState, ...events: AgentEvent[]): ChatState {
  return events.reduce((s, event) => chatReducer(s, { type: 'event', event }), state);
}

describe('chatReducer', () => {
  it('builds an assistant answer from streamed tokens and sources', () => {
    let s = chatReducer(initialChatState, { type: 'userMessage', text: '退款多久到账？' });
    s = chatReducer(s, { type: 'startAssistant' });
    s = apply(
      s,
      { event: 'meta', data: { threadId: '7-abc' } },
      { event: 'token', data: { text: '5–10 个' } },
      { event: 'token', data: { text: '工作日 [1]' } },
      { event: 'sources', data: { items: [{ n: 1, title: '退换货与退款', section: '退款多久到账', source: 'zh/r.md' }] } },
      { event: 'done', data: {} },
    );
    s = chatReducer(s, { type: 'finished' });

    expect(s.threadId).toBe('7-abc');
    expect(s.streaming).toBe(false);
    expect(s.messages.map((m) => [m.role, m.text])).toEqual([
      ['user', '退款多久到账？'],
      ['assistant', '5–10 个工作日 [1]'],
    ]);
    expect(s.messages[1].sources?.[0].section).toBe('退款多久到账');
  });

  it('keeps a confirmation card and expires it when the user moves on', () => {
    let s = chatReducer(initialChatState, { type: 'userMessage', text: 'cancel SO1' });
    s = chatReducer(s, { type: 'startAssistant' });
    s = apply(s, { event: 'tool', data: { name: 'cancel_order' } });
    expect(s.activeTool).toBe('cancel_order');
    s = apply(s, { event: 'confirm', data: { type: 'confirm', actions: [{ id: 'c1', tool: 'cancel_order', orderNo: 'SO1' }] } });
    s = chatReducer(s, { type: 'finished' });
    expect(s.messages[1].confirm?.status).toBe('pending');

    s = chatReducer(s, { type: 'userMessage', text: 'never mind' });
    expect(s.messages[1].confirm?.status).toBe('stale');
  });

  it('marks the pending confirmation as approved or declined', () => {
    let s = chatReducer(initialChatState, { type: 'startAssistant' });
    s = apply(s, { event: 'confirm', data: { type: 'confirm', actions: [{ id: 'c1', tool: 'add_to_cart', productId: 1 }] } });
    expect(chatReducer(s, { type: 'confirmResolved', approved: true }).messages[0].confirm?.status).toBe('approved');
    expect(chatReducer(s, { type: 'confirmResolved', approved: false }).messages[0].confirm?.status).toBe('declined');
  });

  it('shows the error message when the model is unavailable', () => {
    let s = chatReducer(initialChatState, { type: 'startAssistant' });
    s = apply(s, { event: 'error', data: { message: '助手暂时不可用', code: 'model_unavailable' } });
    expect(s.messages[0]).toMatchObject({ text: '助手暂时不可用', error: true });
  });

  it('restores history including a pending confirmation after a page refresh', () => {
    const s = chatReducer(initialChatState, {
      type: 'restore',
      history: {
        threadId: '7-abc',
        messages: [
          { role: 'user', text: '取消 SO1' },
          { role: 'assistant', text: '好的', sources: [] },
        ],
        pendingConfirm: { type: 'confirm', actions: [{ id: 'c1', tool: 'cancel_order', orderNo: 'SO1' }] },
      },
    });
    expect(s.threadId).toBe('7-abc');
    expect(s.messages).toHaveLength(3);
    expect(s.messages[1].sources).toBeUndefined();
    expect(s.messages[2].confirm?.status).toBe('pending');
  });

  it('drops an empty assistant bubble when nothing was produced', () => {
    let s = chatReducer(initialChatState, { type: 'userMessage', text: 'hi' });
    s = chatReducer(s, { type: 'startAssistant' });
    s = chatReducer(s, { type: 'finished' });
    expect(s.messages.map((m) => m.role)).toEqual(['user']);
  });
});

describe('mcpClientConfig', () => {
  it('points MCP clients at the gateway endpoint with the bearer token', async () => {
    const { mcpClientConfig } = await import('../lib/mcp');
    const config = JSON.parse(mcpClientConfig('tok123', 'https://shop.example'));
    expect(config.mcpServers.stickybeak).toEqual({
      type: 'streamableHttp',
      url: 'https://shop.example/api/mcp',
      headers: { Authorization: 'Bearer tok123' },
    });
  });
});
