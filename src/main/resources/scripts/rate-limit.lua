-- Sliding Window Rate Limiter
-- KEYS[1] = rate limit key (e.g. "rate:queue:<ip>")
-- ARGV[1] = limit (max requests in window)
-- ARGV[2] = windowMs (window size in milliseconds)
-- ARGV[3] = nowMs (current timestamp in milliseconds)
-- ARGV[4] = uniqueId (unique request identifier)
--
-- Returns: 1 = allowed, 0 = blocked

local key = KEYS[1]
local limit = tonumber(ARGV[1]) or 10
local windowMs = tonumber(ARGV[2]) or 1000
local nowMs = tonumber(ARGV[3]) or 0
local uniqueId = ARGV[4]

-- Remove expired entries
local cutoff = nowMs - windowMs
redis.call('ZREMRANGEBYSCORE', key, '-inf', cutoff)

-- Count current requests in window
local count = redis.call('ZCARD', key)

if count >= limit then
  return 0
end

-- Add new request
redis.call('ZADD', key, nowMs, uniqueId)

-- Set TTL on key to auto-cleanup (window * 2 for safety)
local ttlSec = math.ceil(windowMs * 2 / 1000)
if ttlSec < 1 then ttlSec = 1 end
redis.call('EXPIRE', key, ttlSec)

return 1
