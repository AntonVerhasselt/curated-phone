import { cronJobs } from 'convex/server';
import { internalMutation } from './_generated/server';
import { components, internal } from './_generated/api';
const crons = cronJobs();
crons.interval('Purge OTP email contents and expired pairing codes', { hours: 1 }, internal.crons.cleanup);
export const cleanup = internalMutation({ args: {}, handler: async ctx => {
  await ctx.scheduler.runAfter(0, components.resend.lib.cleanupOldEmails, { olderThan: 86_400_000 });
  await ctx.scheduler.runAfter(0, components.resend.lib.cleanupAbandonedEmails, { olderThan: 7 * 86_400_000 });
  for (const p of await ctx.db.query('pairingCodes').take(100)) if (p.expiresAt < Date.now() - 86_400_000) await ctx.db.delete(p._id);
  for (const c of await ctx.db.query('otpChallenges').take(100)) if (c.expiresAt < Date.now() - 7 * 86_400_000) await ctx.db.delete(c._id);
} });
export default crons;
