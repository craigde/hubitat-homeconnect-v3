# Understanding Home Connect API Rate Limits (and How to Avoid Getting Stuck)

I've done a deep dive into how the Home Connect API rate limiting actually works, because the official documentation is sparse and the behavior isn't intuitive. Sharing my findings here since rate limiting is the #1 issue people run into with Home Connect integrations.

## The Rate Limits You Didn't Know About

Most people know about the 1,000 calls/day limit. But the Home Connect API actually enforces **multiple independent rate limits**:

| Limit | Type | Retry-After Header? |
|-------|------|---------------------|
| 10 requests/sec average (20 burst) | Leaky bucket | No |
| 50 calls in 1 minute | Window | Yes |
| 1,000 calls in 1 day | Window | Yes |
| 10 successive error calls in 10 minutes | Window | Yes |
| 5 start program calls in 1 minute | Window | Yes |

The per-second limit uses a leaky bucket algorithm (requests drain at a constant rate). The others use time windows with a "remaining period" countdown.

## The Daily Limit: Fixed Window, Not Sliding

The 1,000 calls/day limit uses a **fixed window**. When you hit it, the API returns:

> *"The rate limit '1000 calls in 1 day' was reached. Requests are blocked during the remaining period of XXXX seconds."*

The "remaining period" is a countdown to when the window resets — not a new 24-hour block from when you hit the limit. Users have reported remaining periods ranging from ~47 minutes to ~14 hours, which corresponds to how much time was left in the current window when they exhausted the quota.

You can verify this yourself: the `X-RateLimit-Remaining` header on every API response shows your remaining calls. When the window resets, this value jumps back up to 1,000.

## The Hidden Danger: "10 Successive Error Calls"

This is the rate limit that traps people for hours. Here's how it works:

1. You hit the daily limit (or any other error)
2. Your integration retries → gets a 429 back
3. That 429 counts as an **error call**
4. After 10 error calls in 10 minutes, you trigger **this** rate limit too
5. Now you're double-blocked
6. **Each subsequent retry is another error call that can extend this block**

This creates a vicious cycle: the integration keeps retrying, each retry extends the error-call block, and the "remaining period" grows larger and larger. This is why people report being locked out for 5, 10, or even 14+ hours — it's not the daily limit doing that, it's the error-call limit being continuously re-triggered.

**The only way to break the cycle is to stop making API calls entirely and wait.**

## How This Affects Multi-Device Setups

The daily limit is **per client per user account** — not per device. Whether you have 1 appliance or 5, you get the same 1,000 calls/day. This means more devices = faster you burn through the quota, especially during reconnection events.

A typical reconnect refreshes status, settings, and active program for each device:
- 1 device: ~3 API calls per reconnect
- 3 devices: ~9 API calls per reconnect
- 5 devices: ~15 API calls per reconnect

Under normal SSE streaming operation, this isn't a problem — events push to you in real time with zero API calls. But if your connection bounces repeatedly, those reconnect costs add up fast.

## Running Multiple Integrations: Don't

If you're running two Home Connect integrations (e.g., a legacy driver alongside a newer one), they **share the same API quota**. You're not doubling your capacity — you're doubling your consumption against the same 1,000-call limit.

Worse, if the legacy integration doesn't handle 429 errors properly, it will:
1. Keep retrying after hitting the limit
2. Trigger the "10 successive error calls" block
3. Extend that block indefinitely with continued retries
4. Prevent **both** integrations from recovering

This is the worst-case scenario: one integration's bad retry behavior holds the entire account hostage.

## What a Good Integration Should Do

To avoid rate limit problems, an integration should:

1. **Use SSE event streams** for real-time state updates instead of polling (0 API calls during normal operation)
2. **Track rate limit state locally** and block all outgoing API calls when rate limited — don't rely solely on the server rejecting them
3. **Parse the Retry-After header** to know exactly when the block expires
4. **Add a buffer** after the limit expires before reconnecting (the server may still reject requests right at the boundary)
5. **Avoid unnecessary refreshes** during connection bounces (if you just refreshed 30 seconds ago, don't refresh again)
6. **Monitor API usage** proactively using the `X-RateLimit-Remaining` header so you know you're approaching the limit before you hit it

## What v3 Does Differently

The Home Connect Integration v3 (v3.3.23) implements all of the above:

- **Local rate limit enforcement**: When a 429 is received, all API calls are blocked locally until the limit expires. This prevents the "10 successive error calls" death spiral.
- **SSE-based architecture**: During normal operation, device state updates come through the event stream with zero API calls. REST calls only happen on initial connect/reconnect.
- **Rate limit visibility**: The `rateLimitRemaining` attribute shows your remaining API calls in real time, and `connectionStatus` shows exactly when a rate limit will expire.
- **API usage tracking**: The `showApiUsage` command gives you a full breakdown of calls by method and endpoint category, so you can see exactly where your quota is going.
- **Connection bounce protection**: Prevents unnecessary device refreshes when the connection drops and reconnects in quick succession.
- **Auto-recovery**: Automatically schedules reconnection after the rate limit expires (plus a 5-minute buffer), with no manual intervention needed.

## Practical Tips

1. **Never run two Home Connect integrations simultaneously.** Remove the old one before installing a new one. They share the same quota and a misbehaving integration can block both.

2. **Check your API usage.** In v3, run the `showApiUsage` command on the Stream Driver to see your call breakdown. If you're consistently above 500 calls/day, investigate what's triggering so many calls.

3. **If you're rate limited, don't keep clicking Initialize.** Wait for the full expiry period. Each failed attempt can extend the block via the error-call limit.

4. **Reduce the number of things that trigger reconnects.** Hub reboots, network blips, and mode changes can all cause reconnection events. A stable network = fewer API calls.

5. **If you're stuck in a long block**, use the `clearRateLimit` command in v3 to clear the local tracking, but **wait for the actual server-side limit to expire first**. Clearing local state won't clear the server-side block.

---

*Sources: [Home Connect API Documentation](https://api-docs.home-connect.com/), [Homebridge Home Connect Error Reference](https://github.com/thoukydides/homebridge-homeconnect/wiki/Errors), [Home Assistant Community Discussion](https://community.home-assistant.io/t/home-connect-integration-rate-limit-of-1000-apicalls/327460)*
