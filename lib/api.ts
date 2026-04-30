import { supabase } from './supabase';
import { WorkOrder, Client, Service, Mechanic, ShopConfig, ServiceHistoryItem, WorkOrderStatus } from '../types';

// ==========================================
// CLIENTS
// ==========================================

export async function getClients(): Promise<Client[]> {
  const { data, error } = await supabase.from('clients').select('*');
  if (error) throw error;
  
  return data.map((d: any) => ({
    id: d.id,
    name: d.name,
    phone: d.phone,
    email: d.email,
    identification: d.identification,
    accessCode: d.access_code,
    loyaltyPoints: d.loyalty_points,
    joinDate: new Date(d.join_date),
    lastVisit: d.last_visit ? new Date(d.last_visit) : undefined,
    notes: d.notes,
    avatar: d.avatar,
    vehicles: [], // We need to fetch vehicles separately or via join
    serviceHistory: [] // Fetched separately or via join
  }));
}

export async function createClient(client: Omit<Client, 'id' | 'joinDate' | 'loyaltyPoints' | 'serviceHistory' | 'vehicles'>): Promise<Client> {
  const { data, error } = await supabase.from('clients').insert([{
    name: client.name,
    phone: client.phone,
    email: client.email,
    identification: client.identification,
    access_code: client.accessCode,
    notes: client.notes,
    avatar: client.avatar
  }]).select().single();
  
  if (error) throw error;
  
  return {
    id: data.id,
    name: data.name,
    phone: data.phone,
    email: data.email,
    identification: data.identification,
    accessCode: data.access_code,
    loyaltyPoints: data.loyalty_points,
    joinDate: new Date(data.join_date),
    lastVisit: data.last_visit ? new Date(data.last_visit) : undefined,
    notes: data.notes,
    avatar: data.avatar,
    vehicles: [],
    serviceHistory: []
  };
}

export async function updateClient(id: string, updates: Partial<Client>): Promise<void> {
  const payload: any = {};
  if (updates.name) payload.name = updates.name;
  if (updates.phone) payload.phone = updates.phone;
  if (updates.email) payload.email = updates.email;
  if (updates.avatar) payload.avatar = updates.avatar;
  if (updates.notes) payload.notes = updates.notes;
  
  const { error } = await supabase.from('clients').update(payload).eq('id', id);
  if (error) throw error;
}

// ==========================================
// MECHANICS
// ==========================================

export async function getMechanics(): Promise<Mechanic[]> {
  const { data, error } = await supabase.from('mechanics').select('*');
  if (error) throw error;
  return data.map((d: any) => ({
    id: d.id,
    name: d.name,
    phone: d.phone,
    identification: d.identification,
    accessCode: d.access_code,
    email: d.email,
    specialty: d.specialty,
    efficiencyFactor: d.efficiency_factor,
    avatar: d.avatar,
    certifications: d.certifications
  }));
}

export async function createMechanic(mech: Omit<Mechanic, 'id'>): Promise<Mechanic> {
  const { data, error } = await supabase.from('mechanics').insert([{
    name: mech.name,
    phone: mech.phone,
    identification: mech.identification,
    access_code: mech.accessCode,
    email: mech.email,
    specialty: mech.specialty,
    efficiency_factor: mech.efficiencyFactor,
    avatar: mech.avatar,
    certifications: mech.certifications
  }]).select().single();
  
  if (error) throw error;
  
  return {
    id: data.id,
    name: data.name,
    phone: data.phone,
    identification: data.identification,
    accessCode: data.access_code,
    email: data.email,
    specialty: data.specialty,
    efficiencyFactor: data.efficiency_factor,
    avatar: data.avatar,
    certifications: data.certifications
  };
}

// ==========================================
// SERVICES
// ==========================================

export async function getServices(): Promise<Service[]> {
  const { data, error } = await supabase.from('services').select('*');
  if (error) throw error;
  return data.map((d: any) => ({
    id: d.id,
    name: d.name,
    category: d.category,
    estimatedMinutes: d.estimated_minutes,
    basePrice: d.base_price,
    description: d.description
  }));
}

export async function createService(service: Omit<Service, 'id'>): Promise<Service> {
  const { data, error } = await supabase.from('services').insert([{
    name: service.name,
    category: service.category,
    estimated_minutes: service.estimatedMinutes,
    base_price: service.basePrice,
    description: service.description
  }]).select().single();
  
  if (error) throw error;
  return {
    id: data.id,
    name: data.name,
    category: data.category,
    estimatedMinutes: data.estimated_minutes,
    basePrice: data.base_price,
    description: data.description
  };
}

// ==========================================
// WORK ORDERS
// ==========================================

export async function getWorkOrders(): Promise<WorkOrder[]> {
  const { data, error } = await supabase.from('work_orders').select('*');
  if (error) throw error;
  
  return data.map((d: any) => ({
    id: d.id,
    clientId: d.client_id,
    clientName: d.client_name,
    mechanicId: d.mechanic_id,
    serviceId: d.service_id,
    vehicleInfo: d.vehicle_info,
    startTime: new Date(d.start_time),
    estimatedEndTime: new Date(d.estimated_end_time),
    actualStartTime: d.actual_start_time ? new Date(d.actual_start_time) : undefined,
    actualEndTime: d.actual_end_time ? new Date(d.actual_end_time) : undefined,
    status: d.status as WorkOrderStatus,
    notes: d.notes,
    diagnosticNotes: d.diagnostic_notes,
    price: d.price,
    estimatedMinutes: d.estimated_minutes,
    cancellationReason: d.cancellation_reason,
    cancellationDate: d.cancellation_date ? new Date(d.cancellation_date) : undefined,
    partsNeeded: d.parts_needed,
    partsReady: d.parts_ready
  }));
}

export async function createWorkOrder(wo: Omit<WorkOrder, 'id'>): Promise<WorkOrder> {
  const { data, error } = await supabase.from('work_orders').insert([{
    client_id: wo.clientId,
    client_name: wo.clientName,
    mechanic_id: wo.mechanicId,
    service_id: wo.serviceId,
    vehicle_info: wo.vehicleInfo,
    start_time: wo.startTime.toISOString(),
    estimated_end_time: wo.estimatedEndTime.toISOString(),
    status: wo.status,
    price: wo.price,
    estimated_minutes: wo.estimatedMinutes
  }]).select().single();
  
  if (error) throw error;
  
  return {
    ...wo,
    id: data.id,
  };
}

export async function updateWorkOrder(id: string, updates: Partial<WorkOrder>): Promise<void> {
  const payload: any = {};
  if (updates.status) payload.status = updates.status;
  if (updates.actualStartTime) payload.actual_start_time = updates.actualStartTime.toISOString();
  if (updates.actualEndTime) payload.actual_end_time = updates.actualEndTime.toISOString();
  if (updates.notes) payload.notes = updates.notes;
  if (updates.diagnosticNotes) payload.diagnostic_notes = updates.diagnosticNotes;
  if (updates.price) payload.price = updates.price;
  if (updates.estimatedMinutes) payload.estimated_minutes = updates.estimatedMinutes;
  
  const { error } = await supabase.from('work_orders').update(payload).eq('id', id);
  if (error) throw error;
}

// ==========================================
// SHOP SETTINGS
// ==========================================

export async function getShopSettings(): Promise<ShopConfig | null> {
  const { data, error } = await supabase.from('shop_settings').select('*').limit(1).single();
  if (error && error.code !== 'PGRST116') throw error; // PGRST116 is no rows returned
  if (!data) return null;
  
  return {
    rules: data.rules,
    openHour: data.open_hour,
    closeHour: data.close_hour,
    timeSliceMinutes: data.time_slice_minutes
  };
}

export async function updateShopSettings(config: ShopConfig): Promise<void> {
  // Try to get first to see if we update or insert
  const { data } = await supabase.from('shop_settings').select('id').limit(1).single();
  
  if (data) {
    await supabase.from('shop_settings').update({
      rules: config.rules,
      open_hour: config.openHour,
      close_hour: config.closeHour,
      time_slice_minutes: config.timeSliceMinutes
    }).eq('id', data.id);
  } else {
    await supabase.from('shop_settings').insert([{
      rules: config.rules,
      open_hour: config.openHour,
      close_hour: config.closeHour,
      time_slice_minutes: config.timeSliceMinutes
    }]);
  }
}
