import dotenv = require('dotenv');
import mongoose from 'mongoose';
import * as cron from 'node-cron';
import { Sample, Aggregate } from '../index';
import { fetchTomTomForBbox, computeAggregatesForPoints } from '../integrations/tomtom';

// Load env and connect
dotenv.config();
const MONGODB_URI = process.env.MONGODB_URI;
if (!MONGODB_URI) throw new Error('Missing MONGODB_URI');

// Optional mapping of routeId -> bbox for provider lookups. Example env value:
// TOMTOM_ROUTE_BBOXES='{"138": {"north": 7.3, "west": 79.8, "south": 6.9, "east": 80.2}}'
let ROUTE_BBOXES: Record<string, {north:number; west:number; south:number; east:number}> = {};
try {
  if (process.env.TOMTOM_ROUTE_BBOXES) ROUTE_BBOXES = JSON.parse(process.env.TOMTOM_ROUTE_BBOXES);
} catch (e) {
  console.warn('Invalid TOMTOM_ROUTE_BBOXES JSON, ignoring');
}

// Optional default bbox to use for all routes when ROUTE_BBOXES not provided
let TOMTOM_DEFAULT_BBOX: {north:number; west:number; south:number; east:number} | null = null;
try {
  if (process.env.TOMTOM_DEFAULT_BBOX) TOMTOM_DEFAULT_BBOX = JSON.parse(process.env.TOMTOM_DEFAULT_BBOX);
} catch (e) {
  console.warn('Invalid TOMTOM_DEFAULT_BBOX JSON, ignoring');
}

// If user provided TOMTOM_API_KEY but no bbox config, default to Sri Lanka bbox (reasonable fallback for this project).
if (!TOMTOM_DEFAULT_BBOX && Object.keys(ROUTE_BBOXES).length === 0 && process.env.TOMTOM_API_KEY) {
  TOMTOM_DEFAULT_BBOX = { north: 9.83, west: 79.65, south: 5.85, east: 81.89 };
  console.log('TOMTOM_ROUTE_BBOXES not provided — using default Sri Lanka bbox for provider aggregation. To customize, set TOMTOM_ROUTE_BBOXES or TOMTOM_DEFAULT_BBOX in env.');
}

mongoose.set('strictQuery', true);
mongoose.connect(MONGODB_URI).then(() => console.log('Aggregator: MongoDB connected'));

// Aggregation parameters
const LATE_WINDOW_REPROCESS_COUNT = 2; // reprocess last N windows to handle late-arriving samples

export async function runAggregationForWindow(windowStartMs: number) {
  const windowEndMs = windowStartMs + 60 * 60 * 1000; // 1 hour windows

  // Use MongoDB aggregation pipeline via mongoose.collection.aggregate to compute stats
  const coll = mongoose.connection.collection('samples');

  const pipeline = [
    { $match: { windowStartMs } },
    { $group: {
      _id: { routeId: '$routeId', segmentId: '$segmentId' },
      severities: { $push: '$severity' },
      count: { $sum: 1 },
    }},
    { $project: {
      routeId: '$_id.routeId',
      segmentId: '$_id.segmentId',
      sampleCount: '$count',
      severityAvg: { $avg: '$severities' },
      severityP50: { $arrayElemAt: [ { $sortArray: { input: '$severities', sortBy: 1 } }, { $floor: { $multiply: [0.5, { $subtract: [ { $size: '$severities' }, 1 ] } ] } } ] },
      severityP90: { $arrayElemAt: [ { $sortArray: { input: '$severities', sortBy: 1 } }, { $floor: { $multiply: [0.9, { $subtract: [ { $size: '$severities' }, 1 ] } ] } } ] },
    }},
    { $project: {
      _id: 0,
      routeId: 1,
      segmentId: 1,
      windowStartMs: windowStartMs,
      sampleCount: 1,
      severityAvg: 1,
      severityP50: 1,
      severityP90: 1,
      lastAggregatedAtMs: Date.now(),
    }},
    { $merge: {
      into: 'aggregates',
      on: ['routeId', 'windowStartMs', 'segmentId'],
      whenMatched: 'replace',
      whenNotMatched: 'insert'
    }}
  ];

  const result = await coll.aggregate(pipeline, { allowDiskUse: true }).toArray();
  const processed = Array.isArray(result) ? result.length : 0;
  console.log(`Aggregated window ${new Date(windowStartMs).toISOString()}: processed ${processed} groups`);

  // Determine routes to request provider data for
  let routeIdsToFetch: string[] = [];
  if (Object.keys(ROUTE_BBOXES).length > 0) {
    routeIdsToFetch = Object.keys(ROUTE_BBOXES);
  } else if (TOMTOM_DEFAULT_BBOX) {
    // If no per-route bboxes provided, fetch distinct routeIds from samples for the window
    try {
      routeIdsToFetch = await coll.distinct('routeId', { windowStartMs }) as string[];
    } catch (e) {
      console.error('Failed to list distinct routeIds for provider aggregation', e);
      routeIdsToFetch = [];
    }
  } else {
    // No bbox info available; skip provider aggregation but log guidance
    if (process.env.TOMTOM_API_KEY) {
      console.log('TOMTOM_ROUTE_BBOXES not set and TOMTOM_DEFAULT_BBOX not provided — skipping provider aggregation. To enable, set TOMTOM_ROUTE_BBOXES (per-route bboxes) or TOMTOM_DEFAULT_BBOX (global bbox) in environment.');
    } else {
      console.log('TOMTOM_API_KEY not set — provider aggregation disabled.');
    }
    return processed;
  }

  // For each route, determine bbox (from ROUTE_BBOXES or default) and fetch provider data
  for (const routeId of routeIdsToFetch) {
    const bbox = ROUTE_BBOXES[routeId] || TOMTOM_DEFAULT_BBOX!;
    if (!bbox) continue;
    try {
      console.log(`Fetching TomTom data for route ${routeId} window ${new Date(windowStartMs).toISOString()}`);
      const points = await fetchTomTomForBbox(bbox, { maxPoints: 12 });
      if (points && points.length > 0) {
        const providerAgg = computeAggregatesForPoints(routeId, points, windowStartMs);
        // Upsert provider aggregate as segmentId '_provider'
        await mongoose.connection.collection('aggregates').updateOne(
          { routeId: providerAgg.routeId, windowStartMs: providerAgg.windowStartMs, segmentId: providerAgg.segmentId },
          { $set: providerAgg },
          { upsert: true }
        );
        console.log(`TomTom provider aggregate upserted for route ${routeId}`);

        // --- NEW: compute combined aggregate merging provider points + user samples ---
        try {
          // get user-submitted severities for this route/window
          const sampleDocs = await mongoose.connection.collection('samples').find({ routeId, windowStartMs }).project({ severity: 1, _id: 0 }).toArray();
          const userSeverities: number[] = sampleDocs.map((d: any) => Number(d.severity)).filter((v) => Number.isFinite(v));

          // provider severities from fetched points
          const providerSeverities: number[] = points.map((p) => (typeof p.severity === 'number' ? p.severity as number : 5));

          const combined = [...userSeverities, ...providerSeverities];

          // compute stats simple: avg and percentiles
          combined.sort((a, b) => a - b);
          const totalCount = combined.length;
          const avg = totalCount > 0 ? combined.reduce((s, v) => s + v, 0) / totalCount : 5;
          const p50 = totalCount > 0 ? combined[Math.floor(totalCount * 0.5)] : null;
          const p90 = totalCount > 0 ? combined[Math.floor(totalCount * 0.9)] : null;

          const combinedAgg = {
            routeId,
            windowStartMs,
            segmentId: '_combined',
            severityAvg: avg,
            severityP50: p50,
            severityP90: p90,
            sampleCount: totalCount,
            lastAggregatedAtMs: Date.now(),
          };

          await mongoose.connection.collection('aggregates').updateOne(
            { routeId: combinedAgg.routeId, windowStartMs: combinedAgg.windowStartMs, segmentId: combinedAgg.segmentId },
            { $set: combinedAgg },
            { upsert: true }
          );
          console.log(`Combined aggregate upserted for route ${routeId} (count=${totalCount})`);
        } catch (e) {
          console.error('Failed to compute combined aggregate for route', routeId, (e as any)?.message || e);
        }

      }
    } catch (e) {
      console.error('TomTom provider aggregation failed for route', routeId, (e as any)?.message || e);
    }
  }

  return processed;
}

export async function runRecentWindowAggregation() {
  // compute current window start (hourly buckets)
  const now = Date.now();
  const hourMs = 60 * 60 * 1000;
  const currentWindowStart = Math.floor(now / hourMs) * hourMs;

  // reprocess last N windows
  const starts: number[] = [];
  for (let i = 0; i <= LATE_WINDOW_REPROCESS_COUNT; i++) {
    starts.push(currentWindowStart - i * hourMs);
  }

  let totalProcessed = 0;
  for (const s of starts) {
    try {
      console.log('Aggregating window', new Date(s).toISOString());
      const p = await runAggregationForWindow(s);
      totalProcessed += p;
    } catch (e) {
      console.error('Aggregation error for window', s, e);
    }
  }
  console.log(`Aggregation run complete. Total groups processed: ${totalProcessed}`);
  return totalProcessed;
}

// Allow CLI run: `ts-node src/scripts/aggregator.ts --once` or cron schedule
if (require.main === module) {
  const once = process.argv.includes('--once');
  if (once) {
    runRecentWindowAggregation().then(() => {
      console.log('Aggregation run complete');
      process.exit(0);
    }).catch((e) => { console.error(e); process.exit(1); });
  } else {
    // run every 5 minutes to keep hour aggregates fresh
    cron.schedule('*/5 * * * *', () => {
      runRecentWindowAggregation().catch(console.error);
    });
    console.log('Aggregator scheduled: runs every 5 minutes');
  }
}
