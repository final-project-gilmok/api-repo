-- seat-lock.lua
-- 좌석 잔여석 감소 + 잠금 키 설정 (원자적)
-- KEYS[1] = seat-available:{eventId}:{seatId}
-- KEYS[2] = seat-lock:{eventId}:{seatId}:{userId}
-- ARGV[1] = 요청 수량
-- ARGV[2] = 잠금 TTL (초)
-- 반환: 1 = 성공, 0 = 잔여석 부족

local availableKey = KEYS[1]
local lockKey = KEYS[2]
local quantity = tonumber(ARGV[1])
local ttlSeconds = tonumber(ARGV[2])

local available = tonumber(redis.call('GET', availableKey) or '0')

if available < quantity then
    return 0
end

redis.call('DECRBY', availableKey, quantity)
redis.call('SET', lockKey, quantity, 'EX', ttlSeconds)

return 1
