import { useQuery } from '@tanstack/react-query'
import { api } from '../api/client'

export function PropertiesPage() {
  const properties = useQuery({ queryKey: ['properties'], queryFn: () => api.properties.list() })

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-bold">Properties</h1>
        <button className="px-4 py-2 bg-emerald-500 hover:bg-emerald-600 rounded-lg text-sm font-medium transition-colors">
          + List Property
        </button>
      </div>
      {properties.isLoading && <p className="text-gray-500">Loading...</p>}
      {properties.data?.data?.length === 0 && (
        <p className="text-gray-500">No properties listed yet.</p>
      )}
    </div>
  )
}
