import dotenv from 'dotenv';
import mongoose from 'mongoose';
import cron from 'node-cron';
import { Sample, Aggregate } from '../index';

// Load env and connect
dotenv.config();
const MONGODB_URI = process.env.MONGODB_URI;
if (!MONGODB_URI) throw new Error('Missing MONGODB_URI');

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
