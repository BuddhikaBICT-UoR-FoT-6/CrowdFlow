import mongoose from 'mongoose';
import request from 'supertest';
import { app } from '../index';

const MONGODB_URI = process.env.MONGODB_URI;
const TOMTOM_API_KEY = process.env.TOMTOM_API_KEY;

if (!MONGODB_URI || !TOMTOM_API_KEY) {
  console.warn('Skipping traffic integration tests: MONGODB_URI or TOMTOM_API_KEY not set.');
  describe.skip('Traffic integration (skipped - missing env)', () => {
    test('skip', () => expect(true).toBe(true));
  });
} else {
  jest.setTimeout(20000);

  describe('Traffic/Search Integration', () => {
    beforeAll(async () => {
      await mongoose.connect(MONGODB_URI);
    });

    afterAll(async () => {
      await mongoose.disconnect();
    });

    test('GET /api/v1/traffic returns provider data for Colombo point', async () => {
      const res = await request(app).get('/api/v1/traffic').query({ lat: 6.927079, lon: 79.861244 });
      expect(res.status).toBe(200);
      expect(res.body.ok).toBe(true);
      expect(res.body.data).toBeDefined();
      expect(res.body.data.severity).toBeDefined();
    });

    test('GET /api/v1/search returns results for Colombo', async () => {
      const res = await request(app).get('/api/v1/search').query({ q: 'Colombo' });
      expect(res.status).toBe(200);
      expect(res.body.ok).toBe(true);
      expect(res.body.data).toBeDefined();
    });

    test('POST /api/v1/report accepts a valid sample', async () => {
      const now = Date.now();
      const payload = {
        routeId: 'route-1',
        windowStartMs: Math.floor(now / (15*60*1000)) * (15*60*1000),
        segmentId: '_all',
        severity: 3,
        reportedAtMs: now
      };
      const res = await request(app).post('/api/v1/report').send(payload);
      expect(res.status).toBe(201);
      expect(res.body.ok).toBe(true);
    });
  });
}
