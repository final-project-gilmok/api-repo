-- FAST Admission Cycle (single EVAL per tick)
-- KEYS[1] = waiting queue ZSET
-- KEYS[2] = admitted ZSET
-- KEYS[3] = heartbeats ZSET
-- KEYS[4] = token-bucket HASH
--
-- ARGV[1] = rate (tokens/sec)
-- ARGV[2] = capacity (max tokens)
-- ARGV[3] = nowMs
-- ARGV[4] = admittedTtlMs
-- ARGV[5] = gracePeriodMs
-- ARGV[6] = cleanupBatch
-- ARGV[7] = expireBatch
-- ARGV[8] = maxConcurrency (0 = unlimited)
--
-- Returns: {expiredCount, cleanedCount, admittedCount, tokensConsumed, tokensLeft,
--           waitingSize, admittedSize, ...admittedMembers}

local queueKey = KEYS[1]
local admittedKey = KEYS[2]
local heartbeatsKey = KEYS[3]
local bucketKey = KEYS[4]

local rate = tonumber(ARGV[1]) or 0
local capacity = tonumber(ARGV[2]) or 0
local now = tonumber(ARGV[3]) or 0
local admittedTtlMs = tonumber(ARGV[4]) or 0
local graceMs = tonumber(ARGV[5]) or 0
local cleanupBatch = tonumber(ARGV[6]) or 100
local expireBatch = tonumber(ARGV[7]) or 100
local maxConcurrency = tonumber(ARGV[8]) or 0

-- Guards
if now <= 0 then
  return {0, 0, 0, 0, 0, 0, 0}
end
if cleanupBatch <= 0 then cleanupBatch = 100 end
if expireBatch <= 0 then expireBatch = 100 end
if cleanupBatch > 1000 then cleanupBatch = 1000 end
if expireBatch > 1000 then expireBatch = 1000 end

-- Internal upper bound for requestedMax
local requestedMax = 200

-- ------------------------------------------------------------
-- 1) Expire admitted (batch)
-- ------------------------------------------------------------
local expiredCount = 0
if admittedTtlMs > 0 then
  local cutoff = now - admittedTtlMs
  if cutoff > 0 then
    local expired = redis.call('ZRANGEBYSCORE', admittedKey, '-inf', cutoff, 'LIMIT', 0, expireBatch)
    if expired and #expired > 0 then
      redis.call('ZREM', admittedKey, unpack(expired))
      expiredCount = #expired
    end
  end
end

-- ------------------------------------------------------------
-- 2) Cleanup stale heartbeats (batch) and remove from queue
-- ------------------------------------------------------------
local cleanedCount = 0
if graceMs > 0 then
  local cutoff = now - graceMs
  if cutoff > 0 then
    local stale = redis.call('ZRANGEBYSCORE', heartbeatsKey, '-inf', cutoff, 'LIMIT', 0, cleanupBatch)
    if stale and #stale > 0 then
      -- remove from waiting queue + heartbeats (bulk)
      redis.call('ZREM', queueKey, unpack(stale))
      redis.call('ZREM', heartbeatsKey, unpack(stale))
      cleanedCount = #stale
    end
  end
end

-- ------------------------------------------------------------
-- 3) Token bucket refill + consume (how many we can admit)
-- ------------------------------------------------------------
local tokensLeft = 0
local tokensConsumed = 0
local canAdmit = 0

if rate > 0 and capacity > 0 and requestedMax > 0 then
  local tokens = tonumber(redis.call('HGET', bucketKey, 'tokens'))
  local lastRefillMs = tonumber(redis.call('HGET', bucketKey, 'lastRefillMs'))

  if tokens == nil then tokens = capacity end
  if lastRefillMs == nil or lastRefillMs <= 0 then lastRefillMs = now end

  local elapsed = now - lastRefillMs
  if elapsed > 0 then
    local refill = math.floor(elapsed * rate / 1000)
    if refill > 0 then
      tokens = tokens + refill
      if tokens > capacity then tokens = capacity end
      lastRefillMs = lastRefillMs + math.floor(refill * 1000 / rate)
    end
  end

  canAdmit = requestedMax
  if canAdmit > tokens then canAdmit = tokens end
  if canAdmit < 0 then canAdmit = 0 end

  tokensConsumed = canAdmit
  tokens = tokens - canAdmit
  tokensLeft = tokens

  redis.call('HSET', bucketKey, 'tokens', tokens, 'lastRefillMs', lastRefillMs)
end

-- ------------------------------------------------------------
-- 3.5) Concurrency limit: cap canAdmit by headroom
-- ------------------------------------------------------------
if maxConcurrency > 0 and canAdmit > 0 then
  local currentAdmitted = redis.call('ZCARD', admittedKey)
  local headroom = maxConcurrency - currentAdmitted
  if headroom < 0 then headroom = 0 end
  if canAdmit > headroom then
    -- return excess tokens
    local excess = canAdmit - headroom
    if excess > 0 and rate > 0 and capacity > 0 then
      local t = tokensLeft + excess
      if t > capacity then t = capacity end
      tokensLeft = t
      tokensConsumed = headroom
      redis.call('HSET', bucketKey, 'tokens', t)
    end
    canAdmit = headroom
  end
end

-- ------------------------------------------------------------
-- 4) Admit from head (pop from queue -> add to admitted + remove heartbeat)
-- ------------------------------------------------------------
local admittedCount = 0
local admittedMembers = {}
if canAdmit > 0 then
  local popped = redis.call('ZPOPMIN', queueKey, canAdmit)
  if popped and #popped > 0 then
    local m = 0
    for i = 1, #popped, 2 do
      m = m + 1
      admittedMembers[m] = popped[i]
    end

    -- add to admitted (bulk ZADD)
    local zaddArgs = { admittedKey }
    for i = 1, #admittedMembers do
      table.insert(zaddArgs, now)
      table.insert(zaddArgs, admittedMembers[i])
    end
    redis.call('ZADD', unpack(zaddArgs))

    -- remove from heartbeats (bulk)
    redis.call('ZREM', heartbeatsKey, unpack(admittedMembers))

    admittedCount = #admittedMembers

    -- If we couldn't pop as many as tokens reserved, return unused tokens back.
    if admittedCount < canAdmit and rate > 0 and capacity > 0 then
      local giveBack = canAdmit - admittedCount
      if giveBack > 0 then
        local t = tokensLeft + giveBack
        if t > capacity then t = capacity end
        tokensLeft = t
        tokensConsumed = admittedCount
        redis.call('HSET', bucketKey, 'tokens', t)
      end
    end
  else
    -- nothing to pop: give all tokens back
    if canAdmit > 0 and rate > 0 and capacity > 0 then
      local t = tokensLeft + canAdmit
      if t > capacity then t = capacity end
      tokensLeft = t
      tokensConsumed = 0
      redis.call('HSET', bucketKey, 'tokens', t)
    end
  end
end

-- ------------------------------------------------------------
-- 5) Collect final sizes
-- ------------------------------------------------------------
local waitingSize = redis.call('ZCARD', queueKey)
local admittedSize = redis.call('ZCARD', admittedKey)

-- Build result: 7 numbers + admitted member strings
local result = {expiredCount, cleanedCount, admittedCount, tokensConsumed, tokensLeft, waitingSize, admittedSize}
for i = 1, #admittedMembers do
  result[7 + i] = admittedMembers[i]
end

return result
