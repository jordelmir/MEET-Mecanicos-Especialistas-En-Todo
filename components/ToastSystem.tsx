
import React, { createContext, useContext, useState, useCallback, useRef, useEffect } from 'react';
import { CheckCircle2, AlertTriangle, Info, X, Wrench, Trash2 } from 'lucide-react';

type ToastType = 'success' | 'error' | 'info' | 'warning';

interface Toast {
  id: string;
  type: ToastType;
  title: string;
  message?: string;
  duration?: number;
}

interface ToastContextType {
  toast: (type: ToastType, title: string, message?: string, duration?: number) => void;
}

const ToastContext = createContext<ToastContextType>({ toast: () => {} });

export function useToast() {
  return useContext(ToastContext);
}

export function ToastProvider({ children }: { children: React.ReactNode }) {
  const [toasts, setToasts] = useState<Toast[]>([]);
  const timeoutsRef = useRef<Map<string, number>>(new Map());

  const removeToast = useCallback((id: string) => {
    setToasts(prev => prev.filter(t => t.id !== id));
    const timeout = timeoutsRef.current.get(id);
    if (timeout) {
      clearTimeout(timeout);
      timeoutsRef.current.delete(id);
    }
  }, []);

  const toast = useCallback((type: ToastType, title: string, message?: string, duration = 3500) => {
    const id = `toast-${Date.now()}-${Math.random()}`;
    setToasts(prev => [...prev.slice(-4), { id, type, title, message, duration }]);

    const timeout = window.setTimeout(() => removeToast(id), duration);
    timeoutsRef.current.set(id, timeout);
  }, [removeToast]);

  const icons: Record<ToastType, React.ReactNode> = {
    success: <CheckCircle2 size={18} className="text-green-400" />,
    error: <AlertTriangle size={18} className="text-red-400" />,
    info: <Info size={18} className="text-blue-400" />,
    warning: <AlertTriangle size={18} className="text-yellow-400" />,
  };

  const borders: Record<ToastType, string> = {
    success: 'border-green-500/30',
    error: 'border-red-500/30',
    info: 'border-blue-500/30',
    warning: 'border-yellow-500/30',
  };

  return (
    <ToastContext.Provider value={{ toast }}>
      {children}
      {/* Toast Container */}
      <div className="fixed bottom-4 right-4 z-[9999] flex flex-col gap-2 pointer-events-none max-w-sm">
        {toasts.map((t, i) => (
          <div
            key={t.id}
            className={`pointer-events-auto glass rounded-xl px-4 py-3 flex items-start gap-3 border-l-4 ${borders[t.type]} shadow-2xl animate-slide-up`}
            style={{ animationDelay: `${i * 50}ms` }}
          >
            <div className="mt-0.5 flex-shrink-0">{icons[t.type]}</div>
            <div className="flex-1 min-w-0">
              <p className="text-sm font-bold text-white">{t.title}</p>
              {t.message && <p className="text-xs text-steel-200 mt-0.5">{t.message}</p>}
            </div>
            <button
              onClick={() => removeToast(t.id)}
              className="text-steel-400 hover:text-white transition-colors flex-shrink-0 mt-0.5"
            >
              <X size={14} />
            </button>
          </div>
        ))}
      </div>
    </ToastContext.Provider>
  );
}
