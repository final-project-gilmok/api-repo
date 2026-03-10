-- Admission Rate Recording
-- KEYS[1] = admit-rate HASH
--
-- ARGV[1] = epochSecond (current second)
-- ARGV[2] = count (number of admissions)
-- ARGV[3] = windowSeconds (lookback window)
-- ARGV[4] = hashTtlSeconds (TTL for the hash key)
--
-- Returns: {totalCountInWindow}

local rateKey       = KEYS[1]
local epochSecond   = tonumber(ARGV[1]) or 0
local count         = tonumber(ARGV[2]) or 0
local windowSeconds = tonumber(ARGV[3]) or 60
local hashTtl       = tonumber(ARGV[4]) or 120

-- Increment current second's count
redis.call('HINCRBY', rateKey, tostring(epochSecond), count)
redis.call('EXPIRE', rateKey, hashTtl)

-- Sum counts within window and clean up old fields
local totalInWindow = 0
local cutoff = epochSecond - windowSeconds
local fields = redis.call('HGETALL', rateKey)
local toDelete = {}

for i = 1, #fields, 2 do
  local sec = tonumber(fields[i])
  if sec then
    if sec <= cutoff then
      toDelete[#toDelete + 1] = fields[i]
    else
      totalInWindow = totalInWindow + (tonumber(fields[i + 1]) or 0)
    end
  end
end

-- Delete old fields
if #toDelete > 0 then
  redis.call('HDEL', rateKey, unpack(toDelete))
end

return {totalInWindow}
