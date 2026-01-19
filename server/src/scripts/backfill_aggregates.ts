import dotenv from 'dotenv';
import mongoose from 'mongoose';
import { Sample } from '../index';
import { runAggregationForWindow as runWindowAggregationInternal } from './aggregator';

dotenv.config();
const MONGODB_URI = process.env.MONGODB_URI;
if (!MONGODB_URI) throw new Error('Missing MONGODB_URI');

mongoose.set('strictQuery', true);
mongoose.connect(MONGODB_URI).then(() => console.log('Backfill: MongoDB connected'));

async function listDistinctWindows(limit = 1000) {
  const coll = mongoose.connection.collection('samples');
  const windows = await coll.distinct('windowStartMs');
  windows.sort((a: number, b: number) => a - b);
  return windows.slice(0, limit);
}

async function backfill(limit = 1000) {
  const windows = await listDistinctWindows(limit);
  console.log(`Found ${windows.length} distinct windows (processing up to ${limit})`);
  for (const w of windows) {
    console.log('Backfilling window', new Date(w).toISOString());
    try {
      // call the aggregator's window-level function
      // NOTE: aggregator exposes this function for reuse
      // @ts-ignore
      await runWindowAggregationInternal(w);
    } catch (e) {
      console.error('Backfill error for window', w, e);
    }
  }
}

if (require.main === module) {
  const limitArg = Number(process.argv[2] || 1000);
  backfill(limitArg).then(() => process.exit(0)).catch((e) => { console.error(e); process.exit(1); });
}

