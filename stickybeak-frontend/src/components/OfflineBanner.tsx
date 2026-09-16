import { useEffect } from 'react';
import { Button } from 'antd';
import { DisconnectOutlined, CheckCircleOutlined, ReloadOutlined } from '@ant-design/icons';
import { useOnlineStatus } from '../hooks/useOnlineStatus';
import { useI18n } from '../lib/i18n';

export default function OfflineBanner() {
  const { isOnline, wasOffline, clearWasOffline } = useOnlineStatus();
  const { t } = useI18n();

  // Auto-dismiss reconnected banner after 3.5 seconds
  useEffect(() => {
    if (isOnline && wasOffline) {
      const timer = setTimeout(() => {
        clearWasOffline();
      }, 3500);
      return () => clearTimeout(timer);
    }
  }, [isOnline, wasOffline, clearWasOffline]);

  if (isOnline && !wasOffline) {
    return null;
  }

  // Reconnected online notification
  if (isOnline && wasOffline) {
    return (
      <aside
        role="status"
        aria-live="polite"
        className="relative z-50 flex items-center justify-between bg-emerald-600 px-4 py-2 text-xs font-medium text-white shadow transition-all sm:text-sm"
      >
        <div className="flex items-center gap-2">
          <CheckCircleOutlined className="text-base" />
          <span>{t('pwa.onlineRestored')}</span>
        </div>
        <button
          onClick={clearWasOffline}
          aria-label="Dismiss banner"
          className="ml-3 rounded px-2 py-0.5 text-xs text-white/80 hover:bg-emerald-700 hover:text-white"
        >
          ✕
        </button>
      </aside>
    );
  }

  // Currently offline notification
  return (
    <aside
      role="alert"
      aria-live="assertive"
      className="relative z-50 flex flex-wrap items-center justify-between gap-2 bg-amber-600 px-4 py-2 text-xs font-medium text-white shadow transition-all sm:text-sm"
    >
      <div className="flex items-center gap-2">
        <DisconnectOutlined className="text-base animate-pulse" />
        <div>
          <strong className="font-semibold">{t('pwa.offline')} — </strong>
          <span className="text-white/90">{t('pwa.offlineDesc')}</span>
        </div>
      </div>
      <Button
        size="small"
        icon={<ReloadOutlined />}
        onClick={() => window.location.reload()}
        className="border-white/40 bg-white/20 text-white hover:bg-white/30 hover:text-white"
      >
        {t('pwa.retry')}
      </Button>
    </aside>
  );
}
