import React, { Component } from 'react';

type AppErrorBoundaryProps = {
  children: React.ReactNode;
};

type AppErrorBoundaryState = {
  error: Error | null;
};

export class AppErrorBoundary extends Component<AppErrorBoundaryProps, AppErrorBoundaryState> {
  state: AppErrorBoundaryState = { error: null };

  static getDerivedStateFromError(error: Error): AppErrorBoundaryState {
    return { error };
  }

  componentDidCatch(error: Error, info: React.ErrorInfo) {
    console.error('[MEET] Unhandled app error', error, info);
  }

  render() {
    if (!this.state.error) {
      return (this as any).props.children;
    }

    return (
      <main className="min-h-screen bg-slate-950 text-white flex items-center justify-center p-6">
        <section className="w-full max-w-xl rounded-lg border border-cyan-400/30 bg-slate-900/90 p-6 shadow-2xl shadow-cyan-500/10">
          <p className="text-xs font-semibold uppercase tracking-[0.24em] text-cyan-300">MEET Taller System</p>
          <h1 className="mt-3 text-2xl font-black text-white">La interfaz se protegió de un error</h1>
          <p className="mt-3 text-sm leading-6 text-slate-300">
            El sistema capturó una excepción antes de dejar la pantalla en blanco. Recarga la página y, si el problema
            continúa, revisa la consola con este detalle técnico.
          </p>
          <pre className="mt-5 max-h-56 overflow-auto rounded-md border border-slate-700 bg-black/40 p-4 text-xs text-rose-200">
            {this.state.error.message}
          </pre>
          <button
            className="mt-5 rounded-md bg-cyan-300 px-4 py-2 text-sm font-bold text-slate-950 transition hover:bg-cyan-200"
            onClick={() => window.location.reload()}
          >
            Recargar
          </button>
        </section>
      </main>
    );
  }
}
