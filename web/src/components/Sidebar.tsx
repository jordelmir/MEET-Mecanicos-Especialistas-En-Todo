import { NavLink } from 'react-router-dom'
import { clsx } from 'clsx'

const nav = [
  { to: '/', label: 'Dashboard', icon: '📊' },
  { to: '/vehicles', label: 'Vehicles', icon: '🚗' },
  { to: '/rides', label: 'Rides', icon: '🛣️' },
  { to: '/properties', label: 'Properties', icon: '🏠' },
  { to: '/messages', label: 'Messages', icon: '💬' },
]

export function Sidebar() {
  return (
    <aside className="w-64 bg-gray-900 border-r border-gray-800 flex flex-col">
      <div className="p-6 border-b border-gray-800">
        <h1 className="text-xl font-bold tracking-tight">
          <span className="text-emerald-400">MEET</span>
        </h1>
        <p className="text-xs text-gray-500 mt-1">Mecánicos Especialistas En Todo</p>
      </div>
      <nav className="flex-1 p-4 space-y-1">
        {nav.map((item) => (
          <NavLink
            key={item.to}
            to={item.to}
            end={item.to === '/'}
            className={({ isActive }) =>
              clsx(
                'flex items-center gap-3 px-3 py-2 rounded-lg text-sm transition-colors',
                isActive
                  ? 'bg-emerald-500/10 text-emerald-400'
                  : 'text-gray-400 hover:bg-gray-800 hover:text-white'
              )
            }
          >
            <span>{item.icon}</span>
            <span>{item.label}</span>
          </NavLink>
        ))}
      </nav>
      <div className="p-4 border-t border-gray-800 text-xs text-gray-600">
        v1.0.0 · Web Dashboard
      </div>
    </aside>
  )
}
