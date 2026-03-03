-- Remove Admitted User (reservation cancellation cleanup)
-- KEYS[1] = userIndexKey  (HASH: userId -> queueKey)
-- KEYS[2] = admittedKey   (ZSET: queueKey -> score)
-- KEYS[3] = sessionKeyPrefix (e.g. "queue:{eventId}:session:")
--
-- ARGV[1] = userId
--
-- Returns: 1 if removed, 0 if user was not in admitted set

local userIndexKey     = KEYS[1]
local admittedKey      = KEYS[2]
local sessionKeyPrefix = KEYS[3]

local userId = ARGV[1]

-- 1) Look up queueKey from user-index
local queueKey = redis.call('HGET', userIndexKey, userId)
if not queueKey then
  return 0
end

-- 2) Remove from admitted ZSET
local removed = redis.call('ZREM', admittedKey, queueKey)

-- 3) Remove from user-index
redis.call('HDEL', userIndexKey, userId)

-- 4) Delete session hash
redis.call('DEL', sessionKeyPrefix .. queueKey)

return removed
