import { useQuery } from '@tanstack/react-query'
import { api } from '../api/client'

export function DashboardPage() {
  const health = useQuery({ queryKey: ['health'], queryFn: () => api.health() })
  const vehicles = useQuery({ queryKey: ['vehicles'], queryFn: () => api.vehicles.list() })
  const rides = useQuery({ queryKey: ['rides'], queryFn: () => api.rides.list() })
  const me = useQuery({ queryKey: ['me'], queryFn: () => api.me() })

  return (
    <div className="space-y-6">
      <h1 className="text-2xl font-bold">Dashboard</h1>

      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
        <StatCard
          title="Server"
          value={health.data?.ok ? 'Connected' : 'Offline'}
          color={health.data?.ok ? 'green' : 'red'}
        />
        <StatCard
          title="Vehicles"
          value={vehicles.data?.data?.length ?? 0}
          color="blue"
        />
        <StatCard
          title="Rides"
          value={rides.data?.data?.length ?? 0}
          color="purple"
        />
        <StatCard
          title="User"
          value={me.data?.data?.email ?? '—'}
          color="amber"
        />
      </div>

      <div className="bg-gray-900 rounded-xl p-6 border border-gray-800">
        <h2 className="text-lg font-semibold mb-4">Quick Actions</h2>
        <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
          <ActionButton label="Add Vehicle" href="/vehicles" />
          <ActionButton label="Start Ride" href="/rides" />
          <ActionButton label="View Properties" href="/properties" />
          <ActionButton label="Messages" href="/messages" />
        </div>
      </div>
    </div>
  )
}

function StatCard({ title, value, color }: { title: string; value: string | number; color: string }) {
  const colors: Record<string, string> = {
    green: 'text-emerald-400',
    red: 'text-red-400',
    blue: 'text-blue-400',
    purple: 'text-purple-400',
    amber: 'text-amber-400',
  }
  return (
    <div className="bg-gray-900 rounded-xl p-4 border border-gray-800">
      <p className="text-xs text-gray-500 uppercase tracking-wider">{title}</p>
      <p className={`text-2xl font-bold mt-1 ${colors[color] ?? 'text-white'}`}>{value}</p>
    </div>
  )
}

function ActionButton({ label, href }: { label: string; href: string }) {
  return (
    <a
      href={href}
      className="px-4 py-2 bg-gray-800 hover:bg-gray-700 rounded-lg text-sm text-center transition-colors"
    >
      {label}
    </a>
  )
}
