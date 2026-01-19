// File: server/src/models/aggregates.ts
import mongoose from 'mongoose';

/**
 * Upsert an aggregate object into `aggregates` collection.
 * aggregate must include: routeId, windowStartMs, segmentId (e.g. "_provider"), etc.
 *
 * Uses Mongoose connection to avoid creating a separate MongoClient and to reuse
 * the application's existing connection.
 */
export async function saveAggregateToDb(aggregate: Record<string, any>) {
  if (!aggregate || !aggregate.routeId || typeof aggregate.windowStartMs === 'undefined' || !aggregate.segmentId) {
    throw new Error('Invalid aggregate object; requires routeId, windowStartMs, segmentId');
  }

  const coll = mongoose.connection.collection('aggregates');

  // ensure lastAggregatedAtMs gets updated
  aggregate.lastAggregatedAtMs = Date.now();

  const filter = {
    routeId: aggregate.routeId,
    windowStartMs: aggregate.windowStartMs,
    segmentId: aggregate.segmentId,
  };

  const update = { $set: aggregate };

  try {
    await coll.updateOne(filter, update, { upsert: true });
    return true;
  } catch (e: unknown) {
    console.error('saveAggregateToDb error', (e as any)?.message || e);
    return false;
  }
}
