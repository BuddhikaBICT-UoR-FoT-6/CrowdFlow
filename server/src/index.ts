// Load environment variables as early as possible
require('dotenv').config();
import { validateConfig } from './config';
const cfg = validateConfig();

import express = require('express');
import type { Request, Response } from 'express';
import cors = require('cors');
import mongoose from 'mongoose';
import axios from 'axios';
import bcrypt = require('bcrypt');
import { z } from 'zod';
import helmet from 'helmet';
import compression = require('compression');
import mongoSanitize = require('express-mongo-sanitize');
import winston from 'winston';
import { requireAuth, signAccessToken, type AuthedRequest, signRefreshToken, verifyRefreshToken, verifyTokenMiddleware, rotateRefreshToken, revokeAllTokensForUser, isAccountLocked, recordFailedLogin, clearLockout } from './auth';
import { User, type UserRole } from './models/User';
import { RefreshTokenModel } from './models/RefreshToken';
import { requireRole } from './roles';
import { loginLimiter, registerLimiter, trafficSamplesLimiter, getRecentRateLimitBlocks, listBlockedKeys, removeBlockedKey } from './middleware/rateLimiter';
import { duplicateSubmissionProtection } from './middleware/duplicateDetection';
import tomtomDebugRoute from './routes/tomtomDebugRoute';
import trafficRoute from './routes/trafficRoute';
import searchRoute from './routes/searchRoute';
import osmRoutes from './routes/osmRoutes';
import { startTomTomScheduler } from './tasks/tomtomScheduler';
import { startAggregationCron } from './tasks/aggregationCron';
import { internalOnly } from './middleware/internalOnly';
import { TrafficQueryService } from './services/ITrafficQueryService';
import { AggregationService } from './services/IAggregationService';

const queryService = new TrafficQueryService();
const aggregationService = new AggregationService();

const logger = winston.createLogger({
  level: process.env.LOG_LEVEL || 'info',
  format: winston.format.combine(
    winston.format.timestamp(),
    winston.format.errors({ stack: true }),
    process.env.NODE_ENV === 'production'
      ? winston.format.json()
      : winston.format.simple()
  ),
  transports: [new winston.transports.Console()],
});


const app = express();
app.use(helmet());
app.use(compression());
app.use(cors());
// Set a conservative global JSON body limit to protect against huge payloads (1MB)
app.use(express.json({ limit: '1mb' }));
app.use(mongoSanitize());

// Respect proxy headers (X-Forwarded-For) when behind a proxy/load-balancer.
// Set to true for generic setups; in production you may prefer a restricted list
// of trusted proxies or IP ranges for tighter security.
app.set('trust proxy', true);

// HTTPS redirect in production
if (cfg.NODE_ENV === 'production') {
  app.use((req: Request, res: Response, next) => {
    if (req.headers['x-forwarded-proto'] !== 'https') {
      return res.redirect(301, `https://${req.headers.host}${req.url}`);
    }
    next();
  });
}

if(!process.env.REDIS_URL){
  logger.warn('REDIS_URL is NOT configured — rate limiter will use in-memory fallback and duplicate detection will not work. Configure REDIS_URL for production.');
}

const MONGODB_URI = process.env.MONGODB_URI;
const TOMTOM_API_KEY = process.env.TOMTOM_API_KEY;

if (!MONGODB_URI) {
  logger.error('Missing MONGODB_URI in environment');
  process.exit(1);
}

if(!TOMTOM_API_KEY){
  logger.warn('TOMTOM_API_KEY is NOT configured — TomTom debug route will not work. Configure TOMTOM_API_KEY for production.');
}

// Mongo connection
mongoose.set('strictQuery', true);
mongoose
  .connect(MONGODB_URI)
  .then(() => {
    logger.info('MongoDB connected');
    startAggregationCron();
  })
  .catch((err) => {
    logger.error('MongoDB connection error', { err });
    process.exit(1);
  });

// Schemas
const SAMPLE_RETENTION_DAYS = Number(process.env.SAMPLE_RETENTION_DAYS || '30');

const SampleSchema = new mongoose.Schema(
  {
    routeId: { type: String, required: true, index: true },
    windowStartMs: { type: Number, required: true, index: true },
    segmentId: { type: String, required: true, index: true },
    severity: { type: Number, required: true, min: 0, max: 5 },
    reportedAtMs: { type: Number, required: true, index: true },
    userIdHash: { type: String },
    location: {
      type: { type: String, enum: ['Point'] },
      coordinates: { type: [Number] }
    },
    createdAt: { type: Date, required: true, default: () => new Date(), index: true },
  },
  { timestamps: false, versionKey: false }
);
SampleSchema.index({ location: '2dsphere' });
// TTL index to automatically remove old samples
if (SAMPLE_RETENTION_DAYS > 0) {
  const seconds = SAMPLE_RETENTION_DAYS * 24 * 60 * 60;
  SampleSchema.index({ createdAt: 1 }, { expireAfterSeconds: seconds });
}

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

// Export models so other scripts (aggregator) can import them
export { Sample, Aggregate };

// Validation schemas
const submitSampleSchema = z.object({
  routeId: z.string().min(1),
  windowStartMs: z.number().int().positive(),
  segmentId: z.string().default('_all'),
  severity: z.number().min(0).max(5),
  reportedAtMs: z.number().int().positive(),
  userIdHash: z.string().optional(),
  lat: z.number().optional(),
  lon: z.number().optional(),
});

const aggregateWindowSchema = z.object({
  routeId: z.string().min(1),
  windowStartMs: z.number().int().positive(),
  segmentId: z.string().default('_all'),
});

const registerSchema = z.object({
  email: z.string().email(),
  password: z.string().min(8),
  deviceId: z.string().min(1),
  deviceName: z.string().optional(),
});

const loginSchema = z.object({
  email: z.string().email(),
  password: z.string().min(1),
  deviceId: z.string().min(1),
  deviceName: z.string().optional(),
});

const refreshSchema = z.object({
  refreshToken: z.string().min(1),
});

const createUserSchema = z.object({
  email: z.string().email(),
  password: z.string().min(8),
  role: z.enum(['superadmin', 'admin', 'user']).default('user'),
});

const updateUserRoleSchema = z.object({
  role: z.enum(['superadmin', 'admin', 'user']),
});

const providerQuery = z.object({
    lat: z.string().transform(Number),
    lon: z.string().transform(Number),
});

function computeStats(values: number[]) {
  const sorted = [...values].sort((a, b) => a - b);
  const len = sorted.length;
  const avg = sorted.reduce((s, v) => s + v, 0) / len;
  const p = (q: number) => sorted[Math.floor(q * (len - 1))];
  return { avg, p50: p(0.5), p90: p(0.9), count: len };
}

// for map function
function mapSpeedToSeverity(speedKmph: number | null){
    // lower speed = higher severity
    if(speedKmph === null || speedKmph == undefined) return 3;
    if(speedKmph < 10) return 5;
    if(speedKmph < 20) return 4;
    if(speedKmph < 30) return 3;
    if(speedKmph < 40) return 2;
    if(speedKmph < 50) return 1;
    return 0;
}

async function fetchTomTomFlow(lat: number, lon: number){
    // TomTom flow segment data endpoint
    const url = `https://api.tomtom.com/traffic/services/4/flowSegmentData/absolute/10/json`;
    const params = {
        point: `${lat},${lon}`,
        unit: 'KMPH',
        key: TOMTOM_API_KEY
    };
    const res = await axios.get(url, {params, timeout: 5000});
    return res.data;
}

// Export helper for aggregator scripts
export { computeStats };

// Health check endpoints
app.get('/health', (_req: Request, res: Response) => res.json({ ok: true }));
app.get('/healthz', (_req: Request, res: Response) => res.json({ ok: true, uptime: process.uptime() }));

// Mount TomTom debug route (provides GET /api/v1/debug/provider/point)
app.use(tomtomDebugRoute);
// Mount traffic routes (GET /api/v1/traffic and POST /api/v1/traffic/bbox)
app.use(trafficRoute);
// Mount search routes (GET /api/v1/search and GET /api/v1/reverse)
app.use(searchRoute);
// Mount OSM/Overpass route discovery + GeoJSON (GET /api/v1/osm/routes, /api/v1/osm/routes/:ref/geojson)
app.use(osmRoutes);

// --- Auth routes ---
app.post('/api/v1/auth/register', registerLimiter, async (req: Request, res: Response) => {
  try {
    const { email, password, deviceId, deviceName } = registerSchema.parse(req.body);

    const existing = await User.findOne({ email: email.toLowerCase() }).lean();
    if (existing) {
      return res.status(409).json({ ok: false, error: 'Email already registered' });
    }

    const passwordHash = await bcrypt.hash(password, 12);
    const created = await User.create({ email: email.toLowerCase(), passwordHash, role: 'user' as UserRole });

    const accessToken = signAccessToken({ id: String(created._id), email: created.email, role: String(created.get('role')) as UserRole });
    const refreshToken = await signRefreshToken(String(created._id), deviceId, deviceName);

    return res.status(201).json({
      ok: true,
      data: {
        accessToken,
        refreshToken,
        user: { id: String(created._id), email: created.email, role: String(created.get('role')) },
      },
    });
  } catch (e: any) {
    logger.error('/auth/register error', { err: e });
    return res.status(400).json({ ok: false, error: e.message ?? 'Bad Request' });
  }
});

app.post('/api/v1/auth/login', loginLimiter, async (req: Request, res: Response) => {
  try {
    const { email, password, deviceId, deviceName } = loginSchema.parse(req.body);

    const user = await User.findOne({ email: email.toLowerCase() });
    if (!user) {
      await recordFailedLogin(email);
      return res.status(401).json({ ok: false, error: 'Invalid email or password' });
    }

    if (await isAccountLocked(email)) {
      return res.status(429).json({ ok: false, error: 'Account temporarily locked due to too many failed attempts' });
    }

    const ok = await bcrypt.compare(password, String(user.get('passwordHash')));
    if (!ok) {
      await recordFailedLogin(email);
      return res.status(401).json({ ok: false, error: 'Invalid email or password' });
    }
    await clearLockout(email);

    // Enforce max 5 concurrent sessions per user
    const existingSessions = await RefreshTokenModel.find({ userId: String(user._id) }).lean();
    const otherSessions = existingSessions.filter(s => s.deviceId !== deviceId);
    if (otherSessions.length >= 5) {
      return res.status(429).json({ ok: false, error: 'Maximum of 5 concurrent sessions reached' });
    }

    const accessToken = signAccessToken({
      id: String(user._id),
      email: String(user.get('email')),
      role: String(user.get('role') || 'user') as UserRole,
    });
    const refreshToken = await signRefreshToken(String(user._id), deviceId, deviceName);

    return res.json({
      ok: true,
      data: {
        accessToken,
        refreshToken,
        user: { id: String(user._id), email: String(user.get('email')), role: String(user.get('role') || 'user') },
      },
    });
  } catch (e: any) {
    logger.error('/auth/login error', { err: e });
    return res.status(400).json({ ok: false, error: e.message ?? 'Bad Request' });
  }
});

app.post('/api/v1/auth/refresh', async (req: Request, res: Response) => {
  try {
    const { refreshToken } = refreshSchema.parse(req.body);
    const decoded = await verifyRefreshToken(refreshToken);

    const user = await User.findById(decoded.userId);
    if (!user) {
      return res.status(401).json({ ok: false, error: 'Invalid refresh token' });
    }

    const newRefreshToken = await rotateRefreshToken(refreshToken);

    const accessToken = signAccessToken({
      id: String(user._id),
      email: String(user.get('email')),
      role: String(user.get('role') || 'user') as UserRole,
    });

    return res.json({ ok: true, data: { accessToken, refreshToken: newRefreshToken } });
  } catch (e: any) {
    logger.error('/auth/refresh error', { err: e });
    return res.status(400).json({ ok: false, error: e.message ?? 'Bad Request' });
  }
});

app.post('/api/v1/auth/logout', requireAuth, async (req: AuthedRequest, res: Response) => {
  try {
    const { refreshToken } = req.body || {};
    if (refreshToken) {
      try {
        const { userId, deviceId } = await verifyRefreshToken(refreshToken);
        if (userId === req.user!.sub) {
          await RefreshTokenModel.deleteOne({ userId, deviceId });
        }
      } catch (e) {
        // ignore invalid token
      }
    }
    return res.json({ ok: true });
  } catch (e: any) {
    logger.error('/auth/logout error', { err: e });
    return res.status(400).json({ ok: false, error: e.message ?? 'Bad Request' });
  }
});

app.post('/api/v1/auth/logout-all', requireAuth, async (req: AuthedRequest, res: Response) => {
  try {
    await RefreshTokenModel.deleteMany({ userId: req.user!.sub });
    return res.json({ ok: true });
  } catch (e: any) {
    logger.error('/auth/logout-all error', { err: e });
    return res.status(400).json({ ok: false, error: e.message ?? 'Bad Request' });
  }
});

app.get('/api/v1/auth/sessions', requireAuth, async (req: AuthedRequest, res: Response) => {
  try {
    const sessions = await RefreshTokenModel.find({ userId: req.user!.sub })
      .select('deviceId deviceName issuedAt lastUsedAt -_id')
      .lean();
    return res.json({ ok: true, data: sessions });
  } catch (e: any) {
    logger.error('/auth/sessions error', { err: e });
    return res.status(400).json({ ok: false, error: e.message ?? 'Bad Request' });
  }
});

app.delete('/api/v1/auth/sessions/:deviceId', requireAuth, async (req: AuthedRequest, res: Response) => {
  try {
    const { deviceId } = req.params;
    await RefreshTokenModel.deleteOne({ userId: req.user!.sub, deviceId });
    return res.json({ ok: true });
  } catch (e: any) {
    logger.error('/auth/sessions DELETE error', { err: e });
    return res.status(400).json({ ok: false, error: e.message ?? 'Bad Request' });
  }
});

// --- Admin/Superadmin user management ---
// Superadmin: list all users
app.get('/api/v1/admin/users', requireAuth, requireRole(['superadmin']), async (_req: AuthedRequest, res: Response) => {
  const users = await User.find({}).select('email role createdAt').lean();
  return res.json({ ok: true, data: users });
});

// Admin+Superadmin: create regular users; Superadmin can create admins/superadmins
app.post('/api/v1/admin/users', requireAuth, requireRole(['admin', 'superadmin']), async (req: AuthedRequest, res: Response) => {
  try {
    const { email, password, role } = createUserSchema.parse(req.body);

    const requestedRole = role as UserRole;
    const callerRole = req.user!.role;

    if (callerRole !== 'superadmin' && requestedRole !== 'user') {
      return res.status(403).json({ ok: false, error: 'Only superadmin can create admin/superadmin accounts' });
    }

    const normalizedEmail = email.toLowerCase();
    const existing = await User.findOne({ email: normalizedEmail }).lean();
    if (existing) {
      return res.status(409).json({ ok: false, error: 'Email already registered' });
    }

    const passwordHash = await bcrypt.hash(password, 12);
    const created = await User.create({ email: normalizedEmail, passwordHash, role: requestedRole });

    return res.status(201).json({
      ok: true,
      data: { id: String(created._id), email: String(created.get('email')), role: String(created.get('role')) },
    });
  } catch (e: any) {
    logger.error('/admin/users POST error', { err: e });
    return res.status(400).json({ ok: false, error: e.message ?? 'Bad Request' });
  }
});

// Superadmin only: update a user's role
app.patch('/api/v1/admin/users/:id/role', requireAuth, requireRole(['superadmin']), async (req: AuthedRequest, res: Response) => {
  try {
    const { id } = req.params;
    const { role } = updateUserRoleSchema.parse(req.body);
    const updated = await User.findByIdAndUpdate(
      id,
      { $set: { role } },
      { new: true }
    ).select('email role createdAt');

    if (!updated) {
      return res.status(404).json({ ok: false, error: 'User not found' });
    }

    return res.json({ ok: true, data: { id: String(updated._id), email: String(updated.get('email')), role: String(updated.get('role')) } });
  } catch (e: any) {
    logger.error('/admin/users/:id/role PATCH error', { err: e });
    return res.status(400).json({ ok: false, error: e.message ?? 'Bad Request' });
  }
});

// Admin endpoint: recent rate-limit blocks (superadmin only)
app.get('/api/v1/admin/rate-limits', requireAuth, requireRole(['superadmin']), async (_req: AuthedRequest, res: Response) => {
  try {
    const items = await getRecentRateLimitBlocks(200);
    return res.json({ ok: true, data: items });
  } catch (e: any) {
    logger.error('/admin/rate-limits error', { err: e });
    return res.status(500).json({ ok: false, error: 'Failed to fetch rate-limit blocks' });
  }
});

// Admin endpoint: list temporary blocklist entries (superadmin only)
app.get('/api/v1/admin/blocks', requireAuth, requireRole(['superadmin']), async (_req: AuthedRequest, res: Response) => {
  try {
    const items = await listBlockedKeys(200);
    return res.json({ ok: true, data: items });
  } catch (e: any) {
    logger.error('/admin/blocks error', { err: e });
    return res.status(500).json({ ok: false, error: 'Failed to list blocked keys' });
  }
});

// Admin endpoint: remove/unblock a key (superadmin only)
app.delete('/api/v1/admin/blocks/:key', requireAuth, requireRole(['superadmin']), async (req: AuthedRequest, res: Response) => {
  try {
    const key = String(req.params.key || '').trim();
    if (!key) return res.status(400).json({ ok: false, error: 'Missing key' });
    const ok = await removeBlockedKey(key);
    if (!ok) return res.status(404).json({ ok: false, error: 'Key not found or removal failed' });
    return res.json({ ok: true });
  } catch (e: any) {
    logger.error('/admin/blocks DELETE error', { err: e });
    return res.status(500).json({ ok: false, error: 'Failed to remove block' });
  }
});

// Routes
app.post(
  '/api/v1/samples',
  requireAuth,
  trafficSamplesLimiter,
  duplicateSubmissionProtection(60 * 5),
  async (req: AuthedRequest, res: Response) => {
    try {
      const body = req.body;
      const MAX_SAMPLES = 1000; // per-request limit

      const samplesArray = Array.isArray(body) ? body : [body];
      if (samplesArray.length === 0) {
        return res.status(400).json({ ok: false, error: 'Empty payload' });
      }
      if (samplesArray.length > MAX_SAMPLES) {
        return res.status(413).json({ ok: false, error: `Too many samples - max ${MAX_SAMPLES}` });
      }

      // Validate each sample using submitSampleSchema
      const parsed: any[] = [];
      for (const s of samplesArray) {
        const p = submitSampleSchema.parse(s);
        const mapped: any = { ...p };
        if (p.lat != null && p.lon != null) {
            mapped.location = { type: 'Point', coordinates: [p.lon, p.lat] };
            delete mapped.lat;
            delete mapped.lon;
        }
        parsed.push(mapped);
      }

      // Bulk insert
      await Sample.insertMany(parsed, { ordered: false });
      res.status(201).json({ ok: true, inserted: parsed.length });
    } catch (e: any) {
      logger.error('/samples error', { err: e });
      res.status(400).json({ ok: false, error: e.message ?? 'Bad Request' });
    }
  },
);


app.post('/api/v1/aggregate', requireAuth, internalOnly, async (req: AuthedRequest, res: Response) => {
  try {
    const { routeId, windowStartMs, segmentId } = aggregateWindowSchema.parse(req.body);

    const doc = await aggregationService.aggregateWindow(routeId, windowStartMs, segmentId);
    if (!doc) {
      return res.json({ ok: true, message: 'No samples to aggregate' });
    }
    res.json({ ok: true, data: doc });
  } catch (e: any) {
    logger.error('/aggregate error', { err: e });
    res.status(400).json({ ok: false, error: e.message ?? 'Bad Request' });
  }
});

app.get('/api/v1/aggregates', requireAuth, async (req: AuthedRequest, res: Response) => {
  try {
    const routeId = req.query.routeId ? String(req.query.routeId).trim() : undefined;
    const windowStartMs = req.query.windowStartMs ? Number(req.query.windowStartMs) : undefined;
    
    const results = await queryService.getAggregates(routeId, windowStartMs);
    res.json({ ok: true, data: results });
  } catch (e: any) {
    logger.error('/aggregates error', { err: e });
    res.status(400).json({ ok: false, error: e.message ?? 'Bad Request' });
  }
});


// Public reporting endpoint (convenience wrapper for UI clients).
// Validates payload and inserts into samples collection. This mirrors /api/v1/samples behavior
// but can be used as a client-friendly endpoint (without requiring auth token).
app.post('/api/v1/report', async (req: Request, res: Response) => {
  try {
    const body = req.body;
    const p = submitSampleSchema.parse(body);
    await Sample.create(p);
    return res.status(201).json({ ok: true, inserted: 1 });
  } catch (e: any) {
    logger.error('/api/v1/report error', { err: e });
    return res.status(400).json({ ok: false, error: e?.message || 'Bad Request' });
  }
});

// Export app for testing and reuse
export { app };

// Only start listening when run directly
if (require.main === module) {
  const PORT = Number(process.env.PORT || 3000);
  const HOST = process.env.HOST || '0.0.0.0';

  const server = app.listen(PORT, HOST, () => {
    logger.info(`Server listening on http://${HOST}:${PORT}`);
  });

  server.on('error', (err: any) => {
    if (err && err.code === 'EADDRINUSE') {
      logger.error(`Port ${PORT} is already in use. Stop the existing process or start with a different PORT.`);
      process.exit(1);
    }
    logger.error('Server failed to start', { err });
    process.exit(1);
  });
}


/**
 * Protect traffic samples submissions:
 * - verify token (must be authenticated)
 * - per-user rate limit
 * - duplicate detection
 */
app.post(
  '/traffic/samples',
  verifyTokenMiddleware,         // must set req.user
  trafficSamplesLimiter,         // per-user + per-ip limits
  duplicateSubmissionProtection(60 * 5), // 5min duplicate TTL
  async (req: AuthedRequest, res: Response) => {
    try {
      const body = req.body;
      const MAX_SAMPLES = 1000; // per-request limit

      const samplesArray = Array.isArray(body) ? body : [body];
      if (samplesArray.length === 0) {
        return res.status(400).json({ ok: false, error: 'Empty payload' });
      }
      if (samplesArray.length > MAX_SAMPLES) {
        return res.status(413).json({ ok: false, error: `Too many samples - max ${MAX_SAMPLES}` });
      }

      // Validate each sample using submitSampleSchema and attach userIdHash if missing
      const parsed: any[] = [];
      for (const s of samplesArray) {
        const p = submitSampleSchema.parse(s);
        const mapped: any = { ...p };
        if (p.lat != null && p.lon != null) {
            mapped.location = { type: 'Point', coordinates: [p.lon, p.lat] };
            delete mapped.lat;
            delete mapped.lon;
        }
        // Prefer explicit userIdHash from payload; if missing, attach authenticated user id
        if (!mapped.userIdHash && req.user && req.user.sub) {
          mapped.userIdHash = String(req.user.sub);
        }
        parsed.push(mapped);
      }

      // Bulk insert into samples collection
      await Sample.insertMany(parsed, { ordered: false });
      res.status(201).json({ ok: true, inserted: parsed.length });
    } catch (e: any) {
      logger.error('/traffic/samples error', { err: e });
      res.status(400).json({ ok: false, error: e.message ?? 'Bad Request' });
    }
  },
);

app.get('/api/v1/debug/provider/point', async (req, res) => {
    try{
        const q = providerQuery.parse(req.query);
        if(!TOMTOM_API_KEY){
            return res.status(400).json({ok: false, error: 'TOMTOM_API_KEY not configured'});
        }

        const raw = await fetchTomTomFlow(q.lat, q.lon);
        // Try to extract currentSpeed from response in typical TomTom shape.
        let speed: number | null = null;

        try{
            // typical path: flowSegmentData -> RWS -> RW -> FIS -> FI -> CF -> SP (varies). Use safe navigation.
            if(raw && raw.flowSegmentData && raw.flowSegmentData.currentSpeed != null){
                speed = Number(raw.flowSegmentData.currentSpeed);
            } else if(raw && raw.flowSegmentData && raw.flowSegmentData.freeFlowSpeed) {
                // fallback
                speed = Number(raw.flowSegmentData.freeFlowSpeed);
            }
        } catch(e) { speed = null; }

        const severity = mapSpeedToSeverity(speed);
        return res.json({ ok: true, provider: raw, mapped: { speedKmph: speed, severity }});

    } catch(e: any){
        logger.error('/debug/provider/point error', { err: e });
        return res.status(400).json({ok: false, error: e?.message || 'Bad Request'});
    }
});


// Only start TomTom scheduler when explicitly enabled in env
if (process.env.ENABLE_TOMTOM_SCHEDULER === 'true') {
  try {
    startTomTomScheduler();
    logger.info('TomTom scheduler enabled');
  } catch (e) {
    logger.error('Failed to start TomTom scheduler', { err: e });
  }
}
