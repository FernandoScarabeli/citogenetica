import { cleanup } from '@testing-library/react';
import { afterEach, vi } from 'vitest';

afterEach(() => {
  cleanup();
  document.cookie = 'XSRF-TOKEN=; Path=/; Max-Age=0';
  localStorage.clear();
  vi.unstubAllGlobals();
  vi.useRealTimers();
});
