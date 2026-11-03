import { useCallback, useEffect, useState } from 'react';

export function useRetryCountdown() {
  const [remainingSeconds, setRemainingSeconds] = useState<number | null>(null);

  useEffect(() => {
    if (remainingSeconds === null || remainingSeconds <= 0) return;
    const timer = window.setTimeout(() => {
      setRemainingSeconds((remaining) => remaining === null ? null : Math.max(remaining - 1, 0));
    }, 1000);
    return () => window.clearTimeout(timer);
  }, [remainingSeconds]);

  const start = useCallback((seconds: number | undefined) => {
    setRemainingSeconds(seconds !== undefined && seconds > 0 ? Math.ceil(seconds) : null);
  }, []);

  const clear = useCallback(() => setRemainingSeconds(null), []);
  const isWaiting = remainingSeconds !== null && remainingSeconds > 0;

  return { remainingSeconds, isWaiting, start, clear };
}

export function retryCountdownMessage(seconds: number): string {
  return `Tente novamente em ${seconds} ${seconds === 1 ? 'segundo' : 'segundos'}.`;
}
