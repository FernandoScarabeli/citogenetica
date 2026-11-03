import { act, fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { FloatingNotice } from '../src/components/ui/floating-notice';

describe('Aviso flutuante', () => {
  it('fecha após cinco segundos sem reiniciar por uma renderização do pai', () => {
    vi.useFakeTimers();
    const close = vi.fn();
    const { rerender } = render(<FloatingNotice message="Nome atualizado." onClose={close} />);
    act(() => vi.advanceTimersByTime(3000));
    rerender(<FloatingNotice message="Nome atualizado." onClose={() => close()} />);
    act(() => vi.advanceTimersByTime(2000));
    expect(close).toHaveBeenCalledOnce();
  });

  it('dá tempo para ler com o mouse sobre o aviso e com foco no botão', () => {
    vi.useFakeTimers();
    const close = vi.fn();
    render(<FloatingNotice message="Laboratório criado." onClose={close} />);
    const notice = screen.getByRole('status').parentElement!;
    fireEvent.mouseEnter(notice);
    act(() => vi.advanceTimersByTime(10000));
    expect(close).not.toHaveBeenCalled();
    fireEvent.focus(screen.getByRole('button', { name: 'Dispensar aviso' }));
    fireEvent.mouseLeave(notice);
    act(() => vi.advanceTimersByTime(10000));
    expect(close).not.toHaveBeenCalled();
    fireEvent.blur(screen.getByRole('button', { name: 'Dispensar aviso' }));
    act(() => vi.advanceTimersByTime(5000));
    expect(close).toHaveBeenCalledOnce();
  });

  it('permite dispensar imediatamente e limpa o temporizador ao desmontar', () => {
    vi.useFakeTimers();
    const close = vi.fn();
    const { unmount } = render(<FloatingNotice message="Papéis atualizados." onClose={close} />);
    fireEvent.click(screen.getByRole('button', { name: 'Dispensar aviso' }));
    expect(close).toHaveBeenCalledOnce();
    unmount();
    act(() => vi.advanceTimersByTime(10000));
    expect(close).toHaveBeenCalledOnce();
  });

  it('mantém o conteúdo durante a saída e cancela a remoção se chegar outro aviso', () => {
    vi.useFakeTimers();
    const close = vi.fn();
    const { rerender, container } = render(<FloatingNotice message="Nome atualizado." onClose={close} />);
    rerender(<FloatingNotice message="" onClose={close} />);
    expect(container.querySelector('.floating-notice')?.getAttribute('data-state')).toBe('closed');
    expect(screen.queryByRole('status')).toBeNull();
    expect(container.textContent).toContain('Nome atualizado.');
    act(() => vi.advanceTimersByTime(90));
    rerender(<FloatingNotice message="Convite enviado." onClose={close} />);
    act(() => vi.advanceTimersByTime(90));
    expect(screen.getByRole('status').textContent).toContain('Convite enviado.');
    rerender(<FloatingNotice message="" onClose={close} />);
    act(() => vi.advanceTimersByTime(180));
    expect(container.querySelector('.floating-notice')).toBeNull();
  });

});
