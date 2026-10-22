import { createClient } from "npm:@supabase/supabase-js@2";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers":
    "authorization, apikey, content-type, x-client-info",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
  "Content-Type": "application/json; charset=utf-8",
};

function jsonResponse(
  status: number,
  payload: Record<string, unknown>,
): Response {
  return new Response(JSON.stringify(payload), {
    status,
    headers: corsHeaders,
  });
}

Deno.serve(async (req: Request) => {
  if (req.method === "OPTIONS") {
    return new Response("ok", { status: 200, headers: corsHeaders });
  }

  if (req.method !== "POST") {
    return jsonResponse(405, { error: "Method not allowed" });
  }

  const authorization = req.headers.get("Authorization");

  if (!authorization?.startsWith("Bearer ")) {
    return jsonResponse(401, { error: "Authentication required" });
  }

  const supabaseUrl = Deno.env.get("SUPABASE_URL");
  const supabaseKey =
    Deno.env.get("SUPABASE_ANON_KEY") ??
    Deno.env.get("SUPABASE_PUBLISHABLE_KEY");

  const turnKeyId = Deno.env.get("CLOUDFLARE_TURN_KEY_ID");
  const turnApiToken = Deno.env.get("CLOUDFLARE_TURN_API_TOKEN");

  if (!supabaseUrl || !supabaseKey || !turnKeyId || !turnApiToken) {
    console.error("TURN credential service configuration is incomplete.");

    return jsonResponse(500, {
      error: "TURN credential service is not configured",
    });
  }

  const supabase = createClient(supabaseUrl, supabaseKey, {
    global: {
      headers: {
        Authorization: authorization,
      },
    },
    auth: {
      persistSession: false,
      autoRefreshToken: false,
    },
  });

  try {
    const { data: authData, error: authError } =
      await supabase.auth.getUser();

    if (authError || !authData.user) {
      return jsonResponse(401, { error: "Invalid or expired session" });
    }

    let body: { call_id?: unknown };
    try {
      body = await req.json();
    } catch {
      return jsonResponse(400, { error: "Expected a JSON body" });
    }
    if (body.call_id !== undefined && typeof body.call_id !== "string") {
      return jsonResponse(400, { error: "call_id must be a UUID" });
    }
    const callId = typeof body.call_id === "string" ? body.call_id : "";
    if (callId && !/^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(callId)) {
      return jsonResponse(400, { error: "call_id must be a UUID" });
    }

    const activeStatuses = ["ringing", "accepted", "connecting", "connected"];
    // Current clients identify the exact call. During rollout, older clients
    // that send an empty body are limited to an active call visible under RLS.
    let callQuery = supabase.from("call_sessions").select("id, status");
    if (callId) {
      callQuery = callQuery.eq("id", callId);
    } else {
      callQuery = callQuery
        .or(`caller_id.eq.${authData.user.id},callee_id.eq.${authData.user.id}`)
        .in("status", activeStatuses)
        .limit(1);
    }
    const { data: call, error: callError } = await callQuery.maybeSingle();
    if (callError) {
      console.error("Call authorization lookup failed:", callError.message);
      return jsonResponse(500, { error: "Could not authorize TURN credentials" });
    }
    if (!call) {
      return jsonResponse(403, { error: "An active call participant session is required" });
    }
    if (!activeStatuses.includes(call.status)) {
      return jsonResponse(409, { error: "Call is no longer active" });
    }

    const cloudflareResponse = await fetch(
      `https://rtc.live.cloudflare.com/v1/turn/keys/${encodeURIComponent(turnKeyId)}/credentials/generate-ice-servers`,
      {
        method: "POST",
        headers: {
          Authorization: `Bearer ${turnApiToken}`,
          "Content-Type": "application/json",
        },
        body: JSON.stringify({
          ttl: 3600,
          customIdentifier: authData.user.id,
        }),
        signal: AbortSignal.timeout(10000),
      },
    );

    if (!cloudflareResponse.ok) {
      const responseText = await cloudflareResponse.text();

      console.error("Cloudflare TURN credential request failed:", {
        status: cloudflareResponse.status,
        response: responseText.slice(0, 500),
      });

      return jsonResponse(502, {
        error: "Could not obtain temporary TURN credentials",
      });
    }

    const cloudflareData: unknown = await cloudflareResponse.json();

    if (
      typeof cloudflareData !== "object" ||
      cloudflareData === null ||
      !("iceServers" in cloudflareData) ||
      !Array.isArray(cloudflareData.iceServers) ||
      cloudflareData.iceServers.length === 0
    ) {
      console.error("Cloudflare returned an invalid ICE-server response.");

      return jsonResponse(502, {
        error: "TURN provider returned an invalid response",
      });
    }

    return jsonResponse(200, {
      iceServers: cloudflareData.iceServers,
      expiresInSeconds: 3600,
    });
  } catch (error) {
    console.error(
      "TURN credential request failed:",
      error instanceof Error ? error.message : "Unknown error",
    );

    return jsonResponse(502, {
      error: "TURN credential service is temporarily unavailable",
    });
  }
});
