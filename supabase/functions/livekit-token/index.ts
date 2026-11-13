import { createClient } from "npm:@supabase/supabase-js@2";
import { AccessToken } from "npm:livekit-server-sdk@2.19.3";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "apikey, authorization, content-type, x-client-info",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
  "Content-Type": "application/json",
};

function json(data: unknown, status = 200) {
  return new Response(JSON.stringify(data), { status, headers: corsHeaders });
}

function readPublishableKey(): string {
  const raw = Deno.env.get("SUPABASE_PUBLISHABLE_KEYS")
    ?? Deno.env.get("SUPABASE_PUBLISHABLE_KEY")
    ?? Deno.env.get("SUPABASE_ANON_KEY")
    ?? "";
  if (!raw) return "";
  try {
    const parsed = JSON.parse(raw);
    const value = parsed.default ?? Object.values(parsed)[0];
    return typeof value === "string" ? value : "";
  } catch {
    return raw;
  }
}

Deno.serve(async (request: Request) => {
  if (request.method === "OPTIONS") {
    return new Response("ok", { headers: corsHeaders });
  }
  if (request.method !== "POST") {
    return json({ error: "Method not allowed." }, 405);
  }

  const supabaseUrl = Deno.env.get("SUPABASE_URL") ?? "";
  const publishableKey = readPublishableKey();
  const livekitUrl = Deno.env.get("LIVEKIT_URL") ?? "";
  const livekitApiKey = Deno.env.get("LIVEKIT_API_KEY") ?? "";
  const livekitApiSecret = Deno.env.get("LIVEKIT_API_SECRET") ?? "";

  if (!supabaseUrl || !publishableKey || !livekitUrl || !livekitApiKey || !livekitApiSecret) {
    return json({ error: "Layanan panggilan belum dikonfigurasi." }, 503);
  }

  const suppliedApiKey = request.headers.get("apikey") ?? "";
  if (suppliedApiKey !== publishableKey) {
    return json({ error: "Unauthorized." }, 401);
  }

  const authorization = request.headers.get("authorization") ?? "";
  const accessToken = authorization.startsWith("Bearer ")
    ? authorization.slice("Bearer ".length).trim()
    : "";
  if (!accessToken) return json({ error: "Sesi login diperlukan." }, 401);

  try {
    const body = await request.json();
    const callId = String(body?.call_id ?? "").trim();
    if (!/^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(callId)) {
      return json({ error: "ID panggilan tidak valid." }, 400);
    }

    // Use the caller's JWT so the call_sessions RLS policy remains in force.
    const supabase = createClient(supabaseUrl, publishableKey, {
      global: { headers: { Authorization: `Bearer ${accessToken}` } },
      auth: { persistSession: false, autoRefreshToken: false },
    });

    const { data: userData, error: userError } = await supabase.auth.getUser(accessToken);
    const user = userData?.user;
    if (userError || !user) return json({ error: "Sesi login tidak valid." }, 401);

    const { data: call, error: callError } = await supabase
      .from("call_sessions")
      .select("id, conversation_id, caller_id, callee_id, call_type, status")
      .eq("id", callId)
      .maybeSingle();

    if (callError || !call) return json({ error: "Panggilan tidak ditemukan." }, 404);
    if (call.caller_id !== user.id && call.callee_id !== user.id) {
      return json({ error: "Akun ini bukan peserta panggilan." }, 403);
    }
    if (call.status !== "ringing" && call.status !== "accepted") {
      return json({ error: "Panggilan sudah tidak aktif." }, 409);
    }

    // The call UUID is a non-PII, unique room name shared by both participants.
    const roomName = `chatverse-call-${call.id}`;
    const token = new AccessToken(livekitApiKey, livekitApiSecret, {
      identity: user.id,
      name: user.id,
      ttl: "15m",
    });
    token.addGrant({
      roomJoin: true,
      room: roomName,
      canPublish: true,
      canSubscribe: true,
      canPublishData: false,
    });

    return json({
      server_url: livekitUrl,
      participant_token: await token.toJwt(),
      room_name: roomName,
      call_type: call.call_type,
      expires_in: 900,
    });
  } catch {
    return json({ error: "Token panggilan belum dapat dibuat." }, 503);
  }
});
