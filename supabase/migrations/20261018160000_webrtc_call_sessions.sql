-- WebRTC call sessions and participant-only signaling.
-- SDP and ICE data are private signaling material; only either participant can access it.
begin;

create table if not exists public.call_sessions (
  id uuid primary key default gen_random_uuid(),
  caller_id uuid not null references auth.users(id) on delete cascade,
  callee_id uuid not null references auth.users(id) on delete cascade,
  call_type text not null check (call_type in ('voice', 'video')),
  status text not null default 'ringing'
    check (status in ('ringing', 'accepted', 'declined', 'connecting', 'connected', 'ended', 'missed', 'failed')),
  offer_sdp text,
  answer_sdp text,
  created_at timestamptz not null default now(),
  answered_at timestamptz,
  ended_at timestamptz,
  constraint call_sessions_distinct_participants check (caller_id <> callee_id)
);

create index if not exists call_sessions_caller_created_idx
  on public.call_sessions (caller_id, created_at desc);
create index if not exists call_sessions_callee_status_created_idx
  on public.call_sessions (callee_id, status, created_at desc);

create table if not exists public.call_ice_candidates (
  id bigint generated always as identity primary key,
  call_id uuid not null references public.call_sessions(id) on delete cascade,
  sender_id uuid not null references auth.users(id) on delete cascade,
  candidate text not null,
  sdp_mid text,
  sdp_m_line_index integer,
  created_at timestamptz not null default now()
);
create index if not exists call_ice_candidates_call_created_idx
  on public.call_ice_candidates (call_id, created_at);

alter table public.call_sessions enable row level security;
alter table public.call_ice_candidates enable row level security;

revoke all on public.call_sessions from anon, authenticated;
revoke all on public.call_ice_candidates from anon, authenticated;
grant select, insert, update on public.call_sessions to authenticated;
grant select, insert on public.call_ice_candidates to authenticated;
grant usage, select on sequence public.call_ice_candidates_id_seq to authenticated;

drop policy if exists call_sessions_participant_select on public.call_sessions;
create policy call_sessions_participant_select
  on public.call_sessions for select to authenticated
  using (auth.uid() = caller_id or auth.uid() = callee_id);

drop policy if exists call_sessions_caller_insert on public.call_sessions;
create policy call_sessions_caller_insert
  on public.call_sessions for insert to authenticated
  with check (auth.uid() = caller_id and caller_id <> callee_id);

drop policy if exists call_sessions_participant_update on public.call_sessions;
create policy call_sessions_participant_update
  on public.call_sessions for update to authenticated
  using (auth.uid() = caller_id or auth.uid() = callee_id)
  with check (auth.uid() = caller_id or auth.uid() = callee_id);

drop policy if exists call_ice_participant_select on public.call_ice_candidates;
create policy call_ice_participant_select
  on public.call_ice_candidates for select to authenticated
  using (
    exists (
      select 1 from public.call_sessions s
      where s.id = call_id and (s.caller_id = auth.uid() or s.callee_id = auth.uid())
    )
  );

drop policy if exists call_ice_participant_insert on public.call_ice_candidates;
create policy call_ice_participant_insert
  on public.call_ice_candidates for insert to authenticated
  with check (
    sender_id = auth.uid()
    and exists (
      select 1 from public.call_sessions s
      where s.id = call_id and (s.caller_id = auth.uid() or s.callee_id = auth.uid())
    )
  );

commit;
