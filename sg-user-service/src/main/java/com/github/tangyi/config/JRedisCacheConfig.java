package com.github.tangyi.config;

import com.github.tangyi.common.utils.EnvUtils;
import lombok.extern.slf4j.Slf4j;
import io.lettuce.core.ClientOptions;
import io.lettuce.core.SocketOptions;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.*;
import redis.clients.jedis.JedisPoolConfig;

import javax.annotation.PreDestroy;
import java.time.Duration;

@Configuration
@EnableCaching
public class JRedisCacheConfig {

    // 超时时间：24 小时
    public static final int DEFAULT_REDIS_CACHE_EXPIRE = EnvUtils.getInt("DEFAULT_REDIS_CACHE_EXPIRE", 24);

    @Value("${spring.redis.host}")
    private String redisHost;

    @Value("${spring.redis.username}")
    private String username;

    @Value("${spring.redis.password}")
    private String redisPassword;

    @Value("${spring.redis.port:6379}")
    private int port;

    @Value("${spring.redis.timeout:60}")
    private int timeOutSecond;

    @Bean
    public JedisConnectionFactory jedisConnectionFactory() {
        RedisStandaloneConfiguration configuration = new RedisStandaloneConfiguration(redisHost, port);
        if (StringUtils.isNotEmpty(username)) {
            configuration.setUsername(username);
        }
        if (StringUtils.isNotEmpty(redisPassword)) {
            configuration.setPassword(redisPassword);
        }

        JedisPoolConfig poolConfig = new JedisPoolConfig();
        poolConfig.setMaxTotal(10);
        poolConfig.setMaxIdle(5);
        poolConfig.setMinIdle(1);
        poolConfig.setTestOnBorrow(true);
        poolConfig.setTestOnReturn(true);
        poolConfig.setTestWhileIdle(true);
        poolConfig.setMinEvictableIdleTimeMillis(Duration.ofSeconds(60).toMillis());
        poolConfig.setTimeBetweenEvictionRunsMillis(Duration.ofSeconds(30).toMillis());
        poolConfig.setNumTestsPerEvictionRun(-1);

        JedisConnectionFactory jedisConnectionFactory = new JedisConnectionFactory(configuration);
        jedisConnectionFactory.setPoolConfig(poolConfig);
        jedisConnectionFactory.setTimeout(timeOutSecond * 1000); // in milliseconds
        return jedisConnectionFactory;
    }

    @Bean
    public RedisTemplate<String, Long> longRedisTemplate(JedisConnectionFactory jedisConnectionFactory) {
        RedisTemplate<String, Long> redisTemplate = new RedisTemplate<>();
        redisTemplate.setKeySerializer(new StringRedisSerializer());
        redisTemplate.setValueSerializer(new GenericJackson2JsonRedisSerializer());
        redisTemplate.setConnectionFactory(jedisConnectionFactory);
        return redisTemplate;
    }

    @Bean
    public CacheManager cacheManager(JedisConnectionFactory jedisConnectionFactory) {
        RedisCacheConfiguration redisCacheConfiguration = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofHours(DEFAULT_REDIS_CACHE_EXPIRE))
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(new GenericJackson2JsonRedisSerializer()));
        return RedisCacheManager.builder(jedisConnectionFactory)
                .cacheDefaults(redisCacheConfiguration)
                .build();
    }
}
