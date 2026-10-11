import { httpRouter } from 'convex/server';
import { httpAction } from './_generated/server';
import { internal } from './_generated/api';
import { resend } from './email';
const http = httpRouter();
http.route({ path: '/health', method: 'GET', handler: httpAction(async () => Response.json({ status: 'ok', service: 'curated-phone', schemaVersion: 1 })) });
const handler = httpAction(async (ctx, req) => {
  try {
    if (Number(req.headers.get('content-length') ?? 0) > 65_536) return Response.json({ error: 'Request too large' }, { status: 413 });
    const raw = req.method === 'GET' ? '{}' : await req.text();
    if (raw.length > 65_536) return Response.json({ error: 'Request too large' }, { status: 413 });
    const body = JSON.parse(raw);
    if (body === null || typeof body !== 'object' || Array.isArray(body)) throw new Error('Invalid request');
    const result = await ctx.runAction(internal.cryptoActions.dispatch, { path: new URL(req.url).pathname, token: req.headers.get('authorization')?.replace(/^Bearer /, '') ?? '', body });
    return Response.json(result, { headers: { 'Cache-Control': 'no-store' } });
  } catch (error) {
    const message = error instanceof Error ? error.message : 'Request failed';
    const unauthorized = message.includes('Unauthorized');
    // Never return Convex stack traces, environment values, or provider errors.
    const safe = ['Invalid pairing code', 'Request not found', 'Hourly verification limit reached', 'Hourly email limit reached', 'Challenge already verified or resend is too soon', 'Finish applying the previous approval first', 'Stale versions: sync and review again'];
    return Response.json({ error: unauthorized ? 'Unauthorized' : safe.find(s => message.includes(s)) ?? 'Request rejected. Check configuration, sync, and review the request.' }, { status: unauthorized ? 401 : 400, headers: { 'Cache-Control': 'no-store' } });
  }
});
http.route({ path: '/device/state', method: 'GET', handler });
for (const path of ['/device/pair', '/requests', '/requests/verify', '/requests/resend', '/requests/cancel', '/device/applied', '/device/feed-status']) http.route({ path, method: 'POST', handler });
http.route({ path: '/resend-webhook', method: 'POST', handler: httpAction(async (ctx, req) => {
  if (!process.env.RESEND_WEBHOOK_SECRET) return new Response('Webhook not configured', { status: 503 });
  return await resend.handleResendEventWebhook(ctx, req);
}) });
export default http;
