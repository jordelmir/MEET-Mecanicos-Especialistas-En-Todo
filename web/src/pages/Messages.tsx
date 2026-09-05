import { useQuery } from '@tanstack/react-query'
import { api } from '../api/client'

export function MessagesPage() {
  const messages = useQuery({ queryKey: ['messages'], queryFn: () => api.messages.list() })

  return (
    <div className="space-y-6">
      <h1 className="text-2xl font-bold">Messages</h1>
      {messages.isLoading && <p className="text-gray-500">Loading...</p>}
      {messages.data?.data?.length === 0 && (
        <p className="text-gray-500">No messages yet.</p>
      )}
    </div>
  )
}
