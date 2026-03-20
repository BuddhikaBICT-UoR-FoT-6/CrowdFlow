import type { Request, Response, NextFunction } from 'express';
import * as jwt from 'jsonwebtoken';
import type { SignOptions, Secret } from 'jsonwebtoken';
import { User, type UserRole } from './models/User';
import * as crypto from 'crypto';
import * as bcrypt from 'bcrypt';
import { RefreshTokenModel } from './models/RefreshToken';
import { redisClient } from './middleware/rateLimiter';

export const DEFAULT_ACCESS_EXPIRES = process.env.JWT_EXPIRES_IN || '15m';
export const DEFAULT_REFRESH_EXPIRES = process.env.JWT_REFRESH_EXPIRES_IN || '30d';
const ACCESS_SECRET = process.env.JWT_SECRET;
const REFRESH_SECRET = process.env.JWT_REFRESH_SECRET || process.env.JWT_SECRET; // fallback for compatibility

if (!ACCESS_SECRET) {
  throw new Error('Missing JWT_SECRET (access token secret) in environment');
}

// In production, require a distinct refresh secret for better isolation
if (process.env.NODE_ENV === 'production' && (!process.env.JWT_REFRESH_SECRET || process.env.JWT_REFRESH_SECRET === process.env.JWT_SECRET)) {
  throw new Error('In production, please set a separate JWT_REFRESH_SECRET that is different from JWT_SECRET');
}

if (!REFRESH_SECRET) {
  throw new Error('Missing JWT_REFRESH_SECRET or JWT_SECRET (refresh token secret) in environment');
}

// JWT payload for access tokens
export type JwtUser = {
  sub: string;
  email: string;
  role: UserRole;
};

// JWT payload for refresh tokens
export type JwtRefresh = {
  sub: string;
  jti: string;
};

// Small parser: supports '15m', '30d', '3600s' etc -> milliseconds
function parseDurationToMs(d: string): number {
  if (!d) return 0;
  const s = String(d).trim();
  const m = /^([0-9]+)(s|m|h|d)$/.exec(s);
  if (!m) {
    // try numeric days
    const n = Number(s);
    if (!Number.isFinite(n)) return 0;
    return n * 24 * 60 * 60 * 1000;
  }
  const v = Number(m[1]);
  switch (m[2]) {
    case 's': return v * 1000;
    case 'm': return v * 60 * 1000;
    case 'h': return v * 60 * 60 * 1000;
    case 'd': return v * 24 * 60 * 60 * 1000;
    default: return v * 1000;
  }
}

// Sign a short-lived access token
export function signAccessToken(user: { id: string; email: string; role: UserRole }): string {
  const payload: JwtUser = { sub: user.id, email: user.email, role: user.role };
  const opts: SignOptions = {
    algorithm: 'HS256',
    expiresIn: DEFAULT_ACCESS_EXPIRES as SignOptions['expiresIn'],
  };
  return jwt.sign(payload, ACCESS_SECRET as Secret, opts);
}

// Sign a refresh token, persist a hashed record with device info and return opaque token
export async function signRefreshToken(userId: string, deviceId: string, deviceName?: string): Promise<string> {
  const randomPart = crypto.randomBytes(32).toString('hex');
  const token = `${userId}::${deviceId}::${randomPart}`;
  
  const tokenHash = await (bcrypt as any).hash(token, 10);

  const now = new Date();
  const expiresAt = new Date(now.getTime() + parseDurationToMs(DEFAULT_REFRESH_EXPIRES));

  await RefreshTokenModel.findOneAndUpdate(
    { userId, deviceId },
    {
      $set: {
        userId,
        deviceId,
        deviceName,
        tokenHash,
        issuedAt: now,
        expiresAt,
        lastUsedAt: now,
      }
    },
    { upsert: true }
  );

  return token;
}

// Verify a refresh token using bcrypt.compare()
export async function verifyRefreshToken(token: string): Promise<{ userId: string, deviceId: string }> {
  const parts = token.split('::');
  if (parts.length !== 3) throw new Error('Invalid refresh token format');
  const [userId, deviceId] = parts;

  const doc = await RefreshTokenModel.findOne({ userId, deviceId }).lean();
  if (!doc) throw new Error('Refresh token not found');

  const ok = await (bcrypt as any).compare(token, doc.tokenHash);
  if (!ok) throw new Error('Invalid refresh token');

  // Update lastUsedAt
  await RefreshTokenModel.updateOne({ userId, deviceId }, { $set: { lastUsedAt: new Date() } });

  return { userId, deviceId };
}

// Revoke a refresh token for a specific device
export async function revokeTokenForDevice(userId: string, deviceId: string): Promise<void> {
  await RefreshTokenModel.deleteOne({ userId, deviceId });
}

// Revoke all tokens for a user
export async function revokeAllTokensForUser(userId: string): Promise<void> {
  await RefreshTokenModel.deleteMany({ userId });
}

// Rotate refresh token
export async function rotateRefreshToken(oldToken: string): Promise<string> {
  const { userId, deviceId } = await verifyRefreshToken(oldToken);
  const doc = await RefreshTokenModel.findOne({ userId, deviceId }).lean();
  return signRefreshToken(userId, deviceId, doc?.deviceName ?? undefined);
}

// Express middleware to require a valid access token
export type AuthedRequest = Request & { user?: JwtUser };
export function requireAuth(req: AuthedRequest, res: Response, next: NextFunction) {
  try {
    const header = String(req.headers.authorization || '');
    const token = header.startsWith('Bearer ') ? header.slice('Bearer '.length) : '';
    if (!token) {
      return res.status(401).json({ ok: false, error: 'Missing token' });
    }
    const decoded = jwt.verify(token, ACCESS_SECRET as Secret) as JwtUser;
    req.user = decoded;
    next();
  } catch (e) {
    return res.status(401).json({ ok: false, error: 'Invalid token' });
  }
}

// Account lockout helpers (Redis-backed; falls back to in-memory)
const _inMemAttempts = new Map<string, number>();  // email -> failed count
const _inMemLocked = new Map<string, number>();    // email -> lockedUntilMs

const _lockoutKey = (email: string) => `lockout:${email.toLowerCase()}`;
const _attemptsKey = (email: string) => `lockout_att:${email.toLowerCase()}`;
const _lockoutMax = () => Number(process.env.LOCKOUT_MAX_ATTEMPTS ?? '5');
const _lockoutWin = () => Number(process.env.LOCKOUT_WINDOW_SECONDS ?? '900');
const _lockoutDur = () => Number(process.env.LOCKOUT_DURATION_SECONDS ?? '1800');

export async function isAccountLocked(email: string): Promise<boolean> {
  if (redisClient) {
    const v = await redisClient.get(_lockoutKey(email)).catch(() => null);
    return v !== null;
  }
  const key = email.toLowerCase();
  const until = _inMemLocked.get(key);
  if (!until) return false;
  if (Date.now() > until) { _inMemLocked.delete(key); _inMemAttempts.delete(key); return false; }
  return true;
}

export async function recordFailedLogin(email: string): Promise<void> {
  const key = email.toLowerCase();
  if (redisClient) {
    const attKey = _attemptsKey(key);
    const count = await redisClient.incr(attKey).catch(() => 1);
    if (count === 1) await redisClient.expire(attKey, _lockoutWin()).catch(() => {});
    if (count >= _lockoutMax()) {
      await redisClient.set(_lockoutKey(key), '1', 'EX', _lockoutDur()).catch(() => {});
      await redisClient.del(attKey).catch(() => {});
    }
    return;
  }
  const count = (_inMemAttempts.get(key) ?? 0) + 1;
  _inMemAttempts.set(key, count);
  if (count >= _lockoutMax()) {
    _inMemLocked.set(key, Date.now() + _lockoutDur() * 1000);
    _inMemAttempts.delete(key);
  }
}

export async function clearLockout(email: string): Promise<void> {
  const key = email.toLowerCase();
  if (redisClient) {
    await redisClient.del(_lockoutKey(key), _attemptsKey(key)).catch(() => {});
    return;
  }
  _inMemLocked.delete(key);
  _inMemAttempts.delete(key);
}

// Export alias used elsewhere
export const verifyTokenMiddleware = requireAuth;

// Issue access + refresh tokens together (used after login/register)
export async function issueTokens(userId: string, email: string, role: UserRole, deviceId: string, deviceName?: string) {
  const access = signAccessToken({ id: userId, email, role });
  const refreshToken = await signRefreshToken(userId, deviceId, deviceName);
  return { accessToken: access, refreshToken };
}

// Refresh endpoint helper: exchange refresh token for a new access token (does not rotate)
export async function refreshTokenHandler(req: Request, res: Response) {
  const { token } = req.body;
  try {
    const payload = await verifyRefreshToken(token);
    const newAccess = jwt.sign({ sub: payload.userId }, ACCESS_SECRET as Secret, { expiresIn: DEFAULT_ACCESS_EXPIRES as SignOptions['expiresIn'] });
    res.json({ jwt: newAccess });
  } catch (err: any) {
    res.status(401).send(String(err?.message || 'Invalid refresh token'));
  }
}

// Logout helper: revoke refresh token by token string
export async function logoutHandler(req: Request, res: Response) {
  const { token } = req.body;
  try {
    const parts = token.split('::');
    if (parts.length === 3) {
      const [userId, deviceId] = parts;
      await revokeTokenForDevice(userId, deviceId);
    }
  } catch (e) {
    // ignore invalid token on logout
  }
  res.sendStatus(200);
}
