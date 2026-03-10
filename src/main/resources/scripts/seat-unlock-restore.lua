-- seat-unlock-restore.lua
-- 잠금 해제 + 잔여석 복구 (원자적)
-- KEYS[1] = seat-available:{eventId}:{seatId}
-- KEYS[2] = seat-lock:{eventId}:{seatId}:{userId}
-- 반환: 복구된 수량 (0이면 잠금 없었음)

local availableKey = KEYS[1]
local lockKey = KEYS[2]

local lockedQty = tonumber(redis.call('GET', lockKey) or '0')

if lockedQty == 0 then
    return 0
end

redis.call('DEL', lockKey)
redis.call('INCRBY', availableKey, lockedQty)

return lockedQty
