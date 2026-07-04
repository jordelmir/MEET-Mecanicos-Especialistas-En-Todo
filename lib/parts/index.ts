/**
 * lib/parts — barrel exports for the parts marketplace shared layer.
 * The web/React surface and any future Node worker consume this entry point.
 *
 * PR-3 ships types, compatibility engine, and quote utilities. Suggestion
 * + ranking engines are exposed via dedicated entry points (see PR-2).
 */

export * from './types';
export * from './compatibility';
export * from './quote';
