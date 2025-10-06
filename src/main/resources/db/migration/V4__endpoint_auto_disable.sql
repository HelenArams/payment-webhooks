alter table endpoint add column consecutive_dead integer not null default 0;
alter table endpoint add column disabled_at timestamp with time zone;
