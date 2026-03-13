-- Policy enforcement: block check + rate limit in a single call
-- KEYS[1] = blockKey (e.g. policy:block:{eventId}:{clientKey})
-- KEYS[2] = rlKey    (e.g. policy:rl:{eventId}:{clientKey}:{second})
--
-- ARGV[1] = maxRps (0 = rate limiting disabled)
-- ARGV[2] = blockDurationSeconds (0 = no auto-block on exceed)
--
-- Returns:
--   0 = allowed
--   1 = already blocked
--   2 = rate limit exceeded (block created)
--   3 = rate limit exceeded (no block configured)

local blockKey = KEYS[1]
local rlKey    = KEYS[2]
local maxRps              = tonumber(ARGV[1]) or 0
local blockDurationSeconds = tonumber(ARGV[2]) or 0

-- 1. Check existing block
if redis.call('EXISTS', blockKey) == 1 then
    return 1
end

-- 2. Rate limit (skip if disabled)
if maxRps <= 0 then
    return 0
end

local count = redis.call('INCR', rlKey)
if count == 1 then
    redis.call('EXPIRE', rlKey, 2)
end

if count > maxRps then
    if blockDurationSeconds > 0 then
        redis.call('SET', blockKey, '1', 'EX', blockDurationSeconds)
        return 2
    end
    return 3
end

return 0
