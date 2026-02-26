-- Queue Status (single EVAL for getStatus)
-- KEYS[1] = queueKey (waiting ZSET)
-- KEYS[2] = admittedKey (admitted ZSET)
-- KEYS[3] = heartbeatsKey (heartbeats ZSET)
-- KEYS[4] = sessionKey (session HASH)
-- KEYS[5] = admitRateKey (admit-rate HASH)
--
-- ARGV[1] = member (queueKey value)
-- ARGV[2] = nowMs
-- ARGV[3] = windowSeconds
--
-- Returns: {statusCode, rank, totalSize, admitCountInWindow}
--   statusCode: 1=ADMITTED, 2=WAITING, 3=EXPIRED

local queueKey     = KEYS[1]
local admittedKey  = KEYS[2]
local heartbeatsKey = KEYS[3]
local sessionKey   = KEYS[4]
local admitRateKey = KEYS[5]

local member        = ARGV[1]
local nowMs         = tonumber(ARGV[2]) or 0
local windowSeconds = tonumber(ARGV[3]) or 60

-- 1) Check if admitted
local admittedScore = redis.call('ZSCORE', admittedKey, member)
if admittedScore then
  return {1, -1, 0, 0}
end

-- 2) Check if waiting in queue
local rank = redis.call('ZRANK', queueKey, member)
if rank ~= false then
  -- Update heartbeat (ZADD only, no EXPIRE)
  redis.call('ZADD', heartbeatsKey, nowMs, member)

  -- Update session lastSeenAt
  if redis.call('EXISTS', sessionKey) == 1 then
    redis.call('HSET', sessionKey, 'lastSeenAt', nowMs)
  end

  -- Get total queue size
  local total = redis.call('ZCARD', queueKey)

  -- Calculate admit count in window
  local admitSum = 0
  local nowSec = math.floor(nowMs / 1000)
  local startSec = nowSec - windowSeconds
  local fields = redis.call('HGETALL', admitRateKey)
  for i = 1, #fields, 2 do
    local sec = tonumber(fields[i])
    if sec and sec > startSec then
      admitSum = admitSum + (tonumber(fields[i + 1]) or 0)
    end
  end

  return {2, rank, total, admitSum}
end

-- 3) Session fallback check
if redis.call('EXISTS', sessionKey) == 1 then
  local state = redis.call('HGET', sessionKey, 'state')
  if state == 'ADMITTED' then
    return {1, -1, 0, 0}
  end
  -- Stale session, clean up
  redis.call('DEL', sessionKey)
end

-- 4) Not found anywhere
return {3, -1, 0, 0}
