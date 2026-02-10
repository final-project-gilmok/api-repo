-- Token Bucket algorithm for queue admission rate limiting
-- KEYS[1] = queue:{eventId}:token-bucket
-- ARGV[1] = maxTokens (admissionRps)
-- ARGV[2] = currentTimeMs
-- ARGV[3] = requestedTokens

local key = KEYS[1]
local maxTokens = tonumber(ARGV[1])
local now = tonumber(ARGV[2])
local requested = tonumber(ARGV[3])

local tokens = tonumber(redis.call('HGET', key, 'tokens') or maxTokens)
local lastRefillMs = tonumber(redis.call('HGET', key, 'lastRefillMs') or 0)

-- Refill tokens based on elapsed time
if lastRefillMs > 0 then
    local elapsed = now - lastRefillMs
    local refill = math.floor(elapsed / 1000 * maxTokens)
    if refill > 0 then
        tokens = math.min(maxTokens, tokens + refill)
        lastRefillMs = lastRefillMs + math.floor(refill * 1000 / maxTokens)
    end
else
    tokens = maxTokens
    lastRefillMs = now
end

-- Consume tokens
local consumed = math.min(tokens, requested)
tokens = tokens - consumed

redis.call('HSET', key, 'tokens', tokens)
redis.call('HSET', key, 'lastRefillMs', lastRefillMs)

return consumed
