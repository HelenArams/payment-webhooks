create table payment_event (
  id          uuid                     primary key,
  type        varchar(100)             not null,
  payload     text                     not null,
  occurred_at timestamp with time zone not null,
  created_at  timestamp with time zone not null
);

create table delivery (
  id               uuid                     primary key,
  endpoint_id      uuid                     not null references endpoint (id),
  event_id         uuid                     not null references payment_event (id),
  status           varchar(16)              not null,
  attempts         integer                  not null default 0,
  next_attempt_at  timestamp with time zone,
  last_status_code integer,
  last_error       varchar(500),
  created_at       timestamp with time zone not null,
  updated_at       timestamp with time zone not null,
  completed_at     timestamp with time zone
);

create index delivery_due_idx on delivery (status, next_attempt_at);
create index delivery_endpoint_idx on delivery (endpoint_id, created_at);
create index delivery_event_idx on delivery (event_id);
