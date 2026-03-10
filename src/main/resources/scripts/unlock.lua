-- Distributed lock release (CAS)
-- KEYS[1] = lock key
-- ARGV[1] = expected value (owner UUID)
--
-- Returns: 1 (released) / 0 (not owner or not held)

if redis.call('GET', KEYS[1]) == ARGV[1] then
  return redis.call('DEL', KEYS[1])
end
return 0
