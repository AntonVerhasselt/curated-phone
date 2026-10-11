import { internalMutation } from './_generated/server';
import { v } from 'convex/values';
// Operator-only cleanup for unapproved automated transport fixtures. Never a phone endpoint.
export const discardTransportFixture = internalMutation({ args: { deviceId: v.string() }, handler: async (ctx, a) => {
  const d = await ctx.db.query('devices').withIndex('by_device', q => q.eq('deviceId', a.deviceId)).unique();
  if (!d || !d.label.startsWith('AUTOMATED TRANSPORT FIXTURE') || d.approvedPolicyVersion !== 0) throw new Error('Only unapproved transport fixtures may be deleted');
  for (const r of await ctx.db.query('changeRequests').withIndex('by_device', q => q.eq('deviceId', d.deviceId)).collect()) {
    for (const c of await ctx.db.query('otpChallenges').withIndex('by_request', q => q.eq('requestId', r.requestId)).collect()) await ctx.db.delete(c._id);
    await ctx.db.delete(r._id);
  }
  for (const g of await ctx.db.query('guardians').withIndex('by_device', q => q.eq('deviceId', d.deviceId)).collect()) await ctx.db.delete(g._id);
  for (const event of await ctx.db.query('auditEvents').withIndex('by_device', q => q.eq('deviceId', d.deviceId)).collect()) await ctx.db.delete(event._id);
  await ctx.db.delete(d._id);
} });
