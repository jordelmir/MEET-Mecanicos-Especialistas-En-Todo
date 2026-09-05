import { useQuery } from '@tanstack/react-query'
import { api } from '../api/client'

export function VehiclesPage() {
  const vehicles = useQuery({ queryKey: ['vehicles'], queryFn: () => api.vehicles.list() })

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-bold">Vehicles</h1>
        <button className="px-4 py-2 bg-emerald-500 hover:bg-emerald-600 rounded-lg text-sm font-medium transition-colors">
          + Add Vehicle
        </button>
      </div>

      {vehicles.isLoading && <p className="text-gray-500">Loading...</p>}

      {vehicles.data?.data && (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
          {vehicles.data.data.map((v) => (
            <div key={v.vehicleId} className="bg-gray-900 rounded-xl p-4 border border-gray-800">
              <div className="flex items-center gap-3">
                <span className="text-2xl">🚗</span>
                <div>
                  <p className="font-semibold">{v.year} {v.make} {v.model}</p>
                  <p className="text-xs text-gray-500">{v.licensePlate || '—'}</p>
                </div>
              </div>
              {v.mileageKm != null && (
                <p className="mt-3 text-sm text-gray-400">{v.mileageKm.toLocaleString()} km</p>
              )}
            </div>
          ))}
        </div>
      )}
    </div>
  )
}
