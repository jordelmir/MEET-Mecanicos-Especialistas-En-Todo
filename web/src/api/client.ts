const API_BASE = '/api'

export interface ApiResponse<T> {
  data: T
  ok: boolean
  error?: string
}

async function request<T>(path: string, options?: RequestInit): Promise<ApiResponse<T>> {
  const res = await fetch(`${API_BASE}${path}`, {
    headers: {
      'Content-Type': 'application/json',
      ...options?.headers,
    },
    ...options,
  })

  if (!res.ok) {
    const text = await res.text()
    return { data: null as T, ok: false, error: text }
  }

  const data = await res.json()
  return { data, ok: true }
}

export const api = {
  health: () => request<{ status: string }>('/health'),

  vehicles: {
    list: () => request<Vehicle[]>('/v1/vehicles'),
    get: (id: string) => request<Vehicle>(`/v1/vehicles/${id}`),
    create: (v: CreateVehicle) => request<Vehicle>('/v1/vehicles', {
      method: 'POST', body: JSON.stringify(v),
    }),
    update: (id: string, v: Partial<Vehicle>) => request<Vehicle>(`/v1/vehicles/${id}`, {
      method: 'PUT', body: JSON.stringify(v),
    }),
    delete: (id: string) => request<void>(`/v1/vehicles/${id}`, { method: 'DELETE' }),
  },

  rides: {
    list: () => request<Ride[]>('/v1/rides'),
  },

  me: () => request<UserProfile>('/v1/me'),

  properties: {
    list: () => request<PropertyListing[]>('/v1/properties'),
  },

  messages: {
    list: () => request<Message[]>('/v1/messages'),
  },
}

export interface Vehicle {
  vehicleId: string
  make: string
  model: string
  year: number
  vin?: string
  licensePlate?: string
  color?: string
  mileageKm?: number
  fuelType?: string
  engineDisplacementL?: number
  transmissionType?: string
  isActive: boolean
}

export interface CreateVehicle {
  make: string
  model: string
  year: number
  vin?: string
  licensePlate?: string
}

export interface Ride {
  rideId: string
  vehicleId: string
  status: string
  startedAtEpochMs: number
  completedAtEpochMs?: number
}

export interface UserProfile {
  userId: string
  email: string
  displayName?: string
}

export interface PropertyListing {
  listingId: string
  propertyId: string
  operation: string
  propertyTypeCode: string
  approximateZone: string
  state: string
}

export interface Message {
  messageId: string
  senderId: string
  content: string
  sentAtEpochMs: number
  state: string
}
