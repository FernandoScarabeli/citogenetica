import type { HTMLAttributes } from 'react';
import { X } from 'lucide-react';
import './alert.css';

export type AlertVariant = 'primary' | 'success' | 'destructive' | 'info' | 'warning';

// Adaptado à identidade do sistema, usando os controles e ícones existentes.
export function Alert({ variant = 'primary', close = false, onClose, className = '', children, ...props }:
  HTMLAttributes<HTMLDivElement> & { variant?: AlertVariant; close?: boolean; onClose?: () => void }) {
  return <div data-slot="alert" data-variant={variant} className={`feedback-alert ${className}`} role={variant === 'destructive' ? 'alert' : 'status'} {...props}>
    {children}
    {close && <button type="button" className="feedback-alert-close" aria-label="Dispensar aviso" onClick={onClose}><X size={16} aria-hidden="true" /></button>}
  </div>;
}

export function AlertIcon({ className = '', ...props }: HTMLAttributes<HTMLDivElement>) {
  return <div data-slot="alert-icon" className={`feedback-alert-icon ${className}`} {...props} />;
}
export function AlertTitle({ className = '', ...props }: HTMLAttributes<HTMLDivElement>) {
  return <div data-slot="alert-title" className={`feedback-alert-title ${className}`} {...props} />;
}
