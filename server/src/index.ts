import express, { type Request, type Response } from 'express';
import cors from 'cors';
import dotenv from 'dotenv';
import mongoose from 'mongoose';
import { z } from 'zod';

dotenv.config();

const app = express();
app.use(cors());
app.use(express.json());

const MONGODB_URI = process.env.MONGODB_URI;
if (!MONGODB_URI) {
  console.error('Missing MONGODB_URI in environment');
  process.exit(1);
}

// Mongo connection
mongoose.set('strictQuery', true);
mongoose
  .connect(MONGODB_URI)
  .then(() => console.log('MongoDB connected'))
  .catch((err) => {
    console.error('MongoDB connection error:', err);
    process.exit(1);
  });

// Schemas
const SampleSchema = new mongoose.Schema(
  {
    routeId: { type: String, required: true, index: true },
    windowStartMs: { type: Number, required: true, index: true },
    segmentId: { type: String, required: true, index: true },
    severity: { type: Number, required: true, min: 0, max: 5 },
    reportedAtMs: { type: Number, required: true, index: true },
    userIdHash: { type: String },
  },
  { timestamps: false, versionKey: false }
);
SampleSchema.index({ routeId: 1, windowStartMs: 1, segmentId: 1, reportedAtMs: -1 });

const AggregateSchema = new mongoose.Schema(
  {
    routeId: { type: String, required: true, index: true },
    windowStartMs: { type: Number, required: true, index: true },
    segmentId: { type: String, required: true, index: true },
    severityAvg: { type: Number, required: true },
    severityP50: { type: Number },
    severityP90: { type: Number },
    sampleCount: { type: Number, required: true },
    lastAggregatedAtMs: { type: Number, required: true },
  },
  { timestamps: false, versionKey: false }
);
AggregateSchema.index({ routeId: 1, windowStartMs: 1, segmentId: 1 }, { unique: true });

const Sample = mongoose.model('Sample', SampleSchema);
const Aggregate = mongoose.model('Aggregate', AggregateSchema);

// Validation schemas
const submitSampleSchema = z.object({
  routeId: z.string().min(1),
  windowStartMs: z.number().int().positive(),
  segmentId: z.string().default('_all'),
  severity: z.number().min(0).max(5),
  reportedAtMs: z.number().int().positive(),
  userIdHash: z.string().optional(),
});

const aggregateWindowSchema = z.object({
  routeId: z.string().min(1),
  windowStartMs: z.number().int().positive(),
  segmentId: z.string().default('_all'),
});

function computeStats(values: number[]) {
  const sorted = [...values].sort((a, b) => a - b);
  const len = sorted.length;
  const avg = sorted.reduce((s, v) => s + v, 0) / len;
  const p = (q: number) => sorted[Math.floor(q * (len - 1))];
  return { avg, p50: p(0.5), p90: p(0.9), count: len };
}

// Health check (useful for emulator connectivity testing)
app.get('/health', (_req: Request, res: Response) => {
  res.json({ ok: true });
});

// Routes
app.post('/api/v1/samples', async (req: Request, res: Response) => {
  try {
    const parsed = submitSampleSchema.parse(req.body);
    await Sample.create(parsed);
    res.status(201).json({ ok: true });
  } catch (e: any) {
    console.error('/samples error', e);
    res.status(400).json({ ok: false, error: e.message ?? 'Bad Request' });
  }
});

app.post('/api/v1/aggregate', async (req: Request, res: Response) => {
  try {
    const { routeId, windowStartMs, segmentId } = aggregateWindowSchema.parse(req.body);

    const samples = await Sample.find({ routeId, windowStartMs, segmentId }).select('severity');
    if (samples.length === 0) {
      return res.json({ ok: true, message: 'No samples to aggregate' });
    }
    const severities = samples.map((s) => Number(s.severity));
    const stats = computeStats(severities);

    const doc = {
      routeId,
      windowStartMs,
      segmentId,
      severityAvg: stats.avg,
      severityP50: stats.p50,
      severityP90: stats.p90,
      sampleCount: stats.count,
      lastAggregatedAtMs: Date.now(),
    };

    await Aggregate.updateOne(
      { routeId, windowStartMs, segmentId },
      { $set: doc },
      { upsert: true }
    );

    res.json({ ok: true, data: doc });
  } catch (e: any) {
    console.error('/aggregate error', e);
    res.status(400).json({ ok: false, error: e.message ?? 'Bad Request' });
  }
});

app.get('/api/v1/aggregates', async (req: Request, res: Response) => {
  try {
    const routeId = String(req.query.routeId || '').trim();
    const windowStartMs = Number(req.query.windowStartMs || 0);
    if (!routeId || !Number.isFinite(windowStartMs) || windowStartMs <= 0) {
      return res.status(400).json({ ok: false, error: 'Invalid parameters' });
    }

    const results = await Aggregate.find({ routeId, windowStartMs }).lean();
    res.json({ ok: true, data: results });
  } catch (e: any) {
    console.error('/aggregates error', e);
    res.status(400).json({ ok: false, error: e.message ?? 'Bad Request' });
  }
});

const PORT = Number(process.env.PORT || 3000);
app.listen(PORT, () => console.log(`Server listening on http://localhost:${PORT}`));

