-- A dispatcher that claims a delivery leases it for a short period so that concurrent pollers
-- (other instances, or an overlapping tick) skip it while the HTTP call is in flight. If the
-- process dies mid-flight the lease simply expires and the delivery is picked up again.
alter table delivery add column leased_until timestamp with time zone;
