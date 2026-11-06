import React, { createContext, useCallback, useContext, useRef, useState } from 'react';
import { AlertCircle, CheckCircle2, Info, X } from 'lucide-react';

const ToastContext = createContext(null);
const ICONS = { success: CheckCircle2, error: AlertCircle, info: Info };

let nextId = 0;

export function ToastProvider({ children }) {
  const [toasts, setToasts] = useState([]);
  const timers = useRef(new Map());

  const dismiss = useCallback((id) => {
    setToasts((list) => list.filter((t) => t.id !== id));
    const timer = timers.current.get(id);
    if (timer) {
      clearTimeout(timer);
      timers.current.delete(id);
    }
  }, []);

  const push = useCallback((message, type = 'success', title) => {
    if (!message) return;
    const id = ++nextId;
    setToasts((list) => [...list.slice(-3), { id, message, type, title }]);
    timers.current.set(id, setTimeout(() => dismiss(id), 5000));
  }, [dismiss]);

  const api = React.useMemo(() => ({
    success: (message, title) => push(message, 'success', title),
    error: (message, title) => push(message, 'error', title),
    info: (message, title) => push(message, 'info', title),
    dismiss,
  }), [push, dismiss]);

  return (
    <ToastContext.Provider value={api}>
      {children}
      <div className="toast-stack" aria-live="polite" aria-atomic="false">
        {toasts.map((t) => {
          const Icon = ICONS[t.type] || Info;
          return (
            <div key={t.id} className={`toast toast--${t.type}`} role="status">
              <Icon size={18} className="toast__icon" aria-hidden="true" />
              <div className="toast__content">
                {t.title && <div className="toast__title">{t.title}</div>}
                <div className="toast__message">{t.message}</div>
              </div>
              <button
                type="button"
                className="toast__close"
                aria-label="Dismiss notification"
                onClick={() => dismiss(t.id)}
              >
                <X size={15} />
              </button>
            </div>
          );
        })}
      </div>
    </ToastContext.Provider>
  );
}

export function useToast() {
  const ctx = useContext(ToastContext);
  if (!ctx) throw new Error('useToast must be used inside ToastProvider');
  return ctx;
}
