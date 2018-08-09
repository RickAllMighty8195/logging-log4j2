package org.apache.logging.log4j.redis.appender;

import com.sun.tools.javac.util.List;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractManager;
import org.apache.logging.log4j.core.net.ssl.SslConfiguration;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.exceptions.JedisConnectionException;

import java.net.URI;
import java.nio.charset.Charset;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

class RedisManager extends AbstractManager {

    private final String[] keys;
    private final String host;
    private final int port;
    private final SslConfiguration sslConfiguration;
    private final JedisPoolConfig poolConfiguration;
    private JedisPool jedisPool;

    RedisManager(LoggerContext loggerContext, String name, String[] keys, String host, int port, SslConfiguration sslConfiguration, LoggingJedisPoolConfiguration poolConfiguration) {
        super(loggerContext, name);
        this.keys = keys;
        this.host = host;
        this.port = port;
        this.sslConfiguration = sslConfiguration;
        if (poolConfiguration == null) {
            this.poolConfiguration = LoggingJedisPoolConfiguration.defaultConfiguration();
        } else {
            this.poolConfiguration = poolConfiguration;
        }
    }

    JedisPool createPool(String host, int port, SslConfiguration sslConfiguration) {
        if (sslConfiguration != null) {
            return new JedisPool(
                    poolConfiguration,
                    URI.create(host + ":" + String.valueOf(port)),
                    sslConfiguration.getSslSocketFactory(),
                    sslConfiguration.getSslContext().getSupportedSSLParameters(),
                    null
            );
        } else {
            return new JedisPool(poolConfiguration, host, port, false);
        }

    }

    public void startup() {
        jedisPool = createPool(host, port, sslConfiguration);
    }

    public void sendBulk(Collection<String> values) {
        try (Jedis jedis = jedisPool.getResource()){
            for (String value: values) {
                send(value, jedis);
            }
        } catch (JedisConnectionException e) {
            LOGGER.error("Unable to connect to redis. Please ensure that it's running on {}:{}", host, port, e);
        }
    }

    public void send(String value) {
        try (Jedis jedis = jedisPool.getResource()) {
            try {
                send(value, jedis);
            } catch (JedisConnectionException e) {
                LOGGER.error("Unable to connect to redis. Please ensure that it's running on {}:{}", host, port, e);
            }
        }
    }

    public void send(String value, Jedis jedis) {
        for (String key: keys) {
            jedis.rpush(key, value);
        }
    }

    @Override
    protected boolean releaseSub(final long timeout, final TimeUnit timeUnit) {
        if (jedisPool != null) {
            jedisPool.destroy();
        }
        return true;
    }

    String getHost() {
        return host;
    }

    int getPort() {
        return port;
    }

    String getKeysAsString() {
        return String.join(",", keys);
    }
}
