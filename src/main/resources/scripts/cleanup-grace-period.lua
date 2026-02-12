-- KEYS[1] = queue:{eventId} (waiting queue ZSET)
-- KEYS[2] = queue:{eventId}:heartbeats (heartbeat ZSET)
-- ARGV[1] = gracePeriodMs
-- ARGV[2] = currentTimeMs
-- Returns: number of removed stale entries

local queueKey = KEYS[1]
local heartbeatsKey = KEYS[2]
local gracePeriodMs = tonumber(ARGV[1])
local currentTimeMs = tonumber(ARGV[2])

local cutoff = currentTimeMs - gracePeriodMs

-- Find stale heartbeats (score < cutoff)
local staleMembers = redis.call('ZRANGEBYSCORE', heartbeatsKey, '-inf', cutoff)

if #staleMembers == 0 then
    return 0
end

-- Remove stale members from the waiting queue
for _, member in ipairs(staleMembers) do
    redis.call('ZREM', queueKey, member)
end

-- Remove stale heartbeats
redis.call('ZREMRANGEBYSCORE', heartbeatsKey, '-inf', cutoff)

return #staleMembers
