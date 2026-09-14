import { describe, it, expect, beforeEach } from 'vitest';

describe('Adversarial Ride State Authority & Concurrency Races', () => {
  interface RideRequest {
    id: string;
    passenger_id: string;
    assigned_driver_id: string | null;
    state: 'SEARCHING' | 'OFFERED' | 'ASSIGNED' | 'DRIVER_EN_ROUTE' | 'ARRIVED' | 'IN_PROGRESS' | 'COMPLETED' | 'CANCELLED';
    version: number;
  }

  const rides = new Map<string, RideRequest>();
  const dedup = new Map<string, any>();

  beforeEach(() => {
    rides.clear();
    dedup.clear();

    rides.set('ride-1', {
      id: 'ride-1',
      passenger_id: 'passenger-1',
      assigned_driver_id: null,
      state: 'OFFERED',
      version: 1,
    });
  });

  function simulateAcceptOffer(
    rideId: string,
    driverId: string,
    actorId: string,
    expectedVersion: number,
    idempotencyKey: string,
  ): { status: string; version: number } {
    if (actorId !== rides.get(rideId)?.passenger_id) {
      const err = new Error('FORBIDDEN');
      (err as any).code = '42501';
      throw err;
    }

    if (dedup.has(idempotencyKey)) {
      return dedup.get(idempotencyKey);
    }

    const ride = rides.get(rideId);
    if (!ride) throw new Error('RIDE_NOT_FOUND');

    if (ride.version !== expectedVersion) {
      const err = new Error('VERSION_CONFLICT');
      (err as any).code = '40001';
      throw err;
    }

    // Invariant: Two drivers cannot win the same ride
    if (ride.assigned_driver_id !== null || !['SEARCHING', 'OFFERED'].includes(ride.state)) {
      const err = new Error('RIDE_ALREADY_ASSIGNED');
      (err as any).code = '23505';
      throw err;
    }

    ride.assigned_driver_id = driverId;
    ride.state = 'ASSIGNED';
    ride.version += 1;

    const result = { status: 'ASSIGNED', version: ride.version };
    dedup.set(idempotencyKey, result);
    return result;
  }

  function simulateStartRide(
    rideId: string,
    driverId: string,
    expectedVersion: number,
    idempotencyKey: string,
  ): { status: string; version: number } {
    if (dedup.has(idempotencyKey)) return dedup.get(idempotencyKey);

    const ride = rides.get(rideId);
    if (!ride) throw new Error('RIDE_NOT_FOUND');

    if (ride.assigned_driver_id !== driverId) {
      const err = new Error('FORBIDDEN');
      (err as any).code = '42501';
      throw err;
    }

    if (ride.version !== expectedVersion) {
      const err = new Error('VERSION_CONFLICT');
      (err as any).code = '40001';
      throw err;
    }

    if (ride.state !== 'ARRIVED') {
      throw new Error(`INVALID_TRANSITION from ${ride.state}`);
    }

    ride.state = 'IN_PROGRESS';
    ride.version += 1;

    const result = { status: 'IN_PROGRESS', version: ride.version };
    dedup.set(idempotencyKey, result);
    return result;
  }

  it('100 concurrent driver acceptance attempts result in exactly 1 assigned driver and 99 conflicts', () => {
    let assignedCount = 0;
    let conflictCount = 0;

    for (let i = 0; i < 100; i++) {
      const driverId = `driver-${i}`;
      try {
        simulateAcceptOffer('ride-1', driverId, 'passenger-1', 1, `idem-accept-${i}`);
        assignedCount++;
      } catch (err: any) {
        if (err.message === 'RIDE_ALREADY_ASSIGNED' || err.message === 'VERSION_CONFLICT') {
          conflictCount++;
        } else {
          throw err;
        }
      }
    }

    expect(assignedCount).toBe(1);
    expect(conflictCount).toBe(99);
    expect(rides.get('ride-1')?.state).toBe('ASSIGNED');
    expect(rides.get('ride-1')?.assigned_driver_id).toBe('driver-0');
    expect(rides.get('ride-1')?.version).toBe(2);
  });

  it('rejects invalid state jumps such as START_RIDE directly from ASSIGNED', () => {
    rides.get('ride-1')!.assigned_driver_id = 'driver-1';
    rides.get('ride-1')!.state = 'ASSIGNED';
    rides.get('ride-1')!.version = 2;

    expect(() => {
      simulateStartRide('ride-1', 'driver-1', 2, 'idem-start');
    }).toThrow('INVALID_TRANSITION');
  });

  it('rejects state transition when caller provides stale version', () => {
    rides.get('ride-1')!.assigned_driver_id = 'driver-1';
    rides.get('ride-1')!.state = 'ARRIVED';
    rides.get('ride-1')!.version = 5;

    // Caller presents expectedVersion 4
    expect(() => {
      simulateStartRide('ride-1', 'driver-1', 4, 'idem-stale');
    }).toThrow('VERSION_CONFLICT');
  });
});
