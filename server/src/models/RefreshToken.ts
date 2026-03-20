// TypeScript
import mongoose from 'mongoose';

// Store refresh tokens with hashed token and expiration; expiresAt is a Date so we can add a TTL index
const RefreshTokenSchema = new mongoose.Schema(
  {
    userId: { type: mongoose.Schema.Types.ObjectId, ref: 'User', required: true, index: true },
    deviceId: { type: String, required: true },
    deviceName: { type: String },
    tokenHash: { type: String, required: true },
    issuedAt: { type: Date, required: true },
    expiresAt: { type: Date, required: true, index: true },
    lastUsedAt: { type: Date },
  },
  { timestamps: false, versionKey: false }
);

RefreshTokenSchema.index({ userId: 1, deviceId: 1 }, { unique: true });
// TTL index: expire documents when expiresAt <= now
RefreshTokenSchema.index({ expiresAt: 1 }, { expireAfterSeconds: 0 });

export const RefreshTokenModel = mongoose.model('RefreshToken', RefreshTokenSchema);
export type RefreshTokenDoc = mongoose.Document & {
  userId: string;
  deviceId: string;
  deviceName?: string;
  tokenHash: string;
  issuedAt: Date;
  expiresAt: Date;
  lastUsedAt?: Date;
};
