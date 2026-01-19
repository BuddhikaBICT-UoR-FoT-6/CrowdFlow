import axios from 'axios';
import pLimit = require('p-limit');

const TOMTOM_KEY = process.env.TOMTOM_API_KEY;
if (!TOMTOM_KEY) {
  console.warn('TOMTOM_API_KEY not set; TomTom integration will fail until provided.');
}

type TomTomFlowResponse = {
    flowSegmentData?: {
        currentSpeed?: number; // measured speed (km/h)
        freeFlowSpeed?: number; // typical free flow speed (km/h)
        confidence?: number;
    };
};

export type ProviderPointData = {
    lat: number;
    lon: number;
    speed?: number;
    freeFlowSpeed?: number;
    confidence?: number;
    severity?: number; // 0..5
}

// simple in-memory cache (replace with Redis for multi-process)
const pointCache = new Map<string, {expiresAt: number; value: ProviderPointData}>();
const CACHE_TTL_MS = 30 * 1000; // 30s cache to avoid rate bursts

function cacheKey(lat: number, lon: number){
    return `${lat.toFixed(5)},${lon.toFixed(5)}`;
}

// Rate limiting / concurrency control: limit concurrent TomTom requests to avoid bursts
const CONCURRENCY = Number(process.env.TOMTOM_CONCURRENCY || '6');
const limiter = pLimit(CONCURRENCY);

// Retry helper with exponential backoff
async function retry<T>(fn: () => Promise<T>, retries = 3, baseDelay = 300) : Promise<T> {
  let attempt = 0;
  while (true) {
    try {
      return await fn();
    } catch (err: any) {
      attempt++;
      const status = err?.response?.status;
      // if 4xx other than 429 or 413 -> don't retry
      if (status && status >= 400 && status < 500 && status !== 429 && status !== 413) throw err;
      if (attempt > retries) throw err;
      const delay = baseDelay * Math.pow(2, attempt - 1) + Math.floor(Math.random() * 100);
      await new Promise((r) => setTimeout(r, delay));
    }
  }
}

/**
 * Map TomTom speed -> severity 0..5
 */
export function mapToSeverity(speed?: number, freeFlowSpeed?: number): number {
    if (typeof speed !== 'number' || typeof freeFlowSpeed !== 'number' || freeFlowSpeed <= 0) return 5;
    const ratio = Math.max(0, Math.min(1, speed / freeFlowSpeed));

    if(ratio >= 1.0) return 0;
    if(ratio >= 0.9) return 1;
    if(ratio >= 0.7) return 2;
    if(ratio >= 0.5) return 3;
    if(ratio >= 0.25) return 4;
    return 5;
}

/**
 * Fetch TomTom flowSegmentData for a single lat/lon point
 * Uses TomTom Flow Segment Data API.
 */
export async function fetchPointFlow(lat: number, lon: number) : Promise<ProviderPointData>{
    const key = cacheKey(lat, lon);
    const now = Date.now();
    const cached = pointCache.get(key);

    if(cached && cached.expiresAt > now) return cached.value;

    // wrap the request with limiter + retry
    const wrapped = () => axios.get<TomTomFlowResponse>(`https://api.tomtom.com/traffic/services/4/flowSegmentData/absolute/10/json`, {
        params: { point: `${lat},${lon}`, key: TOMTOM_KEY, unit: 'KMPH' },
        timeout: 5000
    }).then(res => res.data);

    try {
        const data = await limiter(() => retry(wrapped, 3, 300));
        const flow = data?.flowSegmentData;
        const speed = flow?.currentSpeed;
        const freeFlowSpeed = flow?.freeFlowSpeed;
        const confidence = flow?.confidence ?? 0;
        const severity = mapToSeverity(speed, freeFlowSpeed);
        const value: ProviderPointData = {lat, lon, speed, freeFlowSpeed, confidence, severity};
        pointCache.set(key, {value, expiresAt: now + CACHE_TTL_MS});
        return value;
    } catch (err: any) {
        console.error('fetchPointFlow error', (err as any)?.message || err);
        const fallback: ProviderPointData = {lat, lon, severity: 5};
        pointCache.set(key, {value: fallback, expiresAt: now + CACHE_TTL_MS});
        return fallback;
    }
}

/**
 * Aggregate provider data for a list of points (e.g., sampled along a route)
 * returns averages and percentiles useful for storing as your AggregateDto.
 */
export function computeAggregatesForPoints(routeId: string, points: ProviderPointData[], windowStartMs: number){
    const severities = points.map((p) => (typeof p.severity === 'number' ? p.severity : 5));
    severities.sort((a, b) => a - b);
    const sampleCount = severities.length;
    const avg = sampleCount > 0 ? severities.reduce((s, v) => s + v, 0) / sampleCount : 5;
    const p50 = sampleCount > 0 ? severities[Math.floor(sampleCount * 0.5)] : null;
    const p90 = sampleCount > 0 ? severities[Math.floor(sampleCount * 0.9)] : null;

    const aggregate = {
        routeId,
        windowStartMs,
        segmentId: '_provider',
        severityAvg: avg,
        severityP50: p50,
        severityP90: p90,
        sampleCount,
        lastAggregatedAtMs: Date.now()
    };
    return aggregate;
}

/**
 * Fetch TomTom data across a bounding box by sampling a small grid of points.
 * bbox: {north, west, south, east}
 * options: maxPoints limits the number of sampled points (default 12)
 */
export async function fetchTomTomForBbox(bbox: {north: number; west: number; south: number; east: number}, options: {maxPoints?: number} = {}) : Promise<ProviderPointData[]>{
    const maxPoints = Math.max(1, Math.min(50, options.maxPoints || 12));
    const latRange = bbox.north - bbox.south;
    const lonRange = bbox.east - bbox.west;

    // create a simple grid (sqrt spacing)
    const perSide = Math.ceil(Math.sqrt(maxPoints));
    const latStep = latRange / Math.max(1, perSide - 1);
    const lonStep = lonRange / Math.max(1, perSide - 1);

    const pts: Array<Promise<ProviderPointData>> = [];
    for (let i = 0; i < perSide; i++){
        for (let j = 0; j < perSide; j++){
            const lat = bbox.south + latStep * i;
            const lon = bbox.west + lonStep * j;
            pts.push(fetchPointFlow(lat, lon));
        }
    }

    const results = await Promise.all(pts);
    return results;
}
