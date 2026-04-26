
import { Client, WorkOrder, Service, Mechanic } from '../types';

const STORAGE_PREFIX = 'meet_';

const isIsoDate = /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d+)?(?:Z|[+-]\d{2}:\d{2})$/;

/**
 * Serializes data for JSON storage.
 */
function serialize(data: any): string {
  return JSON.stringify(data);
}

/**
 * Deserializes strings back to objects, reviving Dates.
 */
function deserialize<T>(json: string): T {
  return JSON.parse(json, (key, value) => {
    // Check if the value is an object with __date (legacy support for the previous attempt)
    if (value && typeof value === 'object' && value.__date) return new Date(value.iso);
    // Check if the value is an ISO date string
    if (typeof value === 'string' && isIsoDate.test(value)) return new Date(value);
    return value;
  });
}

export function saveState(key: string, data: any): void {
  try {
    localStorage.setItem(STORAGE_PREFIX + key, serialize(data));
  } catch (e) {
    console.warn(`[MEET] Failed to save ${key}:`, e);
  }
}

export function loadState<T>(key: string, fallback: T): T {
  try {
    const stored = localStorage.getItem(STORAGE_PREFIX + key);
    if (!stored) return fallback;
    return deserialize<T>(stored);
  } catch (e) {
    console.warn(`[MEET] Failed to load ${key}:`, e);
    return fallback;
  }
}

export function clearAllState(): void {
  Object.keys(localStorage)
    .filter(k => k.startsWith(STORAGE_PREFIX))
    .forEach(k => localStorage.removeItem(k));
}
