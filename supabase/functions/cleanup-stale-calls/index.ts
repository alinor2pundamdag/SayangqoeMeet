import { createClient } from "npm:@supabase/supabase-js@2";

const json = (status: number, body: Record<string, unknown>) =>
  new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json; charset=utf-8" },
  });

Deno.serve(async (req: Request) => {
  if (req.method === "OPTIONS") return new Response("ok");
  if (req.method !== "POST") return json(405, { error: "Method not allowed" });

  const expected = Deno.env.get("MEDIA_CLEANUP_CRON_SECRET");
  const supplied = req.headers.get("x-cleanup-secret");
  if (!expected || !supplied || supplied.length !== expected.length || supplied !== expected) {
    return json(401, { error: "Unauthorized" });
  }

  const url = Deno.env.get("SUPABASE_URL");
  const serviceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
  if (!url || !serviceKey) return json(500, { error: "Server configuration is incomplete" });

  let body: { dry_run?: unknown };
  try {
    body = await req.json();
  } catch {
    return json(400, { error: "Expected a JSON body" });
  }

  const admin = createClient(url, serviceKey, {
    auth: { persistSession: false, autoRefreshToken: false },
  });
  // Ringing calls expire quickly; accepted/connecting calls without media are
  // reconciled after 24h. Connected calls are never aged out automatically.
  // Terminal call rows (and their cascading ICE candidates) are retained for 30d.
  const cutoff = new Date(Date.now() - 60_000).toISOString();
  const negotiatingCutoff = new Date(Date.now() - 24 * 60 * 60 * 1000).toISOString();
  const retentionCutoff = new Date(Date.now() - 30 * 24 * 60 * 60 * 1000).toISOString();
  const terminalStatuses = ["declined", "cancelled", "ended", "missed", "failed"];

  if (body.dry_run === true) {
    const { count, error } = await admin
      .from("call_sessions")
      .select("id", { count: "exact", head: true })
      .eq("status", "ringing")
      .lt("created_at", cutoff);
    if (error) {
      console.error("Stale-call dry-run failed", JSON.stringify({ code: error.code }));
      return json(500, { error: "Could not inspect stale call sessions" });
    }
    const { count: negotiatingCount, error: negotiatingError } = await admin
      .from("call_sessions")
      .select("id", { count: "exact", head: true })
      .in("status", ["accepted", "connecting"])
      .not("answered_at", "is", null)
      .lt("answered_at", negotiatingCutoff);
    if (negotiatingError) {
      console.error("Stale negotiation dry-run failed", JSON.stringify({ code: negotiatingError.code }));
      return json(500, { error: "Could not inspect abandoned call negotiations" });
    }
    const { count: retentionCount, error: retentionError } = await admin
      .from("call_sessions")
      .select("id", { count: "exact", head: true })
      .in("status", terminalStatuses)
      .not("ended_at", "is", null)
      .lt("ended_at", retentionCutoff);
    if (retentionError) {
      console.error("Call retention dry-run failed", JSON.stringify({ code: retentionError.code }));
      return json(500, { error: "Could not inspect expired terminal calls" });
    }
    return json(200, {
      dry_run: true,
      stale_ringing_calls: count ?? 0,
      abandoned_negotiations: negotiatingCount ?? 0,
      terminal_calls_eligible_for_deletion: retentionCount ?? 0,
      ice_candidates_deleted: 0,
      marked_missed: 0,
      marked_failed: 0,
    });
  }

  const { data, error } = await admin
    .from("call_sessions")
    .update({ status: "missed", ended_at: new Date().toISOString() })
    .eq("status", "ringing")
    .lt("created_at", cutoff)
    .select("id");

  if (error) {
    console.error("Stale-call cleanup failed", JSON.stringify({ code: error.code }));
    return json(500, { error: "Could not close stale call sessions" });
  }

  const { data: failedNegotiations, error: negotiationError } = await admin
    .from("call_sessions")
    .update({ status: "failed", ended_at: new Date().toISOString() })
    .in("status", ["accepted", "connecting"])
    .not("answered_at", "is", null)
    .lt("answered_at", negotiatingCutoff)
    .select("id");
  if (negotiationError) {
    console.error("Abandoned-call reconciliation failed", JSON.stringify({ code: negotiationError.code }));
    return json(500, { error: "Could not reconcile abandoned call negotiations" });
  }

  // call_ice_candidates.call_id references call_sessions(id) ON DELETE CASCADE.
  // Delete only terminal sessions whose ended_at is older than the retention window;
  // active/connected sessions are excluded from the delete predicate.
  const { data: deletedCalls, error: retentionError } = await admin
    .from("call_sessions")
    .delete()
    .in("status", terminalStatuses)
    .not("ended_at", "is", null)
    .lt("ended_at", retentionCutoff)
    .select("id");
  if (retentionError) {
    console.error("Terminal call retention failed", JSON.stringify({ code: retentionError.code }));
    return json(500, { error: "Could not delete expired terminal call sessions" });
  }

  return json(200, {
    dry_run: false,
    stale_ringing_calls: data?.length ?? 0,
    marked_missed: data?.length ?? 0,
    abandoned_negotiations: failedNegotiations?.length ?? 0,
    marked_failed: failedNegotiations?.length ?? 0,
    terminal_calls_deleted: deletedCalls?.length ?? 0,
    ice_candidate_retention: "ICE rows cascade when their terminal call session is deleted",
    terminal_retention_days: 30,
  });
});
