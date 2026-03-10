import WebSocket from "ws";
import jwt from "jsonwebtoken";

const DEFAULT_WS_URL = "ws://localhost:18081/ws/im";
const DEFAULT_CORE_BASE_URL = "http://localhost:18082";

function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

function parseArgs(argv) {
  const out = { _: [] };
  for (let i = 0; i < argv.length; i++) {
    const a = argv[i];
    if (!a.startsWith("--")) {
      out._.push(a);
      continue;
    }
    const key = a.slice(2);
    const next = argv[i + 1];
    if (next == null || next.startsWith("--")) {
      out[key] = true;
      continue;
    }
    out[key] = next;
    i++;
  }
  return out;
}

function usage() {
  // keep it minimal and copy-paste friendly
  console.log(`im-load

Usage:
  node src/index.mjs connect-only [--connections N] [--startUserId N] [--durationSec N] [--wsUrl URL] [--jwtSecret SECRET] [--slowConsumerPct N]
  node src/index.mjs private      [--connections N] [--startUserId N] [--durationSec N] [--wsUrl URL] [--coreBaseUrl URL] [--jwtSecret SECRET]
                                 [--sendPerConnPerSec N] [--reconnectEverySec N] [--slowConsumerPct N]

Defaults:
  --wsUrl        ${DEFAULT_WS_URL}
  --coreBaseUrl  ${DEFAULT_CORE_BASE_URL}
  --durationSec  600
  --connections  1000
  --startUserId  1
  --sendPerConnPerSec  0
  --reconnectEverySec  0
  --slowConsumerPct    0

JWT secret:
  Pass --jwtSecret or export JWT_HMAC_SECRET. Must be >= 32 chars.
`);
}

function requiredSecret(raw) {
  const secret = String(raw ?? "").trim();
  if (secret.length < 32) {
    throw new Error("JWT secret too short (need >= 32 chars). Provide --jwtSecret or JWT_HMAC_SECRET.");
  }
  return secret;
}

function tokenForUser(secret, userId) {
  return jwt.sign(
    {},
    secret,
    {
      algorithm: "HS256",
      subject: String(userId),
      expiresIn: "2h",
    },
  );
}

function conversationId(a, b) {
  const small = Math.min(a, b);
  const large = Math.max(a, b);
  return `${small}_${large}`;
}

function pickSlow(userId, slowConsumerPct) {
  if (slowConsumerPct <= 0) return false;
  // stable-ish hash so runs are comparable
  const h = (userId * 1103515245 + 12345) >>> 0;
  return (h % 100) < slowConsumerPct;
}

function createHistogram() {
  const buckets = [1, 2, 5, 10, 20, 50, 100, 200, 500, 1000, 2000, 5000, 10000];
  const counts = new Array(buckets.length + 1).fill(0);
  let total = 0;
  let sum = 0;
  let min = Number.POSITIVE_INFINITY;
  let max = 0;

  function observe(ms) {
    const v = Math.max(0, Math.floor(ms));
    total += 1;
    sum += v;
    min = Math.min(min, v);
    max = Math.max(max, v);
    let idx = buckets.findIndex((b) => v <= b);
    if (idx < 0) idx = buckets.length;
    counts[idx] += 1;
  }

  function snapshot() {
    const avg = total === 0 ? 0 : Math.round(sum / total);
    const lines = [];
    let acc = 0;
    for (let i = 0; i < counts.length; i++) {
      acc += counts[i];
      const upper = i < buckets.length ? `${buckets[i]}ms` : "inf";
      lines.push(`  <=${upper}: ${counts[i]}`);
    }
    return {
      total,
      min: total === 0 ? 0 : min,
      max,
      avg,
      lines,
    };
  }

  return { observe, snapshot };
}

async function runConnectOnly(opts) {
  const wsUrl = String(opts.wsUrl ?? DEFAULT_WS_URL);
  const durationSec = Number(opts.durationSec ?? 600);
  const connections = Number(opts.connections ?? 1000);
  const startUserId = Number(opts.startUserId ?? 1);
  const slowConsumerPct = Number(opts.slowConsumerPct ?? 0);
  const secret = requiredSecret(opts.jwtSecret ?? process.env.JWT_HMAC_SECRET);

  const states = new Map();
  let connected = 0;
  let authed = 0;
  let closed = 0;
  let authErrors = 0;

  const endAt = Date.now() + durationSec * 1000;

  function connectOne(userId) {
    const token = tokenForUser(secret, userId);
    const ws = new WebSocket(wsUrl, { perMessageDeflate: false, handshakeTimeout: 10_000 });
    const slow = pickSlow(userId, slowConsumerPct);

    const state = {
      userId,
      ws,
      token,
      slow,
      authed: false,
    };
    states.set(userId, state);

    ws.on("open", () => {
      connected += 1;
      ws.send(JSON.stringify({ type: "auth", accessToken: token }));
    });

    ws.on("message", (buf) => {
      let msg;
      try {
        msg = JSON.parse(buf.toString("utf8"));
      } catch {
        return;
      }
      const type = String(msg?.type ?? "");
      if (type === "auth_ok") {
        if (!state.authed) {
          state.authed = true;
          authed += 1;
        }
        if (state.slow && ws?._socket?.pause) {
          try {
            ws._socket.pause();
          } catch {
            // best-effort
          }
        }
      } else if (type === "auth_error") {
        authErrors += 1;
      }
    });

    ws.on("close", () => {
      closed += 1;
    });

    ws.on("error", () => {
      // ignore: close will count
    });
  }

  for (let i = 0; i < connections; i++) {
    connectOne(startUserId + i);
    // gentle ramp
    if (i % 200 === 0) {
      // eslint-disable-next-line no-await-in-loop
      await sleep(10);
    }
  }

  while (Date.now() < endAt) {
    await sleep(1000);
    console.log(`[connect-only] connected=${connected} authed=${authed} closed=${closed} authErrors=${authErrors}`);
  }

  for (const s of states.values()) {
    try {
      s.ws.close();
    } catch {
      // ignore
    }
  }
  await sleep(500);
  console.log(`[connect-only] done. connected=${connected} authed=${authed} closed=${closed} authErrors=${authErrors}`);
}

async function runPrivate(opts) {
  const wsUrl = String(opts.wsUrl ?? DEFAULT_WS_URL);
  const coreBaseUrl = String(opts.coreBaseUrl ?? DEFAULT_CORE_BASE_URL);
  const durationSec = Number(opts.durationSec ?? 600);
  const connections = Number(opts.connections ?? 1000);
  const startUserId = Number(opts.startUserId ?? 1);
  const sendPerConnPerSec = Number(opts.sendPerConnPerSec ?? 0);
  const reconnectEverySec = Number(opts.reconnectEverySec ?? 0);
  const slowConsumerPct = Number(opts.slowConsumerPct ?? 0);
  const secret = requiredSecret(opts.jwtSecret ?? process.env.JWT_HMAC_SECRET);

  const endAt = Date.now() + durationSec * 1000;
  const latency = createHistogram();

  const states = new Map();
  const authedUsers = [];
  let connected = 0;
  let authed = 0;
  let closed = 0;
  let authErrors = 0;
  let sent = 0;
  let recvPrivate = 0;
  let backfillCalls = 0;

  function pickRandomAuthed() {
    if (authedUsers.length === 0) return null;
    const idx = Math.floor(Math.random() * authedUsers.length);
    const userId = authedUsers[idx];
    return states.get(userId) ?? null;
  }

  async function backfillConversation(state) {
    if (!state?.conversationId) return;
    const afterSeq = state.lastSeenSeq ?? 0;
    const url = `${coreBaseUrl}/api/im/conversations/${encodeURIComponent(state.conversationId)}/messages?afterSeq=${afterSeq}&limit=200`;
    try {
      backfillCalls += 1;
      const res = await fetch(url, {
        method: "GET",
        headers: {
          Authorization: `Bearer ${state.token}`,
        },
      });
      if (!res.ok) return;
      const body = await res.json();
      const items = Array.isArray(body?.items) ? body.items : [];
      if (items.length > 0) {
        const last = items[items.length - 1];
        const seq = Number(last?.seq ?? 0);
        if (seq > (state.lastSeenSeq ?? 0)) {
          state.lastSeenSeq = seq;
        }
      }
    } catch {
      // ignore
    }
  }

  function connectOne(userId) {
    const peerUserId = startUserId + ((userId - startUserId + 1) % connections);
    const cId = conversationId(userId, peerUserId);
    const token = tokenForUser(secret, userId);
    const slow = pickSlow(userId, slowConsumerPct);

    const state = {
      userId,
      peerUserId,
      conversationId: cId,
      token,
      ws: null,
      authed: false,
      slow,
      sendSeq: 0,
      lastSeenSeq: 0,
    };
    states.set(userId, state);

    const ws = new WebSocket(wsUrl, { perMessageDeflate: false, handshakeTimeout: 10_000 });
    state.ws = ws;

    ws.on("open", () => {
      connected += 1;
      ws.send(JSON.stringify({ type: "auth", accessToken: token }));
    });

    ws.on("message", async (buf) => {
      let msg;
      try {
        msg = JSON.parse(buf.toString("utf8"));
      } catch {
        return;
      }
      const type = String(msg?.type ?? "");
      if (type === "auth_ok") {
        if (!state.authed) {
          state.authed = true;
          authed += 1;
          authedUsers.push(userId);
        }
        if (state.slow && ws?._socket?.pause) {
          try {
            ws._socket.pause();
          } catch {
            // best-effort
          }
        }
        if (reconnectEverySec > 0) {
          // backfill after (re)auth
          await backfillConversation(state);
        }
        return;
      }
      if (type === "auth_error") {
        authErrors += 1;
        return;
      }
      if (type === "privateMessage") {
        recvPrivate += 1;
        const seq = Number(msg?.seq ?? 0);
        if (seq > state.lastSeenSeq) {
          state.lastSeenSeq = seq;
        }
        const content = String(msg?.content ?? "");
        const m = /ts=(\d+)/.exec(content);
        if (m) {
          const sentAt = Number(m[1]);
          if (Number.isFinite(sentAt) && sentAt > 0) {
            latency.observe(Date.now() - sentAt);
          }
        }
      }
    });

    ws.on("close", () => {
      closed += 1;
      if (Date.now() >= endAt) return;
      if (reconnectEverySec <= 0) return;
      // reconnect with some jitter
      const jitterMs = Math.floor(Math.random() * 200);
      setTimeout(() => connectOne(userId), jitterMs);
    });

    ws.on("error", () => {
      // ignore: close will count
    });
  }

  for (let i = 0; i < connections; i++) {
    connectOne(startUserId + i);
    if (i % 200 === 0) {
      // eslint-disable-next-line no-await-in-loop
      await sleep(10);
    }
  }

  // send loop (global credit-based)
  const tickMs = 100;
  let credit = 0;
  const sendTimer = setInterval(() => {
    if (Date.now() >= endAt) return;
    if (sendPerConnPerSec <= 0) return;
    if (authedUsers.length === 0) return;

    credit += (sendPerConnPerSec * authedUsers.length * tickMs) / 1000;
    const n = Math.floor(credit);
    credit -= n;
    for (let i = 0; i < n; i++) {
      const s = pickRandomAuthed();
      if (!s?.ws || s.ws.readyState !== WebSocket.OPEN) continue;
      s.sendSeq += 1;
      const clientMsgId = `cmsg-${s.userId}-${s.sendSeq}`;
      const content = `hello ts=${Date.now()} from=${s.userId} seq=${s.sendSeq}`;
      try {
        s.ws.send(JSON.stringify({
          type: "sendPrivateText",
          clientMsgId,
          toUserId: s.peerUserId,
          content,
        }));
        sent += 1;
      } catch {
        // ignore
      }
    }
  }, tickMs);

  // reconnect loop
  const reconnectTimer = reconnectEverySec > 0 ? setInterval(() => {
    if (Date.now() >= endAt) return;
    // close ~1% connections per tick to avoid spikes
    const target = Math.max(1, Math.floor(connections * 0.01));
    for (let i = 0; i < target; i++) {
      const s = pickRandomAuthed();
      if (!s?.ws || s.ws.readyState !== WebSocket.OPEN) continue;
      try {
        s.ws.close();
      } catch {
        // ignore
      }
    }
  }, reconnectEverySec * 1000) : null;

  while (Date.now() < endAt) {
    await sleep(1000);
    const snap = latency.snapshot();
    console.log(`[private] connected=${connected} authed=${authed} closed=${closed} authErrors=${authErrors} sent=${sent} recvPrivate=${recvPrivate} backfillCalls=${backfillCalls} latency(avg=${snap.avg}ms min=${snap.min}ms max=${snap.max}ms n=${snap.total})`);
  }

  clearInterval(sendTimer);
  if (reconnectTimer) clearInterval(reconnectTimer);

  for (const s of states.values()) {
    try {
      s.ws?.close();
    } catch {
      // ignore
    }
  }
  await sleep(800);

  const snap = latency.snapshot();
  console.log(`[private] done. connected=${connected} authed=${authed} closed=${closed} authErrors=${authErrors} sent=${sent} recvPrivate=${recvPrivate} backfillCalls=${backfillCalls}`);
  console.log("[private] latency histogram:");
  for (const line of snap.lines) {
    console.log(line);
  }
}

async function main() {
  const argv = parseArgs(process.argv.slice(2));
  const cmd = argv._[0];

  if (argv.help || argv.h || !cmd) {
    usage();
    process.exit(cmd ? 0 : 1);
  }

  if (cmd === "connect-only") {
    await runConnectOnly(argv);
    return;
  }
  if (cmd === "private") {
    await runPrivate(argv);
    return;
  }

  console.error(`Unknown command: ${cmd}`);
  usage();
  process.exit(1);
}

main().catch((e) => {
  console.error(e?.stack ?? String(e));
  process.exit(1);
});

