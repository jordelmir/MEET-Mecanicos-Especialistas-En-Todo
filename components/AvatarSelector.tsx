
import React, { useState } from 'react';
import { RefreshCw, User, Camera, Layers, Box, Type, Sparkles } from 'lucide-react';

interface AvatarSelectorProps {
    currentAvatar: string;
    name: string;
    onAvatarChange: (url: string) => void;
}

type AvatarStyle = 'initials' | 'avataaars' | 'shapes' | 'bottts' | 'lorelei';

export const AvatarSelector: React.FC<AvatarSelectorProps> = ({ currentAvatar, name, onAvatarChange }) => {
    const [aiStyle, setAiStyle] = useState<AvatarStyle>('avataaars');
    const [seed, setSeed] = useState(name.replace(/\s/g, ''));
    const [isGenerating, setIsGenerating] = useState(false);

    const handleGenerate = () => {
        setIsGenerating(true);
        // Randomize seed slightly to allow variations of the same name
        const newSeed = Math.random().toString(36).substring(7);
        setSeed(newSeed);
        
        // Construct the professional API URL
        const generatedUrl = `https://api.dicebear.com/9.x/${aiStyle}/svg?seed=${name}-${newSeed}&backgroundColor=1a1a1a&radius=50`;
        
        // Artificial delay for UX "Processing" feel
        setTimeout(() => {
            onAvatarChange(generatedUrl);
            setIsGenerating(false);
        }, 600);
    };

    const handleStyleChange = (style: AvatarStyle) => {
        setAiStyle(style);
        const generatedUrl = `https://api.dicebear.com/9.x/${style}/svg?seed=${seed}&backgroundColor=1a1a1a&radius=50`;
        onAvatarChange(generatedUrl);
    };

    return (
        <div className="bg-dark-800/50 border border-brand-500/20 rounded-xl p-4 space-y-4 animate-in fade-in duration-300 w-full">
            
            <div className="flex items-center gap-2 mb-2">
                <Sparkles size={14} className="text-brand-500" />
                <span className="text-xs font-bold text-gray-300 uppercase tracking-wider">Generador de Identidad IA</span>
            </div>

            {/* Style Selector Scrollable */}
            <div className="flex gap-2 overflow-x-auto custom-scrollbar pb-2 w-full">
                {[
                    { id: 'avataaars', label: 'Digital Twin', icon: User },
                    { id: 'lorelei', label: 'Artístico', icon: Camera },
                    { id: 'initials', label: 'Corporativo', icon: Type },
                    { id: 'shapes', label: 'Abstracto', icon: Box },
                    { id: 'bottts', label: 'Bot', icon: Layers },
                ].map((s) => (
                    <button
                        key={s.id}
                        onClick={() => handleStyleChange(s.id as AvatarStyle)}
                        className={`flex items-center gap-2 px-3 py-2 rounded-lg border text-[10px] md:text-xs font-bold transition-all whitespace-nowrap ${aiStyle === s.id ? 'bg-brand-500 text-black border-brand-500 shadow-md' : 'bg-dark-900 border-dark-600 text-gray-500 hover:border-gray-400 hover:text-white'}`}
                    >
                        <s.icon size={12} /> {s.label}
                    </button>
                ))}
            </div>

            {/* Action Button */}
            <button 
                onClick={handleGenerate}
                disabled={isGenerating}
                className="w-full bg-dark-900 hover:bg-black border border-dark-600 hover:border-brand-500/50 text-white py-3 rounded-lg flex items-center justify-center gap-2 transition-all active:scale-95 disabled:opacity-50 group"
            >
                <RefreshCw size={16} className={`text-gray-400 group-hover:text-brand-500 transition-colors ${isGenerating ? "animate-spin text-brand-500" : ""}`} />
                <span className="text-xs font-bold uppercase tracking-wider group-hover:text-brand-100">
                    {isGenerating ? 'Calculando Geometría...' : 'Generar Nueva Variación'}
                </span>
            </button>
            
            <p className="text-[9px] text-gray-600 text-center font-mono">
                Powered by DiceBear Neural Engine v9.0
            </p>
        </div>
    );
};
