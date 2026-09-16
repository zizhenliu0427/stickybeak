import { useEffect, useState } from 'react';

export interface OnlineStatus {
  isOnline: boolean;
  wasOffline: boolean;
  clearWasOffline: () => void;
}

export function useOnlineStatus(): OnlineStatus {
  const [isOnline, setIsOnline] = useState<boolean>(() =>
    typeof navigator !== 'undefined' && typeof navigator.onLine === 'boolean'
      ? navigator.onLine
      : true
  );
  const [wasOffline, setWasOffline] = useState<boolean>(false);

  useEffect(() => {
    if (typeof window === 'undefined') return;

    const handleOnline = () => {
      setIsOnline(true);
      setWasOffline(true);
    };

    const handleOffline = () => {
      setIsOnline(false);
    };

    window.addEventListener('online', handleOnline);
    window.addEventListener('offline', handleOffline);

    return () => {
      window.removeEventListener('online', handleOnline);
      window.removeEventListener('offline', handleOffline);
    };
  }, []);

  const clearWasOffline = () => setWasOffline(false);

  return { isOnline, wasOffline, clearWasOffline };
}
