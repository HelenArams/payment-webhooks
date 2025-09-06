create table endpoint (
  id         uuid                     primary key,
  url        varchar(2048)            not null,
  secret     varchar(128)             not null,
  enabled    boolean                  not null default true,
  created_at timestamp with time zone not null
);

create table endpoint_event_type (
  endpoint_id uuid         not null references endpoint (id) on delete cascade,
  event_type  varchar(100) not null,
  primary key (endpoint_id, event_type)
);
