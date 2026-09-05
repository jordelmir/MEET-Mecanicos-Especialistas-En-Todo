import { useQuery } from '@tanstack/react-query'
import { api } from '../api/client'

export function RidesPage() {
  const rides = useQuery({ queryKey: ['rides'], queryFn: () => api.rides.list() })

  return (
    <div className="space-y-6">
      <h1 className="text-2xl font-bold">Rides</h1>
      {rides.isLoading && <p className="text-gray-500">Loading...</p>}
      {rides.data?.data?.length === 0 && (
        <p className="text-gray-500">No rides recorded yet.</p>
      )}
    </div>
  )
}
