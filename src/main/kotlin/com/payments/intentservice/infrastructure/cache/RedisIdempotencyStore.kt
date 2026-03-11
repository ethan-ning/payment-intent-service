package com.payments.intentservice.infrastructure.cache

import com.fasterxml.jackson.databind.ObjectMapper
import com.payments.intentservice.application.port.outbound.IdempotencyRecord
import com.payments.intentservice.application.port.outbound.IdempotencyStore
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import java.time.Duration

@Component
class RedisIdempotencyStore(
    private val redisTemplate: StringRedisTemplate,
    private val objectMapper: ObjectMapper
) : IdempotencyStore {

    private val log = LoggerFactory.getLogger(RedisIdempotencyStore::class.java)

    companion object {
        private const val KEY_PREFIX = "idempotency:"
        private const val LOCK_PREFIX = "idempotency:lock:"
    }

    override fun get(key: String): IdempotencyRecord? {
        return try {
            redisTemplate.opsForValue().get(keyFor(key))
                ?.let { objectMapper.readValue(it, IdempotencyRecord::class.java) }
        } catch (e: Exception) {
            log.warn("Failed to get idempotency record for key=$key", e)
            null
        }
    }

    override fun set(key: String, record: IdempotencyRecord, ttl: Duration) {
        try {
            val json = objectMapper.writeValueAsString(record)
            redisTemplate.opsForValue().set(keyFor(key), json, ttl)
        } catch (e: Exception) {
            log.warn("Failed to set idempotency record for key=$key", e)
        }
    }

    override fun tryAcquireLock(key: String, ttl: Duration): Boolean {
        return redisTemplate.opsForValue()
            .setIfAbsent(lockKeyFor(key), "locked", ttl) == true
    }

    override fun releaseLock(key: String) {
        redisTemplate.delete(lockKeyFor(key))
    }

    private fun keyFor(key: String) = "$KEY_PREFIX$key"
    private fun lockKeyFor(key: String) = "$LOCK_PREFIX$key"
}
