"use node";
import { internalAction } from './_generated/server';
import { components } from './_generated/api';
import { v } from 'convex/values';
import { Resend } from '@convex-dev/resend';
export const guardianChanged = internalAction({ args: { emails: v.array(v.string()), requestId: v.string(), primaryEmail: v.string(), backupEmail: v.string() }, handler: async (ctx, args) => {
  if (!process.env.EMAIL_FROM || !process.env.RESEND_API_KEY) return;
  const mail = new Resend(components.resend, { testMode: process.env.RESEND_TEST_MODE !== 'false' });
  for (const address of new Set(args.emails)) await mail.sendEmail(ctx, { from: process.env.EMAIL_FROM, to: address, subject: 'Curated Phone: guardian address changed', text: `Request ${args.requestId} was authorized and its guardian addresses have been updated.\nPrimary: ${args.primaryEmail}\nRecovery: ${args.backupEmail}\nThis notification grants no additional approval powers. Native enforcement remains a separate application step.` });
} });
