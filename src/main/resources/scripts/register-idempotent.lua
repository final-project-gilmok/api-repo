-- Idempotent Queue Registration
-- KEYS[1] = queueKey (waiting ZSET)
-- KEYS[2] = admittedKey (admitted ZSET)
-- KEYS[3] = userIndexKey (user-index HASH)
-- KEYS[4] = heartbeatsKey (heartbeats ZSET)
-- KEYS[5] = sessionKeyPrefix (e.g. "queue:{eventId}:session:")
--
-- ARGV[1] = userId
-- ARGV[2] = newQueueKey
-- ARGV[3] = score (timestamp)
-- ARGV[4] = nowMs
-- ARGV[5] = sessionTtlSeconds
-- ARGV[6] = eventId
--
-- Returns: {isNew, queueKeyString, rank}
--   isNew: -1 = already admitted (blocked), 0 = existing waiting (idempotent), 1 = newly registered

local queueKey           = KEYS[1]
local admittedKey        = KEYS[2]
local userIndexKey       = KEYS[3]
local heartbeatsKey      = KEYS[4]
local sessionKeyPrefix   = KEYS[5]

local userId          = ARGV[1]
local newQueueKeyVal  = ARGV[2]
local score           = tonumber(ARGV[3]) or 0
local nowMs           = tonumber(ARGV[4]) or 0
local sessionTtlSec   = tonumber(ARGV[5]) or 600
local eventIdVal      = ARGV[6]

-- Helper: upsert session — create if missing, update state + lastSeenAt, extend TTL
local function upsertSession(qk, state)
  local sessKey = sessionKeyPrefix .. qk
  local existingCreatedAt = redis.call('HGET', sessKey, 'createdAt')
  if existingCreatedAt then
    redis.call('HSET', sessKey, 'state', state, 'lastSeenAt', nowMs)
  else
    redis.call('HSET', sessKey, 'eventId', eventIdVal, 'state', state, 'createdAt', nowMs, 'lastSeenAt', nowMs)
  end
  redis.call('EXPIRE', sessKey, sessionTtlSec)
end

-- 1) Check existing registration via user-index
local existing = redis.call('HGET', userIndexKey, userId)
if existing then
  -- a) Already admitted → session upsert (ADMITTED) + return blocked
  local admScore = redis.call('ZSCORE', admittedKey, existing)
  if admScore then
    upsertSession(existing, 'ADMITTED')
    return {-1, existing, -1}
  end

  -- b) Still in waiting queue → session upsert (WAITING) + return idempotent
  local existingRank = redis.call('ZRANK', queueKey, existing)
  if existingRank ~= false then
    upsertSession(existing, 'WAITING')
    return {0, existing, existingRank}
  end

  -- c) Both gone (expired) → clean up and re-register below
  redis.call('HDEL', userIndexKey, userId)
end

-- 2) New registration
redis.call('ZADD', queueKey, 'NX', score, newQueueKeyVal)
redis.call('HSET', userIndexKey, userId, newQueueKeyVal)
redis.call('ZADD', heartbeatsKey, nowMs, newQueueKeyVal)

-- Create session HASH
local sessKey = sessionKeyPrefix .. newQueueKeyVal
redis.call('HSET', sessKey, 'eventId', eventIdVal, 'state', 'WAITING', 'createdAt', nowMs, 'lastSeenAt', nowMs)
redis.call('EXPIRE', sessKey, sessionTtlSec)

-- Get rank of newly registered member
local newRank = redis.call('ZRANK', queueKey, newQueueKeyVal)
if newRank == false then newRank = 0 end

return {1, newQueueKeyVal, newRank}
