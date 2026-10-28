import type { AgentConfirm, AgentEvent, AgentSource, ThreadHistory } from '../../lib/agent';

/** 聊天窗口的状态（纯 reducer，便于单测）。 */

export type ConfirmStatus = 'pending' | 'approved' | 'declined' | 'stale';

export interface ChatMessage {
  id: number;
  role: 'user' | 'assistant';
  text: string;
  sources?: AgentSource[];
  confirm?: AgentConfirm & { status: ConfirmStatus };
  error?: boolean;
}

export interface ChatState {
  threadId: string | null;
  messages: ChatMessage[];
  streaming: boolean;
  /** 当前正在调用的工具，用于显示"正在查询订单…" */
  activeTool: string | null;
  nextId: number;
}

export type ChatAction =
  | { type: 'reset' }
  | { type: 'restore'; history: ThreadHistory }
  | { type: 'userMessage'; text: string }
  | { type: 'startAssistant' }
  | { type: 'event'; event: AgentEvent }
  | { type: 'confirmResolved'; approved: boolean }
  | { type: 'failed'; text: string }
  | { type: 'finished' };

export const initialChatState: ChatState = {
  threadId: null,
  messages: [],
  streaming: false,
  activeTool: null,
  nextId: 1,
};

function updateLast(state: ChatState, patch: (m: ChatMessage) => ChatMessage): ChatState {
  const messages = [...state.messages];
  const index = messages.length - 1;
  if (index < 0 || messages[index].role !== 'assistant') return state;
  messages[index] = patch(messages[index]);
  return { ...state, messages };
}

/** 发新消息后，之前没处理的确认卡片作废（服务端也不会再执行它）。 */
function expirePendingConfirms(messages: ChatMessage[]): ChatMessage[] {
  return messages.map((m) =>
    m.confirm?.status === 'pending' ? { ...m, confirm: { ...m.confirm, status: 'stale' } } : m,
  );
}

export function chatReducer(state: ChatState, action: ChatAction): ChatState {
  switch (action.type) {
    case 'reset':
      return initialChatState;
    case 'restore': {
      let nextId = 1;
      const messages: ChatMessage[] = action.history.messages.map((m) => ({
        id: nextId++,
        role: m.role,
        text: m.text,
        sources: m.sources?.length ? m.sources : undefined,
      }));
      if (action.history.pendingConfirm) {
        messages.push({
          id: nextId++,
          role: 'assistant',
          text: '',
          confirm: { ...action.history.pendingConfirm, status: 'pending' },
        });
      }
      return { ...initialChatState, threadId: action.history.threadId, messages, nextId };
    }
    case 'userMessage':
      return {
        ...state,
        messages: [
          ...expirePendingConfirms(state.messages),
          { id: state.nextId, role: 'user', text: action.text },
        ],
        nextId: state.nextId + 1,
      };
    case 'startAssistant':
      return {
        ...state,
        streaming: true,
        activeTool: null,
        messages: [...state.messages, { id: state.nextId, role: 'assistant', text: '' }],
        nextId: state.nextId + 1,
      };
    case 'event': {
      const { event } = action;
      switch (event.event) {
        case 'meta':
          return { ...state, threadId: event.data.threadId };
        case 'token':
          return { ...updateLast(state, (m) => ({ ...m, text: m.text + event.data.text })), activeTool: null };
        case 'tool':
          return { ...state, activeTool: event.data.name };
        case 'sources':
          return updateLast(state, (m) => ({ ...m, sources: event.data.items }));
        case 'confirm':
          return {
            ...updateLast(state, (m) => ({ ...m, confirm: { ...event.data, status: 'pending' } })),
            activeTool: null,
          };
        case 'error':
          return updateLast(state, (m) => ({ ...m, text: m.text || event.data.message, error: !m.text }));
        case 'done':
          return state;
      }
      return state;
    }
    case 'confirmResolved': {
      const messages = state.messages.map((m) =>
        m.confirm?.status === 'pending'
          ? { ...m, confirm: { ...m.confirm, status: (action.approved ? 'approved' : 'declined') as ConfirmStatus } }
          : m,
      );
      return { ...state, messages };
    }
    case 'failed':
      return { ...updateLast(state, (m) => ({ ...m, text: m.text || action.text, error: true })), streaming: false };
    case 'finished':
      // 没有任何输出的空消息（例如只等待确认）保留，用来承载确认卡片；完全为空的去掉
      return {
        ...state,
        streaming: false,
        activeTool: null,
        messages: state.messages.filter((m) => m.role === 'user' || m.text || m.confirm),
      };
  }
}
