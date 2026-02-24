-- Idempotent Queue Registration
-- KEYS[1] = queueKey (waiting ZSET)
-- KEYS[2] = admittedKey (admitted ZSET)
-- KEYS[3] = userIndexKey (user-index HASH)
-- KEYS[4] = heartbeatsKey (heartbeats ZSET)
-- KEYS[5] = sessionKey (session HASH for new registration)
--
-- ARGV[1] = userId
-- ARGV[2] = newQueueKey
-- ARGV[3] = score (timestamp)
-- ARGV[4] = nowMs
-- ARGV[5] = sessionTtlSeconds
--
-- Returns: {isNew, queueKeyString, rank}
--   isNew: -1 = already admitted (blocked), 0 = existing waiting (idempotent), 1 = newly registered

local queueKey      = KEYS[1]
local admittedKey   = KEYS[2]
local userIndexKey  = KEYS[3]
local heartbeatsKey = KEYS[4]
local sessionKey    = KEYS[5]

local userId          = ARGV[1]
local newQueueKeyVal  = ARGV[2]
local score           = tonumber(ARGV[3]) or 0
local nowMs           = tonumber(ARGV[4]) or 0
local sessionTtlSec  = tonumber(ARGV[5]) or 600

-- 1) Check existing registration via user-index
local existing = redis.call('HGET', userIndexKey, userId)
if existing then
  -- a) Check if already admitted
  local admScore = redis.call('ZSCORE', admittedKey, existing)
  if admScore then
    return {-1, existing, -1}
  end

  -- b) Check if still in waiting queue
  local existingRank = redis.call('ZRANK', queueKey, existing)
  if existingRank ~= false then
    return {0, existing, existingRank}
  end

  -- c) Both gone (expired) — clean up and re-register below
  redis.call('HDEL', userIndexKey, userId)
end

-- 2) New registration
redis.call('ZADD', queueKey, 'NX', score, newQueueKeyVal)
redis.call('HSET', userIndexKey, userId, newQueueKeyVal)
redis.call('ZADD', heartbeatsKey, nowMs, newQueueKeyVal)

-- Set initial EXPIRE on heartbeats key (only on fresh registration)
redis.call('EXPIRE', heartbeatsKey, 600)

-- Create session HASH
local sessKey = string.gsub(sessionKey, '__PLACEHOLDER__', newQueueKeyVal)
-- Session key is already resolved by caller, use KEYS[5] directly
redis.call('HSET', sessionKey, 'eventId', '', 'state', 'WAITING', 'createdAt', nowMs, 'lastSeenAt', nowMs)
redis.call('EXPIRE', sessionKey, sessionTtlSec)

-- Get rank of newly registered member
local newRank = redis.call('ZRANK', queueKey, newQueueKeyVal)
if newRank == false then newRank = 0 end

return {1, newQueueKeyVal, newRank}
