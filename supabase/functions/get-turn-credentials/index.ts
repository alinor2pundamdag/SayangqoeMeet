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
