
import React, { useState } from 'react';
import { Wrench, Shield, Eye, EyeOff, UserPlus, LogIn } from 'lucide-react';

interface LoginPageProps {
  onLogin: (identity: string, code: string) => void;
  onRegister: (data: { name: string; email: string; phone: string; identification: string; accessCode: string }) => void;
  error: string | null;
}

export function LoginPage({ onLogin, onRegister, error }: LoginPageProps) {
  const [isRegistering, setIsRegistering] = useState(false);
  const [identity, setIdentity] = useState('');
  const [code, setCode] = useState('');
  const [showCode, setShowCode] = useState(false);
  const [isLoading, setIsLoading] = useState(false);
  
  // Registration specific fields
  const [regForm, setRegForm] = useState({ name: '', phone: '', email: '', identification: '', accessCode: '' });

  const handleLoginSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setIsLoading(true);
    await onLogin(identity.trim(), code.trim());
    setIsLoading(false);
  };

  const handleRegisterSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setIsLoading(true);
    await onRegister({
      name: regForm.name.trim(),
      email: regForm.email.trim(),
      phone: regForm.phone.trim(),
      identification: regForm.identification.trim(),
      accessCode: regForm.accessCode.trim()
    });
    setIsLoading(false);
  };

  return (
    <div className="min-h-screen flex items-center justify-center p-4 relative overflow-hidden">
      {/* Background Effects */}
      <div className="absolute inset-0 bg-gradient-to-br from-steel-950 via-steel-900 to-steel-950" />
      <div className="absolute top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 w-[800px] h-[800px] rounded-full bg-forge-500/5 blur-[120px]" />
      
      {/* Grid Pattern */}
      <div className="absolute inset-0" style={{
        backgroundImage: 'linear-gradient(rgba(0, 240, 255,0.03) 1px, transparent 1px), linear-gradient(90deg, rgba(0, 240, 255,0.03) 1px, transparent 1px)',
        backgroundSize: '60px 60px'
      }} />

      <div className="relative z-10 w-full max-w-md animate-slide-up">
        {/* Logo Section */}
        <div className="text-center mb-8">
          <div className="inline-flex items-center justify-center w-20 h-20 rounded-2xl bg-forge-500/10 border border-forge-500/20 mb-6 forge-glow">
            <Wrench size={36} className="text-forge-500" />
          </div>
          <h1 className="font-display text-5xl tracking-wider text-forge-500 drop-shadow-lg">
            MEET
          </h1>
          <p className="font-mono text-xs text-steel-200 tracking-[4px] uppercase mt-2">
            Mecánicos Especialistas En Todo
          </p>
        </div>

        {/* Card */}
        <div className="glass rounded-2xl p-8">
          <div className="flex items-center justify-between mb-6 pb-4 border-b border-white/5">
            <div className="flex items-center gap-3">
              {isRegistering ? <UserPlus size={18} className="text-forge-500" /> : <Shield size={18} className="text-forge-500" />}
              <span className="font-mono text-xs tracking-widest text-steel-200 uppercase">
                {isRegistering ? 'Nuevo Registro' : 'Acceso al Sistema'}
              </span>
            </div>
            <button 
              onClick={() => { setIsRegistering(!isRegistering); setIdentity(''); setCode(''); }}
              className="text-[10px] font-bold font-mono text-forge-500 hover:text-white uppercase tracking-wider transition-colors bg-forge-500/10 px-3 py-1 rounded-full border border-forge-500/30"
            >
              {isRegistering ? 'Iniciar Sesión' : 'Crear Cuenta'}
            </button>
          </div>

          {!isRegistering ? (
            /* ── LOGIN FORM ── */
            <form onSubmit={handleLoginSubmit} className="space-y-5">
              <div>
                <label className="block font-mono text-[10px] tracking-widest text-steel-300 uppercase mb-2">
                  Cédula o Email
                </label>
                <input
                  type="text"
                  value={identity}
                  onChange={e => setIdentity(e.target.value)}
                  placeholder="Ingrese su identificación..."
                  className="w-full bg-steel-800 border border-steel-500 rounded-lg px-4 py-3 font-mono text-sm text-white placeholder-steel-300 focus:border-forge-500 focus:ring-1 focus:ring-forge-500/30 transition-all outline-none"
                  required
                  autoFocus
                />
              </div>

              <div>
                <label className="block font-mono text-[10px] tracking-widest text-steel-300 uppercase mb-2">
                  Código de Acceso
                </label>
                <div className="relative">
                  <input
                    type={showCode ? 'text' : 'password'}
                    value={code}
                    onChange={e => setCode(e.target.value)}
                    placeholder="••••••"
                    maxLength={6}
                    className="w-full bg-steel-800 border border-steel-500 rounded-lg px-4 py-3 pr-12 font-mono text-sm text-white placeholder-steel-300 focus:border-forge-500 focus:ring-1 focus:ring-forge-500/30 transition-all outline-none tracking-[6px]"
                    required
                  />
                  <button
                    type="button"
                    onClick={() => setShowCode(!showCode)}
                    className="absolute right-3 top-1/2 -translate-y-1/2 text-steel-300 hover:text-forge-500 transition-colors"
                  >
                    {showCode ? <EyeOff size={16} /> : <Eye size={16} />}
                  </button>
                </div>
              </div>

              {error && (
                <div className="bg-red-500/10 border border-red-500/20 rounded-lg p-3 text-sm text-red-400 font-medium animate-slide-up">
                  {error}
                </div>
              )}

              <button
                type="submit"
                disabled={isLoading || !identity || !code}
                className="w-full bg-forge-500 hover:bg-forge-400 disabled:opacity-50 disabled:cursor-not-allowed text-black font-bold py-3 rounded-lg transition-all transform hover:scale-[1.02] active:scale-[0.98] font-mono text-sm tracking-wider uppercase forge-glow mt-2 flex items-center justify-center gap-2"
              >
                <LogIn size={16} />
                {isLoading ? 'Verificando...' : 'Ingresar al Sistema'}
              </button>
            </form>
          ) : (
            /* ── REGISTER FORM ── */
            <form onSubmit={handleRegisterSubmit} className="space-y-4 animate-slide-up">
              <div>
                <label className="block font-mono text-[10px] tracking-widest text-steel-300 uppercase mb-1">Nombre Completo</label>
                <input value={regForm.name} onChange={e => setRegForm({...regForm, name: e.target.value})} className="w-full bg-steel-800 border border-steel-500 rounded-lg px-3 py-2 text-sm text-white focus:border-forge-500 outline-none" required />
              </div>
              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="block font-mono text-[10px] tracking-widest text-steel-300 uppercase mb-1">Cédula</label>
                  <input value={regForm.identification} onChange={e => setRegForm({...regForm, identification: e.target.value})} className="w-full bg-steel-800 border border-steel-500 rounded-lg px-3 py-2 text-sm text-white focus:border-forge-500 outline-none font-mono" required />
                </div>
                <div>
                  <label className="block font-mono text-[10px] tracking-widest text-steel-300 uppercase mb-1">Teléfono</label>
                  <input type="tel" value={regForm.phone} onChange={e => setRegForm({...regForm, phone: e.target.value})} className="w-full bg-steel-800 border border-steel-500 rounded-lg px-3 py-2 text-sm text-white focus:border-forge-500 outline-none font-mono" required />
                </div>
              </div>
              <div>
                <label className="block font-mono text-[10px] tracking-widest text-steel-300 uppercase mb-1">Correo Electrónico</label>
                <input type="email" value={regForm.email} onChange={e => setRegForm({...regForm, email: e.target.value})} className="w-full bg-steel-800 border border-steel-500 rounded-lg px-3 py-2 text-sm text-white focus:border-forge-500 outline-none" required />
              </div>
              <div>
                <label className="block font-mono text-[10px] tracking-widest text-steel-300 uppercase mb-1">Crea un Código (6 Digitos)</label>
                <div className="relative">
                  <input type={showCode ? 'text' : 'password'} maxLength={6} value={regForm.accessCode} onChange={e => setRegForm({...regForm, accessCode: e.target.value})} className="w-full bg-steel-800 border border-steel-500 rounded-lg px-3 py-2 pr-10 text-sm text-white focus:border-forge-500 outline-none tracking-widest font-mono" required />
                  <button type="button" onClick={() => setShowCode(!showCode)} className="absolute right-3 top-1/2 -translate-y-1/2 text-steel-300 hover:text-forge-500 transition-colors">
                    {showCode ? <EyeOff size={14} /> : <Eye size={14} />}
                  </button>
                </div>
              </div>
              <button type="submit" disabled={isLoading || !regForm.name || !regForm.identification || regForm.accessCode.length < 4} className="w-full bg-forge-500 hover:bg-forge-400 disabled:opacity-50 text-black font-bold py-3 rounded-lg transition-all transform hover:scale-[1.02] font-mono text-sm tracking-wider uppercase forge-glow mt-4 flex items-center justify-center gap-2">
                <UserPlus size={16} /> Crear Cuenta
              </button>
            </form>
          )}

          {/* Quick Access Info (Only on Login Mode) */}
          {!isRegistering && (
            <div className="mt-6 pt-4 border-t border-white/5">
              <p className="font-mono text-[10px] text-steel-300 text-center uppercase tracking-wider mb-3">
                Accesos de Demostración
              </p>
              <div className="grid grid-cols-3 gap-2">
                {[
                  { label: 'Admin', id: '000000000', code: '000000', color: 'forge' },
                  { label: 'Mecánico', id: '101110111', code: '111111', color: 'blue' },
                  { label: 'Cliente', id: '111111111', code: '123456', color: 'green' },
                ].map(demo => (
                  <button
                    key={demo.label}
                    onClick={() => { setIdentity(demo.id); setCode(demo.code); }}
                    className="glass-inner glass-hover rounded-lg p-2 text-center transition-all cursor-pointer group"
                  >
                    <span className={`block font-mono text-[10px] font-bold ${
                      demo.color === 'forge' ? 'text-forge-500' :
                      demo.color === 'blue' ? 'text-blue-400' : 'text-green-400'
                    } group-hover:scale-105 transition-transform`}>
                      {demo.label}
                    </span>
                    <span className="block font-mono text-[8px] text-steel-300 mt-0.5">{demo.id.substring(0,5)}...</span>
                  </button>
                ))}
              </div>
            </div>
          )}
        </div>

        {/* Footer */}
        <p className="text-center font-mono text-[10px] text-steel-400 mt-6 tracking-wider">
          MEET v1.0 — MECÁNICOS ESPECIALISTAS EN TODO
        </p>
      </div>
    </div>
  );
}
