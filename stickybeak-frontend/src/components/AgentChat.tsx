import { useEffect, useReducer, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Button, Drawer, FloatButton, Input, Tag } from 'antd';
import { CommentOutlined, PlusOutlined, SendOutlined } from '@ant-design/icons';
import { useAppDispatch, useAppSelector } from '../app/hooks';
import { fetchCartAsync } from '../features/cart/cartSlice';
import { chatReducer, initialChatState } from '../features/agent/chatState';
import type { ChatMessage } from '../features/agent/chatState';
import { AgentHttpError, fetchThread, resolveConfirm, sendMessage } from '../lib/agent';
import type { AgentAction, AgentEvent } from '../lib/agent';
import { useI18n } from '../lib/i18n';
import type { TranslationKey } from '../lib/i18n';

type Translate = (key: TranslationKey, params?: Record<string, string | number>) => string;

const threadKey = (userId: number | string) => `sb.agent.thread.${userId}`;

function readThread(key: string): string | null {
  try {
    return localStorage.getItem(key);
  } catch {
    return null;
  }
}

function writeThread(key: string, threadId: string | null) {
  try {
    if (threadId) localStorage.setItem(key, threadId);
    else localStorage.removeItem(key);
  } catch {
    // 隐私模式等禁用存储时，只是刷新后不能继续对话
  }
}

function useIsMobile() {
  const query = '(max-width: 640px)';
  const [mobile, setMobile] = useState(() => typeof window !== 'undefined' && !!window.matchMedia?.(query).matches);
  useEffect(() => {
    const mq = window.matchMedia?.(query);
    if (!mq) return;
    const handler = () => setMobile(mq.matches);
    mq.addEventListener('change', handler);
    return () => mq.removeEventListener('change', handler);
  }, []);
  return mobile;
}

function describeAction(action: AgentAction, t: Translate): string {
  if (action.tool === 'add_to_cart') {
    return t('agent.confirmAddToCart', {
      name: action.name ?? `#${action.productId ?? '?'}`,
      qty: action.qty ?? 1,
      price: action.priceAud ?? '-',
    });
  }
  if (action.tool === 'cancel_order') {
    return t('agent.confirmCancelOrder', {
      orderNo: action.orderNo ?? '?',
      amount: action.payAmount ?? '-',
      currency: action.currency ?? '',
    });
  }
  return action.tool;
}

function ConfirmCard({
  message,
  t,
  disabled,
  onResolve,
}: {
  message: ChatMessage;
  t: Translate;
  disabled: boolean;
  onResolve: (approved: boolean) => void;
}) {
  const confirm = message.confirm!;
  const statusLabel: Record<string, TranslationKey> = {
    approved: 'agent.confirmApproved',
    declined: 'agent.confirmDeclined',
    stale: 'agent.confirmStale',
  };
  return (
    <div className="mt-2 rounded-lg border border-amber-300 bg-amber-50 p-3 text-sm dark:border-amber-700 dark:bg-stone-800">
      <p className="mb-1 font-medium text-gray-800 dark:text-stone-100">{t('agent.confirmTitle')}</p>
      <ul className="mb-2 list-disc pl-5 text-gray-700 dark:text-stone-300">
        {confirm.actions.map((action) => (
          <li key={action.id}>
            {describeAction(action, t)}
            {action.notFound && <p className="text-xs text-red-500">{t('agent.confirmNotFound')}</p>}
          </li>
        ))}
      </ul>
      {confirm.status === 'pending' ? (
        <div className="flex gap-2">
          <Button type="primary" size="small" disabled={disabled} onClick={() => onResolve(true)}>
            {t('agent.confirm')}
          </Button>
          <Button size="small" disabled={disabled} onClick={() => onResolve(false)}>
            {t('agent.decline')}
          </Button>
        </div>
      ) : (
        <Tag color={confirm.status === 'approved' ? 'green' : 'default'}>{t(statusLabel[confirm.status])}</Tag>
      )}
    </div>
  );
}

/** 右下角的 AI 购物助手（Sprint 13）：流式回答、写操作确认卡片、售后回答的引用来源。 */
export default function AgentChat() {
  const user = useAppSelector((s) => s.auth.user);
  const dispatch = useAppDispatch();
  const navigate = useNavigate();
  const { t } = useI18n();
  const isMobile = useIsMobile();
  const [open, setOpen] = useState(false);
  const [input, setInput] = useState('');
  const [state, send] = useReducer(chatReducer, initialChatState);
  const abortRef = useRef<AbortController | null>(null);
  const restoredFor = useRef<string | null>(null);
  const listRef = useRef<HTMLDivElement>(null);
  const userId = user ? String(user.id) : null;

  // 换账号或退出登录：清空对话
  useEffect(() => {
    abortRef.current?.abort();
    send({ type: 'reset' });
    restoredFor.current = null;
  }, [userId]);

  // 第一次打开时恢复上次的对话（包括还没确认的操作）
  useEffect(() => {
    if (!open || !userId || restoredFor.current === userId) return;
    restoredFor.current = userId;
    const saved = readThread(threadKey(userId));
    if (!saved) return;
    fetchThread(saved)
      .then((history) => send({ type: 'restore', history }))
      .catch(() => writeThread(threadKey(userId), null));
  }, [open, userId]);

  useEffect(() => {
    if (userId && state.threadId) writeThread(threadKey(userId), state.threadId);
  }, [userId, state.threadId]);

  useEffect(() => {
    listRef.current?.scrollTo({ top: listRef.current.scrollHeight });
  }, [state.messages, state.activeTool]);

  useEffect(() => () => abortRef.current?.abort(), []);

  const errorText = (e: unknown) => {
    if (e instanceof AgentHttpError && e.status === 429) {
      return e.message.startsWith('daily_') ? t('agent.quotaExceeded') : t('agent.rateLimited');
    }
    return t('agent.failed');
  };

  const run = async (call: (onEvent: (e: AgentEvent) => void, signal: AbortSignal) => Promise<void>) => {
    const controller = new AbortController();
    abortRef.current = controller;
    send({ type: 'startAssistant' });
    try {
      await call((event) => send({ type: 'event', event }), controller.signal);
    } catch (e) {
      if (!controller.signal.aborted) send({ type: 'failed', text: errorText(e) });
    } finally {
      send({ type: 'finished' });
    }
  };

  const submit = async () => {
    const text = input.trim();
    if (!text || state.streaming) return;
    setInput('');
    send({ type: 'userMessage', text });
    await run((onEvent, signal) => sendMessage(text, state.threadId, onEvent, signal));
  };

  const resolve = async (message: ChatMessage, approved: boolean) => {
    if (!state.threadId || state.streaming) return;
    send({ type: 'confirmResolved', approved });
    await run((onEvent, signal) => resolveConfirm(state.threadId!, approved, onEvent, signal));
    if (approved && message.confirm?.actions.some((a) => a.tool === 'add_to_cart')) {
      dispatch(fetchCartAsync());
    }
  };

  const newChat = () => {
    abortRef.current?.abort();
    send({ type: 'reset' });
    if (userId) writeThread(threadKey(userId), null);
  };

  const last = state.messages[state.messages.length - 1];
  const waiting = state.streaming && last?.role === 'assistant' && !last.text && !last.confirm;

  return (
    <>
      <FloatButton
        icon={<CommentOutlined />}
        type="primary"
        tooltip={t('agent.open')}
        aria-label={t('agent.open')}
        className="!bottom-24 sm:!bottom-10"
        onClick={() => setOpen(true)}
      />
      <Drawer
        open={open}
        onClose={() => setOpen(false)}
        width={isMobile ? '100%' : 420}
        title={t('agent.title')}
        extra={
          user && (
            <Button size="small" icon={<PlusOutlined />} onClick={newChat} disabled={state.streaming}>
              {t('agent.newChat')}
            </Button>
          )
        }
        styles={{ body: { display: 'flex', flexDirection: 'column', padding: 0 } }}
      >
        {!user ? (
          <div className="flex flex-1 flex-col items-center justify-center gap-3 p-6 text-center">
            <p className="text-gray-600 dark:text-stone-300">{t('agent.signInPrompt')}</p>
            <Button type="primary" onClick={() => navigate('/login')}>
              {t('agent.signIn')}
            </Button>
          </div>
        ) : (
          <>
            <div ref={listRef} className="flex-1 space-y-3 overflow-y-auto p-4" aria-live="polite">
              {state.messages.length === 0 && (
                <p className="rounded-lg bg-sand-50 p-3 text-sm text-gray-600 dark:bg-stone-800 dark:text-stone-300">
                  {t('agent.welcome')}
                </p>
              )}
              {state.messages.map((m) => (
                <div key={m.id} className={m.role === 'user' ? 'flex justify-end' : 'flex justify-start'}>
                  <div
                    className={
                      m.role === 'user'
                        ? 'max-w-[85%] whitespace-pre-wrap rounded-2xl rounded-br-sm bg-brand-600 px-3 py-2 text-sm text-white'
                        : 'max-w-[90%] text-sm text-gray-800 dark:text-stone-100'
                    }
                  >
                    {m.text && (
                      <p
                        className={`whitespace-pre-wrap ${m.role === 'assistant' ? 'rounded-2xl rounded-bl-sm bg-sand-100 px-3 py-2 dark:bg-stone-800' : ''} ${m.error ? 'text-red-600 dark:text-red-400' : ''}`}
                      >
                        {m.text}
                      </p>
                    )}
                    {m.sources && m.sources.length > 0 && (
                      <div className="mt-1 text-xs text-gray-500 dark:text-stone-400">
                        <span className="font-medium">{t('agent.sources')}</span>
                        {m.sources.map((s) => (
                          <span key={s.n} className="mr-2">
                            [{s.n}] {s.title} · {s.section}
                          </span>
                        ))}
                      </div>
                    )}
                    {m.confirm && (
                      <ConfirmCard message={m} t={t} disabled={state.streaming} onResolve={(ok) => resolve(m, ok)} />
                    )}
                  </div>
                </div>
              ))}
              {waiting && (
                <p className="text-xs text-gray-400 dark:text-stone-500">
                  {state.activeTool
                    ? t(`agent.tool.${state.activeTool}` as TranslationKey)
                    : t('agent.thinking')}
                </p>
              )}
            </div>
            <div className="flex gap-2 border-t border-sand-200 p-3 dark:border-stone-700">
              <Input.TextArea
                value={input}
                onChange={(e) => setInput(e.target.value)}
                onPressEnter={(e) => {
                  if (!e.shiftKey) {
                    e.preventDefault();
                    submit();
                  }
                }}
                placeholder={t('agent.placeholder')}
                aria-label={t('agent.placeholder')}
                autoSize={{ minRows: 1, maxRows: 4 }}
                maxLength={1000}
              />
              <Button
                type="primary"
                icon={<SendOutlined />}
                onClick={submit}
                disabled={state.streaming || !input.trim()}
                aria-label={t('agent.send')}
              />
            </div>
          </>
        )}
      </Drawer>
    </>
  );
}
