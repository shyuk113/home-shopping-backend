-- KEYS[1] = coupon:{id}:meta    (hash: total, start, end)
-- KEYS[2] = coupon:{id}:members (set: 발급받은 memberId)
-- ARGV[1] = memberId, ARGV[2] = 현재 시각(epoch millis)
local meta = redis.call('HMGET', KEYS[1], 'total', 'start', 'end')
if not meta[1] then return -3 end                                   -- 쿠폰 정보 없음

local now = tonumber(ARGV[2])
if now < tonumber(meta[2]) or now > tonumber(meta[3]) then return -2 end  -- 발급 기간 아님

if redis.call('SISMEMBER', KEYS[2], ARGV[1]) == 1 then return -1 end      -- 중복 발급

if redis.call('SCARD', KEYS[2]) >= tonumber(meta[1]) then return 0 end    -- 수량 소진

redis.call('SADD', KEYS[2], ARGV[1])
return 1                                                                  -- 성공