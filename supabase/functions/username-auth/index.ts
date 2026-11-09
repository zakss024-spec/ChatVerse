const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "apikey, content-type, x-client-info",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
  "Content-Type": "application/json",
};

const supabaseUrl = Deno.env.get("SUPABASE_URL") ?? "";
const secretKey = readKey("SUPABASE_SECRET_KEYS") ?? Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "";
const publishableKey = readKey("SUPABASE_PUBLISHABLE_KEYS") ?? Deno.env.get("SUPABASE_ANON_KEY") ?? "";

function readKey(name: string): string | null {
  const value = Deno.env.get(name);
  if (!value) return null;
  try {
    const parsed = JSON.parse(value);
    return parsed.default ?? Object.values(parsed)[0] ?? null;
  } catch {
    return value;
  }
}

function json(data: unknown, status = 200, extraHeaders: Record<string, string> = {}) {
  return new Response(JSON.stringify(data), {
    status,
    headers: { ...corsHeaders, ...extraHeaders },
  });
}

function adminHeaders(contentType = false): Headers {
  const headers = new Headers({ apikey: secretKey });
  // New sb_secret keys must only be sent in apikey, never as a Bearer JWT.
  if (!secretKey.startsWith("sb_secret_")) {
    headers.set("Authorization", `Bearer ${secretKey}`);
  }
  if (contentType) headers.set("Content-Type", "application/json");
  return headers;
}

function authHeaders(): Headers {
  const headers = new Headers({
    apikey: publishableKey,
    "Content-Type": "application/json",
  });
  if (!publishableKey.startsWith("sb_publishable_")) {
    headers.set("Authorization", `Bearer ${publishableKey}`);
  }
  return headers;
}

async function sha256(value: string): Promise<string> {
  const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(value));
  return Array.from(new Uint8Array(digest), (byte) => byte.toString(16).padStart(2, "0")).join("");
}

async function consumeRateLimit(keyHash: string): Promise<boolean> {
  const response = await fetch(`${supabaseUrl}/rest/v1/rpc/consume_username_auth_attempt`, {
    method: "POST",
    headers: adminHeaders(true),
    body: JSON.stringify({ p_key_hash: keyHash }),
  });
  if (!response.ok) throw new Error("Rate limit service unavailable");
  const result = await response.json();
  const row = Array.isArray(result) ? result[0] : result;
  return row?.allowed === true;
}

async function clearRateLimits(keys: string[]) {
  if (!keys.length) return;
  const filter = keys.join(",");
  await fetch(
    `${supabaseUrl}/rest/v1/username_auth_rate_limits?key_hash=in.(${filter})`,
    { method: "DELETE", headers: adminHeaders() },
  );
}

async function findProfile(username: string) {
  const response = await fetch(
    `${supabaseUrl}/rest/v1/profiles?select=id,username&username=ilike.${encodeURIComponent(username)}&limit=1`,
    { headers: adminHeaders() },
  );
  if (!response.ok) throw new Error("Profile lookup failed");
  const rows = await response.json();
  return Array.isArray(rows) ? rows[0] ?? null : null;
}

async function deleteAuthUser(userId: string) {
  await fetch(`${supabaseUrl}/auth/v1/admin/users/${encodeURIComponent(userId)}`, {
    method: "DELETE",
    headers: adminHeaders(),
  });
}

async function createAccount(username: string, password: string) {
  const internalEmail = `${crypto.randomUUID()}@auth.chatverse.invalid`;
  const createResponse = await fetch(`${supabaseUrl}/auth/v1/admin/users`, {
    method: "POST",
    headers: adminHeaders(true),
    body: JSON.stringify({
      email: internalEmail,
      password,
      email_confirm: true,
      user_metadata: {
        username,
        display_name: username,
      },
    }),
  });

  const createdBody = await createResponse.json().catch(() => ({}));
  const createdUser = createdBody.user ?? createdBody;
  if (!createResponse.ok || !createdUser.id) {
    if (createResponse.status === 422 || createResponse.status === 409) {
      return { error: "Username sudah digunakan.", status: 409 };
    }
    return { error: "Akun belum dapat dibuat. Coba lagi nanti.", status: 503 };
  }

  // The database trigger is responsible for creating the profile and enforcing
  // case-insensitive username uniqueness. Verify its final value before success.
  const profileResponse = await fetch(
    `${supabaseUrl}/rest/v1/profiles?select=id,username&id=eq.${encodeURIComponent(createdUser.id)}&limit=1`,
    { headers: adminHeaders() },
  );
  const profileRows = profileResponse.ok ? await profileResponse.json().catch(() => []) : [];
  const profile = Array.isArray(profileRows) ? profileRows[0] : null;
  if (!profile || profile.username !== username) {
    await deleteAuthUser(createdUser.id);
    return { error: "Username sudah digunakan atau profil belum siap.", status: 409 };
  }

  return { email: internalEmail, status: 201 };
}

async function signIn(username: string, password: string) {
  const profile = await findProfile(username);
  if (!profile?.id) return null;

  const userResponse = await fetch(
    `${supabaseUrl}/auth/v1/admin/users/${encodeURIComponent(profile.id)}`,
    { headers: adminHeaders() },
  );
  if (!userResponse.ok) return null;
  const userBody = await userResponse.json().catch(() => ({}));
  const user = userBody.user ?? userBody;
  if (!user.email) return null;

  const signInResponse = await fetch(
    `${supabaseUrl}/auth/v1/token?grant_type=password`,
    {
      method: "POST",
      headers: authHeaders(),
      body: JSON.stringify({ email: user.email, password }),
    },
  );
  if (!signInResponse.ok) return null;
  return await signInResponse.json();
}

Deno.serve(async (request: Request) => {
  if (request.method === "OPTIONS") {
    return new Response("ok", { headers: corsHeaders });
  }
  if (request.method !== "POST") {
    return json({ error: "Method not allowed." }, 405);
  }
  if (!supabaseUrl || !secretKey || !publishableKey) {
    return json({ error: "Authentication service is not configured." }, 503);
  }
  // The Edge gateway's JWT verification is disabled for this public login
  // endpoint. Require the exact project publishable key before doing any work.
  if (request.headers.get("apikey") !== publishableKey) {
    return json({ error: "Unauthorized." }, 401);
  }

  try {
    const body = await request.json();
    const action = body?.action === "register" ? "register" : body?.action === "login" ? "login" : "";
    const username = String(body?.username ?? "").trim().toLowerCase();
    const password = String(body?.password ?? "");

    if (!action) return json({ error: "Permintaan tidak valid." }, 400);
    if (!/^[a-z0-9_.]{3,30}$/.test(username)) {
      return json({ error: "Username harus 3–30 karakter: huruf, angka, titik, atau garis bawah." }, 400);
    }
    if (password.length < 8 || password.length > 128) {
      return json({ error: "Password harus 8–128 karakter." }, 400);
    }

    const forwardedIp = request.headers.get("cf-connecting-ip")
      ?? request.headers.get("x-real-ip")
      ?? request.headers.get("x-forwarded-for")?.split(",")[0]?.trim()
      ?? "";
    const rateKeys = [
      await sha256(`username:${username}`),
      ...(forwardedIp ? [await sha256(`ip:${forwardedIp}`), await sha256(`pair:${forwardedIp}:${username}`)] : []),
    ];

    const limits = await Promise.all(rateKeys.map(consumeRateLimit));
    if (limits.some((allowed) => !allowed)) {
      return json(
        { error: "Terlalu banyak percobaan. Coba lagi dalam 15 menit." },
        429,
        { "Retry-After": "900" },
      );
    }

    if (action === "register") {
      const existing = await findProfile(username);
      if (existing) return json({ error: "Username sudah digunakan." }, 409);

      const created = await createAccount(username, password);
      if (!created.email) return json({ error: created.error }, created.status);

      const session = await fetch(
        `${supabaseUrl}/auth/v1/token?grant_type=password`,
        {
          method: "POST",
          headers: authHeaders(),
          body: JSON.stringify({ email: created.email, password }),
        },
      );
      if (!session.ok) {
        return json({ error: "Akun berhasil dibuat, tetapi sesi belum dapat dibuka. Silakan login." }, 503);
      }
      const sessionBody = await session.json();
      await clearRateLimits(rateKeys);
      return json({
        access_token: sessionBody.access_token,
        refresh_token: sessionBody.refresh_token,
        expires_in: sessionBody.expires_in,
        token_type: sessionBody.token_type,
      }, 201);
    }

    const sessionBody = await signIn(username, password);
    if (!sessionBody?.access_token || !sessionBody?.refresh_token) {
      return json({ error: "Username atau password salah." }, 401);
    }

    await clearRateLimits(rateKeys);
    return json({
      access_token: sessionBody.access_token,
      refresh_token: sessionBody.refresh_token,
      expires_in: sessionBody.expires_in,
      token_type: sessionBody.token_type,
    });
  } catch {
    return json({ error: "Layanan autentikasi sedang bermasalah. Coba lagi nanti." }, 503);
  }
});
