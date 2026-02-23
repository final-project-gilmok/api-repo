-- Atomically pop members from queue and add to admitted sorted set
-- KEYS[1] = queue:{eventId}        (source sorted set)
-- KEYS[2] = queue:{eventId}:admitted (destination sorted set)
-- ARGV[1] = count
-- ARGV[2] = currentTimeMs (admitted score)

local queueKey = KEYS[1]
local admittedKey = KEYS[2]
local count = tonumber(ARGV[1])
local now = tonumber(ARGV[2])

local members = redis.call('ZPOPMIN', queueKey, count)
local admitted = 0

-- ZPOPMIN returns {member1, score1, member2, score2, ...}
for i = 1, #members, 2 do
    redis.call('ZADD', admittedKey, now, members[i])
    admitted = admitted + 1
end

return admitted

