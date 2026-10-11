import { Resend, vOnEmailEventArgs } from '@convex-dev/resend';
import { components, internal } from './_generated/api';
import { internalMutation } from './_generated/server';
export const resend: Resend = new Resend(components.resend, { testMode: process.env.RESEND_TEST_MODE !== 'false', onEmailEvent: internal.email.onEvent });
export const onEvent = internalMutation({ args: vOnEmailEventArgs, handler: async (ctx, { id, event }) => {
  const challenges = await ctx.db.query('otpChallenges').filter(q => q.eq(q.field('emailJobId'), id)).collect();
  for (const c of challenges) await ctx.db.patch(c._id, { delivery: event.type });
} });
