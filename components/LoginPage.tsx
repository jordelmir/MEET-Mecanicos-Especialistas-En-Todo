
import React, { useState, useMemo } from 'react';
import { Wrench, Shield, Eye, EyeOff, UserPlus, LogIn, Zap, Activity } from 'lucide-react';
import { useBrand } from '../lib/BrandModuleRegistry';

/* ─────────────────── KEYFRAME ANIMATIONS (injected once) ─────────────────── */
const STYLE_ID = '__elysium-login-keyframes';
if (typeof document !== 'undefined' && !document.getElementById(STYLE_ID)) {
  const style = document.createElement('style');
  style.id = STYLE_ID;
  style.textContent = `
    @keyframes elysium-float {
      0%, 100% { transform: translateY(0) rotate(0deg); opacity: 0.07; }
      50%      { transform: translateY(-40px) rotate(180deg); opacity: 0.15; }
    }
    @keyframes elysium-drift {
      0%   { transform: translate(0, 0) rotate(0deg); }
      25%  { transform: translate(30px, -20px) rotate(90deg); }
      50%  { transform: translate(-10px, -50px) rotate(180deg); }
      75%  { transform: translate(-30px, -15px) rotate(270deg); }
      100% { transform: translate(0, 0) rotate(360deg); }
    }
    @keyframes elysium-ring-spin {
      0%   { transform: rotate(0deg); }
      100% { transform: rotate(360deg); }
    }
    @keyframes elysium-ring-pulse {
      0%, 100% { box-shadow: 0 0 20px 4px rgba(0,240,255,0.3), inset 0 0 20px 4px rgba(0,240,255,0.1); }
      50%      { box-shadow: 0 0 40px 8px rgba(0,240,255,0.6), inset 0 0 40px 8px rgba(0,240,255,0.2); }
    }
    @keyframes elysium-glow-text {
      0%, 100% { text-shadow: 0 0 10px rgba(0,240,255,0.5), 0 0 30px rgba(0,240,255,0.3), 0 0 60px rgba(0,240,255,0.15); }
      50%      { text-shadow: 0 0 20px rgba(0,240,255,0.8), 0 0 50px rgba(0,240,255,0.5), 0 0 90px rgba(0,240,255,0.25); }
    }
    @keyframes elysium-scan-line {
      0%   { top: -2px; opacity: 1; }
      50%  { opacity: 0.6; }
      100% { top: 100%; opacity: 0; }
    }
    @keyframes elysium-gradient-shift {
      0%   { background-position: 0% 50%; }
      50%  { background-position: 100% 50%; }
      100% { background-position: 0% 50%; }
    }
    @keyframes elysium-pulse-loading {
      0%, 100% { opacity: 1; transform: scale(1); }
      50%      { opacity: 0.6; transform: scale(0.97); }
    }
    @keyframes elysium-fade-in-up {
      0%   { opacity: 0; transform: translateY(30px); }
      100% { opacity: 1; transform: translateY(0); }
    }
    @keyframes elysium-hexagon-pulse {
      0%, 100% { opacity: 0.04; transform: scale(1); }
      50%      { opacity: 0.12; transform: scale(1.05); }
    }
  `;
  document.head.appendChild(style);
}

/* ────────────────────── HEXAGON SVG PATH ────────────────────── */
const HEX_PATH = 'M50 0 L93.3 25 L93.3 75 L50 100 L6.7 75 L6.7 25 Z';

/* ────────────────────── PROPS ────────────────────── */
interface LoginPageProps {
  onLogin: (identity: string, code: string) => void;
  onRegister: (data: { name: string; email: string; phone: string; identification: string; accessCode: string }) => void;
  error: string | null;
}

/* ═══════════════════════════════════════════════════════════════
   ██  ELYSIUM VANGUARD — LOGIN PAGE  ██
   ═══════════════════════════════════════════════════════════════ */
export function LoginPage({ onLogin, onRegister, error }: LoginPageProps) {
  const { t } = useBrand();
  const [isRegistering, setIsRegistering] = useState(false);
  const [identity, setIdentity] = useState('');
  const [code, setCode] = useState('');
  const [showCode, setShowCode] = useState(false);
  const [isLoading, setIsLoading] = useState(false);

  // Registration fields
  const [regForm, setRegForm] = useState({
    name: '', phone: '', email: '', identification: '', accessCode: ''
  });

  /* ── Hexagon field (deterministic positions) ── */
  const hexagons = useMemo(() =>
    Array.from({ length: 18 }, (_, i) => ({
      id: i,
      size: 40 + (i * 17) % 80,
      left: (i * 23 + 7) % 100,
      top: (i * 31 + 13) % 100,
      duration: 12 + (i * 3) % 20,
      delay: (i * 1.7) % 8,
      drift: i % 3 === 0,
    })),
  []);

  /* ── Handlers ── */
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
      accessCode: regForm.accessCode.trim(),
    });
    setIsLoading(false);
  };

  const toggleMode = () => {
    setIsRegistering(!isRegistering);
    setIdentity('');
    setCode('');
  };

  /* ── Shared input style ── */
  const inputClass =
    'w-full rounded-xl px-4 py-3 font-mono text-sm text-white placeholder-white/25 outline-none transition-all duration-300 ' +
    'border border-white/10 ' +
    'focus:border-cyan-400/70 focus:ring-2 focus:ring-cyan-400/20 focus:shadow-[0_0_20px_rgba(0,240,255,0.15)]';

  const inputBg: React.CSSProperties = {
    background: 'rgba(0,10,20,0.65)',
    backdropFilter: 'blur(6px)',
  };

  const labelClass = 'block font-mono text-[10px] tracking-[3px] text-cyan-300/60 uppercase mb-2';

  /* ═══════════════════════════════ JSX ═══════════════════════════════ */
  return (
    <div className="min-h-screen flex items-center justify-center p-4 relative overflow-hidden select-none"
         style={{ background: 'linear-gradient(135deg, #020a14 0%, #0a1628 40%, #0f0a24 70%, #060e1a 100%)' }}>

      {/* ───────── ANIMATED GEOMETRIC BACKGROUND ───────── */}
      {/* Grid overlay */}
      <div className="absolute inset-0 pointer-events-none" style={{
        backgroundImage:
          'linear-gradient(rgba(0,240,255,0.025) 1px, transparent 1px),' +
          'linear-gradient(90deg, rgba(0,240,255,0.025) 1px, transparent 1px)',
        backgroundSize: '80px 80px',
      }} />

      {/* Radial glow centre */}
      <div className="absolute top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 w-[900px] h-[900px] rounded-full pointer-events-none"
           style={{ background: 'radial-gradient(circle, rgba(0,240,255,0.06) 0%, transparent 70%)' }} />
      {/* Purple secondary glow */}
      <div className="absolute bottom-0 right-0 w-[600px] h-[600px] rounded-full pointer-events-none"
           style={{ background: 'radial-gradient(circle, rgba(120,0,255,0.05) 0%, transparent 70%)' }} />

      {/* Floating Hexagons */}
      {hexagons.map(h => (
        <svg key={h.id}
             width={h.size} height={h.size}
             viewBox="0 0 100 100"
             className="absolute pointer-events-none"
             style={{
               left: `${h.left}%`,
               top: `${h.top}%`,
               animation: h.drift
                 ? `elysium-drift ${h.duration}s ease-in-out ${h.delay}s infinite`
                 : `elysium-float ${h.duration}s ease-in-out ${h.delay}s infinite`,
             }}>
          <path d={HEX_PATH}
                fill="none"
                stroke="rgba(0,240,255,0.12)"
                strokeWidth="1.5"
                style={{ animation: `elysium-hexagon-pulse ${h.duration * 0.8}s ease-in-out ${h.delay}s infinite` }} />
        </svg>
      ))}

      {/* ───────── CONTENT ───────── */}
      <div className="relative z-10 w-full max-w-md"
           style={{ animation: 'elysium-fade-in-up 0.8s ease-out both' }}>

        {/* ══════ LOGO AREA ══════ */}
        <div className="text-center mb-10">
          {/* Rotating neon ring + Wrench icon */}
          <div className="relative inline-flex items-center justify-center w-28 h-28 mb-6">
            {/* Outer rotating ring */}
            <div className="absolute inset-0 rounded-full"
                 style={{
                   border: '2px solid transparent',
                   borderTopColor: 'rgba(0,240,255,0.8)',
                   borderRightColor: 'rgba(120,0,255,0.5)',
                   animation: 'elysium-ring-spin 3s linear infinite, elysium-ring-pulse 2s ease-in-out infinite',
                 }} />
            {/* Inner static ring */}
            <div className="absolute inset-2 rounded-full"
                 style={{
                   border: '1px solid rgba(0,240,255,0.15)',
                   boxShadow: 'inset 0 0 30px rgba(0,240,255,0.08)',
                 }} />
            {/* Glow backdrop */}
            <div className="absolute inset-0 rounded-full pointer-events-none"
                 style={{ boxShadow: '0 0 60px 10px rgba(0,240,255,0.2), 0 0 120px 30px rgba(0,240,255,0.07)' }} />
            {/* Icon */}
            <Wrench size={42} className="relative z-10" style={{ color: '#00f0ff', filter: 'drop-shadow(0 0 12px rgba(0,240,255,0.7))' }} />
          </div>

          {/* Brand name */}
          <h1 className="font-display text-5xl md:text-6xl tracking-[6px] font-black uppercase"
              style={{
                color: '#00f0ff',
                animation: 'elysium-glow-text 3s ease-in-out infinite',
                letterSpacing: '0.15em',
              }}>
            {t('ELYSIUM VANGUARD')}
          </h1>

          {/* Subtitle */}
          <p className="font-mono text-[11px] tracking-[5px] uppercase mt-3"
             style={{ color: 'rgba(0,240,255,0.4)' }}>
            {t('Ecosistema Automotriz • Vanguard Network')}
          </p>

          {/* Decorative line */}
          <div className="mx-auto mt-4 h-px w-48"
               style={{ background: 'linear-gradient(90deg, transparent, rgba(0,240,255,0.4), transparent)' }} />
        </div>

        {/* ══════ GLASSMORPHIC CARD ══════ */}
        <div className="relative rounded-3xl p-8 overflow-hidden"
             style={{
               background: 'rgba(5,15,30,0.55)',
               backdropFilter: 'blur(30px) saturate(1.4)',
               WebkitBackdropFilter: 'blur(30px) saturate(1.4)',
               border: '1px solid rgba(0,240,255,0.12)',
               boxShadow:
                 '0 0 1px rgba(0,240,255,0.3),' +
                 '0 0 15px rgba(0,240,255,0.07),' +
                 '0 8px 32px rgba(0,0,0,0.5),' +
                 '0 20px 60px rgba(0,0,0,0.35),' +
                 'inset 0 1px 0 rgba(255,255,255,0.04)',
             }}>

          {/* ── Scanning line ── */}
          <div className="absolute left-0 w-full h-[2px] pointer-events-none z-20"
               style={{
                 background: 'linear-gradient(90deg, transparent 0%, rgba(0,240,255,0.5) 50%, transparent 100%)',
                 animation: 'elysium-scan-line 4s ease-in-out infinite',
               }} />

          {/* ── Card Header ── */}
          <div className="flex items-center justify-between mb-7 pb-4"
               style={{ borderBottom: '1px solid rgba(0,240,255,0.08)' }}>
            <div className="flex items-center gap-3">
              {isRegistering
                ? <UserPlus size={18} style={{ color: '#00f0ff', filter: 'drop-shadow(0 0 6px rgba(0,240,255,0.5))' }} />
                : <Shield size={18} style={{ color: '#00f0ff', filter: 'drop-shadow(0 0 6px rgba(0,240,255,0.5))' }} />
              }
              <span className="font-mono text-[10px] tracking-[3px] uppercase"
                    style={{ color: 'rgba(0,240,255,0.6)' }}>
                {isRegistering ? 'Nuevo Registro' : 'Acceso al Sistema'}
              </span>
            </div>
            <button onClick={toggleMode}
                    className="text-[10px] font-bold font-mono uppercase tracking-wider transition-all duration-300 px-3 py-1.5 rounded-full"
                    style={{
                      color: '#00f0ff',
                      background: 'rgba(0,240,255,0.06)',
                      border: '1px solid rgba(0,240,255,0.2)',
                      boxShadow: '0 0 8px rgba(0,240,255,0.08)',
                    }}>
              {isRegistering ? 'Iniciar Sesión' : 'Crear Cuenta'}
            </button>
          </div>

          {/* ══════════ LOGIN FORM ══════════ */}
          {!isRegistering ? (
            <form onSubmit={handleLoginSubmit} className="space-y-5"
                  style={isLoading ? { animation: 'elysium-pulse-loading 1.2s ease-in-out infinite' } : undefined}>

              {/* Identity */}
              <div>
                <label className={labelClass}>Cédula o Email</label>
                <input type="text" value={identity} onChange={e => setIdentity(e.target.value)}
                       placeholder="Ingrese su identificación..."
                       className={inputClass} style={inputBg}
                       required autoFocus />
              </div>

              {/* Code */}
              <div>
                <label className={labelClass}>Código de Acceso</label>
                <div className="relative">
                  <input type={showCode ? 'text' : 'password'}
                         value={code} onChange={e => setCode(e.target.value)}
                         placeholder="••••••" maxLength={6}
                         className={inputClass + ' pr-12 tracking-[6px]'}
                         style={inputBg} required />
                  <button type="button" onClick={() => setShowCode(!showCode)}
                          className="absolute right-4 top-1/2 -translate-y-1/2 transition-colors duration-200"
                          style={{ color: 'rgba(0,240,255,0.4)' }}>
                    {showCode ? <EyeOff size={16} /> : <Eye size={16} />}
                  </button>
                </div>
              </div>

              {/* Error */}
              {error && (
                <div className="rounded-xl p-3 text-sm font-medium"
                     style={{
                       background: 'rgba(255,50,50,0.08)',
                       border: '1px solid rgba(255,50,50,0.25)',
                       color: '#ff6b6b',
                       boxShadow: '0 0 20px rgba(255,50,50,0.1), inset 0 0 20px rgba(255,50,50,0.03)',
                       animation: 'elysium-fade-in-up 0.3s ease-out',
                     }}>
                  <div className="flex items-center gap-2">
                    <Activity size={14} style={{ color: '#ff6b6b' }} />
                    {error}
                  </div>
                </div>
              )}

              {/* Submit Button */}
              <button type="submit" disabled={isLoading || !identity || !code}
                      className="w-full font-bold py-3.5 rounded-xl font-mono text-sm tracking-[3px] uppercase flex items-center justify-center gap-2.5 transition-all duration-300 disabled:opacity-40 disabled:cursor-not-allowed disabled:transform-none hover:scale-[1.02] active:scale-[0.98] mt-3"
                      style={{
                        background: 'linear-gradient(135deg, #00f0ff 0%, #7b2fff 50%, #00f0ff 100%)',
                        backgroundSize: '200% 200%',
                        animation: 'elysium-gradient-shift 4s ease infinite',
                        color: '#000',
                        boxShadow: '0 0 20px rgba(0,240,255,0.3), 0 4px 15px rgba(0,0,0,0.3)',
                      }}>
                <LogIn size={16} />
                {isLoading ? 'Verificando...' : 'Ingresar al Sistema'}
              </button>
            </form>
          ) : (
            /* ══════════ REGISTER FORM ══════════ */
            <form onSubmit={handleRegisterSubmit} className="space-y-4"
                  style={{
                    animation: 'elysium-fade-in-up 0.4s ease-out',
                    ...(isLoading ? { animation: 'elysium-pulse-loading 1.2s ease-in-out infinite' } : {}),
                  }}>

              <div>
                <label className={labelClass}>Nombre Completo</label>
                <input value={regForm.name}
                       onChange={e => setRegForm({ ...regForm, name: e.target.value })}
                       className={inputClass} style={inputBg} required />
              </div>

              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className={labelClass}>Cédula</label>
                  <input value={regForm.identification}
                         onChange={e => setRegForm({ ...regForm, identification: e.target.value })}
                         className={inputClass + ' font-mono'} style={inputBg} required />
                </div>
                <div>
                  <label className={labelClass}>Teléfono</label>
                  <input type="tel" value={regForm.phone}
                         onChange={e => setRegForm({ ...regForm, phone: e.target.value })}
                         className={inputClass + ' font-mono'} style={inputBg} required />
                </div>
              </div>

              <div>
                <label className={labelClass}>Correo Electrónico</label>
                <input type="email" value={regForm.email}
                       onChange={e => setRegForm({ ...regForm, email: e.target.value })}
                       className={inputClass} style={inputBg} required />
              </div>

              <div>
                <label className={labelClass}>Crea un Código (6 Dígitos)</label>
                <div className="relative">
                  <input type={showCode ? 'text' : 'password'}
                         maxLength={6}
                         value={regForm.accessCode}
                         onChange={e => setRegForm({ ...regForm, accessCode: e.target.value })}
                         className={inputClass + ' pr-12 tracking-[6px] font-mono'}
                         style={inputBg} required />
                  <button type="button" onClick={() => setShowCode(!showCode)}
                          className="absolute right-4 top-1/2 -translate-y-1/2 transition-colors duration-200"
                          style={{ color: 'rgba(0,240,255,0.4)' }}>
                    {showCode ? <EyeOff size={14} /> : <Eye size={14} />}
                  </button>
                </div>
              </div>

              {/* Error in register mode */}
              {error && (
                <div className="rounded-xl p-3 text-sm font-medium"
                     style={{
                       background: 'rgba(255,50,50,0.08)',
                       border: '1px solid rgba(255,50,50,0.25)',
                       color: '#ff6b6b',
                       boxShadow: '0 0 20px rgba(255,50,50,0.1), inset 0 0 20px rgba(255,50,50,0.03)',
                       animation: 'elysium-fade-in-up 0.3s ease-out',
                     }}>
                  <div className="flex items-center gap-2">
                    <Activity size={14} style={{ color: '#ff6b6b' }} />
                    {error}
                  </div>
                </div>
              )}

              <button type="submit"
                      disabled={isLoading || !regForm.name || !regForm.identification || regForm.accessCode.length < 4}
                      className="w-full font-bold py-3.5 rounded-xl font-mono text-sm tracking-[3px] uppercase flex items-center justify-center gap-2.5 transition-all duration-300 disabled:opacity-40 disabled:cursor-not-allowed hover:scale-[1.02] active:scale-[0.98] mt-3"
                      style={{
                        background: 'linear-gradient(135deg, #00f0ff 0%, #7b2fff 50%, #00f0ff 100%)',
                        backgroundSize: '200% 200%',
                        animation: 'elysium-gradient-shift 4s ease infinite',
                        color: '#000',
                        boxShadow: '0 0 20px rgba(0,240,255,0.3), 0 4px 15px rgba(0,0,0,0.3)',
                      }}>
                <UserPlus size={16} />
                {isLoading ? 'Creando...' : 'Crear Cuenta'}
              </button>
            </form>
          )}

          {/* ══════════ DEMO QUICK ACCESS ══════════ */}
          {!isRegistering && (
            <div className="mt-7 pt-5" style={{ borderTop: '1px solid rgba(0,240,255,0.06)' }}>
              <p className="font-mono text-[10px] tracking-[4px] uppercase text-center mb-4"
                 style={{ color: 'rgba(0,240,255,0.35)' }}>
                <Zap size={10} className="inline -mt-0.5 mr-1" style={{ color: 'rgba(0,240,255,0.35)' }} />
                Accesos de Demostración
              </p>
              <div className="grid grid-cols-3 gap-3">
                {[
                  { label: 'Admin',    id: '000000000', code: '000000', glow: '#00f0ff', border: 'rgba(0,240,255,0.25)' },
                  { label: 'Mecánico', id: '101110111', code: '111111', glow: '#4d8dff', border: 'rgba(77,141,255,0.25)' },
                  { label: 'Cliente',  id: '111111111', code: '123456', glow: '#00ff88', border: 'rgba(0,255,136,0.25)' },
                ].map(demo => (
                  <button key={demo.label}
                          onClick={() => { setIdentity(demo.id); setCode(demo.code); }}
                          className="rounded-xl p-3 text-center transition-all duration-300 cursor-pointer group hover:scale-105"
                          style={{
                            background: 'rgba(0,10,20,0.4)',
                            border: `1px solid ${demo.border}`,
                            boxShadow: `0 0 12px ${demo.glow}15`,
                          }}>
                    <span className="block font-mono text-[11px] font-bold tracking-wider transition-all duration-200"
                          style={{ color: demo.glow, filter: `drop-shadow(0 0 4px ${demo.glow}60)` }}>
                      {demo.label}
                    </span>
                    <span className="block font-mono text-[8px] mt-1" style={{ color: 'rgba(255,255,255,0.2)' }}>
                      {demo.id.substring(0, 5)}•••
                    </span>
                  </button>
                ))}
              </div>
            </div>
          )}
        </div>

        {/* ══════════ FOOTER ══════════ */}
        <div className="text-center mt-8">
          <div className="mx-auto mb-3 h-px w-32"
               style={{ background: 'linear-gradient(90deg, transparent, rgba(0,240,255,0.15), transparent)' }} />
          <p className="font-mono text-[10px] tracking-[4px] uppercase"
             style={{ color: 'rgba(0,240,255,0.2)' }}>
            {t('Elysium Vanguard v4.0 — VANGUARD NETWORK')}
          </p>
        </div>
      </div>
    </div>
  );
}
