
import React, { useState } from 'react';
import { Client } from '../types';
import { X, Plus, Edit2, Trash2, Save, User, Mail, Phone, AlertTriangle, Fingerprint, Key, Search, RefreshCw, Link } from 'lucide-react';
import { AvatarSelector } from './AvatarSelector';

interface ClientManagerProps {
  clients: Client[];
  onAdd: (client: Omit<Client, 'id' | 'bookingHistory' | 'points' | 'joinDate'>) => void;
  onUpdate: (client: Client) => void;
  onDelete: (id: string) => void;
  onClose: () => void;
}

export const ClientManager: React.FC<ClientManagerProps> = ({ clients, onAdd, onUpdate, onDelete, onClose }) => {
  const [editingId, setEditingId] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [searchTerm, setSearchTerm] = useState('');
  
  // New State for Image Preview Error Handling
  const [previewError, setPreviewError] = useState(false);

  // Form State
  const initialFormState = { 
      name: '', 
      phone: '', 
      email: '',
      identification: '',
      accessCode: '',
      notes: '',
      avatar: '' 
  };
  const [formData, setFormData] = useState(initialFormState);

  const generateAccessCode = () => {
      const code = Math.floor(100000 + Math.random() * 900000).toString();
      setFormData(prev => ({ ...prev, accessCode: code }));
  };

  const resetForm = () => {
      setFormData(initialFormState);
      setEditingId(null);
      setError(null);
      setPreviewError(false);
  };

  const handleEditClick = (client: Client) => {
      setEditingId(client.id);
      setFormData({ 
          name: client.name, 
          phone: client.phone,
          email: client.email,
          identification: client.identification,
          accessCode: client.accessCode,
          notes: client.notes || '',
          avatar: client.avatar || ''
      });
      setError(null);
      setPreviewError(false);
  };

  const handleSubmit = (e: React.FormEvent) => {
      e.preventDefault();
      if (!formData.name || !formData.identification) {
          setError("Nombre y Cédula son obligatorios para el acceso.");
          return;
      }
      
      if (!formData.accessCode) {
         setError("Debes generar un código de acceso.");
         return;
      }
      
      const avatarToUse = formData.avatar || `https://api.dicebear.com/9.x/avataaars/svg?seed=${formData.name}&backgroundColor=1a1a1a&radius=50`;

      const clientPayload = {
          ...formData,
          // Points are NOT handled here. They start at 0 (or stay as is) via the onAdd logic in App.tsx
          avatar: avatarToUse
      };

      if (editingId) {
            const existingClient = clients.find(c => c.id === editingId);
            if (existingClient) {
                onUpdate({ ...existingClient, ...clientPayload });
            }
      } else {
          onAdd(clientPayload);
      }
      resetForm();
  };

  const filteredClients = clients.filter(c => 
      c.name.toLowerCase().includes(searchTerm.toLowerCase()) ||
      c.identification.includes(searchTerm) ||
      c.email.toLowerCase().includes(searchTerm)
  );

  return (
    <div className="fixed inset-0 bg-black/80 backdrop-blur-sm z-50 flex items-center justify-center p-4 animate-in fade-in duration-200">
      <div className="bg-dark-800 rounded-xl border border-dark-600 shadow-2xl w-full max-w-2xl overflow-hidden flex flex-col max-h-[85vh]">
        
        {/* Header */}
        <div className="p-5 border-b border-dark-700 flex justify-between items-center bg-dark-900/50">
            <div className="flex items-center gap-3">
                <div className="bg-emerald-500/20 p-2 rounded-lg text-emerald-500">
                    <User size={20} />
                </div>
                <div>
                    <h2 className="text-xl font-bold text-white">Directorio de Clientes</h2>
                    <p className="text-xs text-gray-500">Crear perfiles y gestionar accesos</p>
                </div>
            </div>
            <button onClick={onClose} className="text-gray-400 hover:text-white transition-colors">
                <X size={24} />
            </button>
        </div>

        {/* Content */}
        <div className="flex-1 overflow-y-auto custom-scrollbar p-6">
            
            {/* Error Message */}
            {error && (
                <div className="bg-red-500/10 border border-red-500/50 p-3 rounded-lg flex items-center gap-3 mb-4">
                    <AlertTriangle className="text-red-500 shrink-0" size={18} />
                    <p className="text-sm text-red-200">{error}</p>
                </div>
            )}

            {/* Form */}
            <form onSubmit={handleSubmit} className="bg-dark-700/30 p-4 rounded-xl border border-dark-600 mb-6 transition-all focus-within:border-emerald-500/50">
                <h3 className="text-sm font-bold text-gray-300 uppercase mb-3 flex items-center gap-2">
                    {editingId ? <Edit2 size={14}/> : <Plus size={14}/>} 
                    {editingId ? 'Editar Cliente' : 'Registrar Nuevo Cliente'}
                </h3>
                
                <div className="flex flex-col md:flex-row gap-4 mb-4">
                    {/* Avatar Column */}
                    <div className="w-full md:w-1/3 shrink-0 flex flex-col items-center gap-2">
                        <div className="w-24 h-24 rounded-full bg-dark-600 border-2 border-dark-500 overflow-hidden relative group flex items-center justify-center mb-2">
                            {formData.avatar && !previewError ? (
                                <img 
                                    src={formData.avatar} 
                                    alt="Avatar" 
                                    className="w-full h-full object-cover" 
                                    onError={() => setPreviewError(true)}
                                />
                            ) : (
                                previewError ? <AlertTriangle className="text-red-500" size={24}/> : <User className="text-gray-500" size={32} />
                            )}
                        </div>

                        <AvatarSelector 
                            currentAvatar={formData.avatar}
                            name={formData.name || 'Client'}
                            onAvatarChange={(url) => {
                                setFormData({...formData, avatar: url});
                                setPreviewError(false);
                            }}
                        />
                    </div>

                    <div className="flex-1 space-y-3">
                        
                        {/* IDENTITY CONTAINER */}
                        <div className="bg-dark-900/50 border border-dark-600 rounded-lg p-3 space-y-3">
                             <label className="text-[10px] font-bold text-gray-400 uppercase tracking-wider block mb-1">Datos Personales y Contacto</label>
                             
                             <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
                                <div className="col-span-1 md:col-span-2">
                                    <input 
                                        type="text" 
                                        placeholder="Nombre Completo" 
                                        className="w-full bg-dark-800 border border-dark-600 rounded-lg px-3 py-2 text-white focus:border-emerald-500 focus:outline-none text-sm"
                                        value={formData.name}
                                        onChange={(e) => setFormData({...formData, name: e.target.value})}
                                        required
                                    />
                                </div>
                                <div className="relative">
                                    <Fingerprint size={14} className="absolute left-3 top-3 text-gray-500" />
                                    <input 
                                        type="text" 
                                        placeholder="Cédula / ID" 
                                        className="w-full bg-dark-800 border border-dark-600 rounded-lg pl-9 pr-3 py-2 text-white focus:border-emerald-500 focus:outline-none text-sm font-mono"
                                        value={formData.identification}
                                        onChange={(e) => setFormData({...formData, identification: e.target.value.replace(/\s/g, '')})}
                                        required
                                    />
                                </div>
                                <div className="relative">
                                    <Phone size={14} className="absolute left-3 top-3 text-emerald-500" />
                                    <input 
                                        type="text" 
                                        placeholder="Número de Teléfono" 
                                        className="w-full bg-dark-800 border border-dark-600 rounded-lg pl-9 pr-3 py-2 text-white focus:border-emerald-500 focus:outline-none text-sm font-bold"
                                        value={formData.phone}
                                        onChange={(e) => setFormData({...formData, phone: e.target.value})}
                                    />
                                </div>
                             </div>
                        </div>

                        {/* OTHER DETAILS */}
                        <div className="relative">
                            <Mail size={14} className="absolute left-3 top-3 text-gray-500" />
                            <input 
                                type="email" 
                                placeholder="Email (Opcional)" 
                                className="w-full bg-dark-800 border border-dark-600 rounded-lg pl-9 pr-3 py-2 text-white focus:border-emerald-500 focus:outline-none text-sm"
                                value={formData.email}
                                onChange={(e) => setFormData({...formData, email: e.target.value})}
                            />
                        </div>
                    </div>
                </div>

                {/* Credentials Display */}
                <div className="bg-dark-800/50 p-3 rounded-lg border border-emerald-500/30 flex items-center justify-between mb-4">
                    <div className="flex items-center gap-2">
                        <div className="bg-emerald-500/20 p-1.5 rounded text-emerald-400"><Key size={14}/></div>
                        <div>
                            <div className="text-[10px] text-gray-400 uppercase font-bold">Código de Acceso</div>
                            <div className="text-xs text-gray-500">Credencial única de 6 dígitos</div>
                        </div>
                    </div>
                     {formData.accessCode ? (
                        <div className="flex items-center gap-3">
                            <span className="font-mono text-xl font-bold text-emerald-400 tracking-widest bg-dark-900 px-3 py-1 rounded border border-dark-700">
                                {formData.accessCode}
                            </span>
                            <button 
                                type="button" 
                                onClick={generateAccessCode}
                                className="p-2 bg-dark-700 hover:bg-dark-600 rounded-lg text-gray-400 hover:text-white transition-colors"
                                title="Regenerar Código"
                            >
                                <RefreshCw size={14} />
                            </button>
                        </div>
                    ) : (
                        <button 
                            type="button" 
                            onClick={generateAccessCode}
                            className="bg-emerald-600/20 hover:bg-emerald-600/30 text-emerald-400 border border-emerald-500/50 px-3 py-1.5 rounded-lg text-xs font-bold transition-all flex items-center gap-2"
                        >
                            <Key size={12} /> Generar Credenciales
                        </button>
                    )}
                </div>

                <div className="flex justify-end gap-2 mt-3">
                    {editingId && (
                        <button type="button" onClick={resetForm} className="text-xs text-gray-400 hover:text-white px-3 py-2">
                            Cancelar
                        </button>
                    )}
                    <button type="submit" className="bg-emerald-600 text-white text-xs font-bold px-4 py-2 rounded-lg hover:bg-emerald-500 flex items-center gap-2 shadow-lg shadow-emerald-600/20">
                        <Save size={14} /> {editingId ? 'Actualizar Cliente' : 'Crear Perfil'}
                    </button>
                </div>
            </form>

            {/* List */}
            <div className="mb-4 relative">
                <Search className="absolute left-3 top-2.5 text-gray-500" size={14} />
                <input 
                    className="w-full bg-dark-800 border border-dark-700 rounded-lg pl-9 pr-3 py-2 text-sm text-white focus:border-emerald-500 focus:outline-none"
                    placeholder="Buscar cliente..."
                    value={searchTerm}
                    onChange={e => setSearchTerm(e.target.value)}
                />
            </div>

            <div className="space-y-2">
                {filteredClients.map(client => (
                    <div key={client.id} className="group flex items-center justify-between p-3 rounded-lg border border-dark-700 bg-dark-800/50 hover:border-dark-500 transition-all">
                        <div className="flex items-center gap-4">
                             <div className="w-10 h-10 rounded-full bg-dark-600 flex items-center justify-center overflow-hidden border border-dark-500">
                                {client.avatar ? (
                                    <img src={client.avatar} alt={client.name} className="w-full h-full object-cover" />
                                ) : (
                                    <User size={18} className="text-gray-400" />
                                )}
                            </div>
                            <div>
                                <div className="font-medium text-white text-sm">{client.name}</div>
                                <div className="text-xs text-gray-500 flex flex-col gap-0.5 mt-0.5 font-mono">
                                    <span className="flex items-center gap-1"><Fingerprint size={10} /> ID: {client.identification}</span>
                                    {client.phone && <span className="flex items-center gap-1 text-emerald-500/80"><Phone size={10} /> {client.phone}</span>}
                                </div>
                            </div>
                        </div>
                        <div className="flex items-center gap-2 opacity-0 group-hover:opacity-100 transition-opacity">
                            <button 
                                onClick={() => handleEditClick(client)}
                                className="p-2 text-gray-400 hover:text-emerald-500 hover:bg-dark-700 rounded-lg"
                                title="Editar"
                            >
                                <Edit2 size={16} />
                            </button>
                            <button 
                                onClick={() => onDelete(client.id)}
                                className="p-2 text-gray-400 hover:text-red-500 hover:bg-red-900/20 rounded-lg"
                                title="Eliminar"
                            >
                                <Trash2 size={16} />
                            </button>
                        </div>
                    </div>
                ))}
            </div>

        </div>
      </div>
    </div>
  );
};
