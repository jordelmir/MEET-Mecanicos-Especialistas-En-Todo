
import React, { useState } from 'react';
import { Scissors, Lock, ArrowRight, AlertCircle, Eye, EyeOff, Shield, User, Terminal, Zap } from 'lucide-react';
import { MatrixRain } from './MatrixRain';

interface LoginPageProps {
  onLogin: (identity: string, code: string) => Promise<void>;
  error?: string | null;
}

export const LoginPage: React.FC<LoginPageProps> = ({ onLogin, error }) => {
  const [identity, setIdentity] = useState('');
  const [code, setCode] = useState('');
  const [isLoading, setIsLoading] = useState(false);
  const [showCode, setShowCode] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!identity || !code) return;
    
    setIsLoading(true);
    // Simulate network delay for realism
    setTimeout(async () => {
        await onLogin(identity.replace(/\s/g, ''), code.replace(/\s/g, ''));
        setIsLoading(false);
    }, 800);
  };

  const handleIdentityChange = (e: React.ChangeEvent<HTMLInputElement>) => {
      // Remove spaces immediately for ID
      setIdentity(e.target.value.trim());
  };

  const handleCodeChange = (e: React.ChangeEvent<HTMLInputElement>) => {
      // Allow only numbers and max 6 digits
      const val = e.target.value.replace(/\D/g, '').slice(0, 6);
      setCode(val);
  };

  // --- DEV TOOLS: QUICK LOGIN ---
  const handleDevLogin = (role: 'ADMIN' | 'BARBER' | 'CLIENT') => {
      setIsLoading(true);
      let id = '';
      let pass = '';

      // Hardcoded credentials matching constants.ts
      switch(role) {
          case 'ADMIN': 
              id = '000000000'; 
              pass = '000000'; 
              break;
          case 'BARBER': 
              id = '101110111'; // Alex "Navaja"
              pass = '111111'; 
              break;
          case 'CLIENT': 
              id = '111111111'; // Juan Pérez
              pass = '123456'; 
              break;
      }

      // Update UI for feedback
      setIdentity(id);
      setCode(pass);

      // Execute Login
      setTimeout(async () => {
          await onLogin(id, pass);
          setIsLoading(false);
      }, 500);
  };

  return (
    <div className="min-h-screen bg-[#050505] flex items-center justify-center p-4 relative overflow-hidden font-sans">
      
      {/* PROFESSIONAL BACKGROUND SYSTEM */}
      <MatrixRain theme="GOLD" opacity={0.35} speed={0.8} />

      <div className="relative z-10 w-full max-w-md">
        
        {/* Header */}
        <div className="text-center mb-10 animate-in fade-in slide-in-from-bottom-4 duration-700">
          <div className="inline-flex items-center gap-3 mb-6 glass-morphism px-4 py-2 rounded-full shadow-[0_0_30px_rgba(0,0,0,0.5)]">
             <Scissors size={20} className="text-brand-500" />
             <span className="text-xs font-mono text-brand-100 tracking-widest uppercase font-bold">Chronos Secure Access</span>
          </div>
          <h1 className="text-4xl font-black text-white tracking-tighter mb-2 drop-shadow-2xl">
            Bienvenido
          </h1>
          <p className="text-gray-400 text-sm font-medium">
            Ingresa tus credenciales para acceder al sistema.
          </p>
        </div>

        {/* Login Form Container - GLASS MORPHISM APPLIED */}
        <div className="glass-morphism rounded-2xl p-8 shadow-2xl animate-in zoom-in-95 duration-500 relative overflow-hidden">
            <form onSubmit={handleSubmit} className="space-y-6 relative z-10">
                
                {error && (
                    <div className="bg-red-500/10 border border-red-500/30 p-3 rounded-lg flex items-start gap-3 animate-in shake">
                        <AlertCircle className="text-red-500 shrink-0 mt-0.5" size={16} />
                        <p className="text-xs text-red-200">{error}</p>
                    </div>
                )}

                <div className="space-y-2">
                    <label className="text-xs font-bold text-gray-400 uppercase tracking-wide ml-1">Cédula o Email</label>
                    <div className="relative group">
                        <div className="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none">
                            <Lock size={16} className="text-gray-500 group-focus-within:text-brand-500 transition-colors" />
                        </div>
                        <input
                            type="text"
                            value={identity}
                            onChange={handleIdentityChange}
                            className="w-full bg-black/20 border border-dark-600 rounded-xl py-3 pl-10 pr-4 text-white placeholder-gray-600 focus:border-brand-500 focus:ring-1 focus:ring-brand-500 focus:outline-none transition-all font-mono"
                            placeholder="Ej. 101110222"
                            required
                        />
                    </div>
                </div>

                <div className="space-y-2">
                    <label className="text-xs font-bold text-gray-400 uppercase tracking-wide ml-1">Código de Acceso (6 Dígitos)</label>
                    <div className="relative group">
                        <input
                            type={showCode ? "text" : "password"}
                            value={code}
                            onChange={handleCodeChange}
                            className="w-full bg-black/20 border border-dark-600 rounded-xl py-3 pl-4 pr-10 text-white placeholder-gray-600 focus:border-brand-500 focus:ring-1 focus:ring-brand-500 focus:outline-none transition-all font-mono tracking-widest text-center text-lg"
                            placeholder="• • • • • •"
                            maxLength={6}
                            required
                        />
                        <button 
                            type="button"
                            onClick={() => setShowCode(!showCode)}
                            className="absolute inset-y-0 right-0 pr-3 flex items-center text-gray-500 hover:text-white transition-colors"
                        >
                            {showCode ? <EyeOff size={16} /> : <Eye size={16} />}
                        </button>
                    </div>
                </div>

                <button
                    type="submit"
                    disabled={isLoading || identity.length < 3 || code.length !== 6}
                    className="w-full bg-brand-500 text-black font-bold py-3.5 rounded-xl hover:bg-brand-400 shadow-[0_0_20px_rgba(240,180,41,0.3)] hover:shadow-[0_0_30px_rgba(240,180,41,0.5)] transition-all transform active:scale-95 disabled:opacity-50 disabled:cursor-not-allowed flex items-center justify-center gap-2 group"
                >
                    {isLoading ? (
                        <span className="w-5 h-5 border-2 border-black/30 border-t-black rounded-full animate-spin"></span>
                    ) : (
                        <>
                            Ingresar al Sistema <ArrowRight size={18} className="group-hover:translate-x-1 transition-transform" />
                        </>
                    )}
                </button>
            </form>
            
             {/* --- DEV TOOLS SECTION --- */}
            <div className="mt-8 pt-6 border-t border-white/10">
                <div className="flex items-center gap-2 mb-3 opacity-50">
                    <Terminal size={12} className="text-gray-400" />
                    <span className="text-[10px] font-mono uppercase tracking-widest text-gray-400">Modo Desarrollo: Acceso Rápido</span>
                </div>
                <div className="grid grid-cols-3 gap-2">
                    <button 
                        type="button"
                        onClick={() => handleDevLogin('ADMIN')}
                        className="flex flex-col items-center justify-center p-2 glass-morphism-inner hover:border-purple-500/50 hover:bg-purple-900/10 rounded-lg transition-all group"
                        disabled={isLoading}
                    >
                        <Shield size={16} className="text-gray-500 group-hover:text-purple-400 mb-1 transition-colors" />
                        <span className="text-[10px] text-gray-400 group-hover:text-purple-300 font-bold">ADMIN</span>
                    </button>
                    
                    <button 
                        type="button"
                        onClick={() => handleDevLogin('BARBER')}
                        className="flex flex-col items-center justify-center p-2 glass-morphism-inner hover:border-blue-500/50 hover:bg-blue-900/10 rounded-lg transition-all group"
                        disabled={isLoading}
                    >
                        <Scissors size={16} className="text-gray-500 group-hover:text-blue-400 mb-1 transition-colors" />
                        <span className="text-[10px] text-gray-400 group-hover:text-blue-300 font-bold">BARBERO</span>
                    </button>

                    <button 
                        type="button"
                        onClick={() => handleDevLogin('CLIENT')}
                        className="flex flex-col items-center justify-center p-2 glass-morphism-inner hover:border-emerald-500/50 hover:bg-emerald-900/10 rounded-lg transition-all group"
                        disabled={isLoading}
                    >
                        <User size={16} className="text-gray-500 group-hover:text-emerald-400 mb-1 transition-colors" />
                        <span className="text-[10px] text-gray-400 group-hover:text-emerald-300 font-bold">CLIENTE</span>
                    </button>
                </div>
            </div>

        </div>

        {/* Footer */}
        <div className="mt-8 text-center relative z-10">
            <p className="text-[10px] text-gray-500 font-mono uppercase tracking-widest bg-black/40 px-2 py-1 rounded inline-block backdrop-blur-sm">
                Protected by Chronos Security™
            </p>
        </div>
      </div>
    </div>
  );
};
