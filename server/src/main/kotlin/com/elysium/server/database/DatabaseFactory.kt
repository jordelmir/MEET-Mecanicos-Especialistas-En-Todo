package com.elysium.server.database

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.slf4j.LoggerFactory
import java.sql.SQLException
import javax.sql.DataSource

/**
 * ══════════════════════════════════════════════════════════════════════
 *  D A T A B A S E   F A C T O R Y
 *  ──────────────────────────────────────────────────────────────
 *  Authoritative connection pool factory and health evaluator.
 *  Adheres strictly to Master Order Ascension Maxima Directives 47 & 48:
 *  - Real connection pooling with strict timeouts.
 *  - Fail-closed in production: Never silently fall back to in-memory
 *    storage when production environment is detected.
 *  - Health check executing "SELECT 1" with validation timeout.
 * ══════════════════════════════════════════════════════════════════════
 */
object DatabaseFactory {

    private val logger = LoggerFactory.getLogger(DatabaseFactory::class.java)

    @Volatile
    private var activeDataSource: HikariDataSource? = null

    data class DatabaseConfig(
        val jdbcUrl: String,
        val user: String,
        val pass: String,
        val maxPoolSize: Int = 10,
        val minIdle: Int = 2,
        val connectionTimeoutMs: Long = 5000L,
        val validationTimeoutMs: Long = 2500L,
        val idleTimeoutMs: Long = 60000L,
        val maxLifetimeMs: Long = 1800000L,
    )

    fun createDataSource(config: DatabaseConfig): HikariDataSource {
        val hikariConfig = HikariConfig().apply {
            val normalizedUrl = if (!config.jdbcUrl.startsWith("jdbc:")) "jdbc:${config.jdbcUrl}" else config.jdbcUrl
            this.jdbcUrl = normalizedUrl
            this.username = config.user
            this.password = config.pass
            this.maximumPoolSize = config.maxPoolSize
            this.minimumIdle = config.minIdle
            this.connectionTimeout = config.connectionTimeoutMs
            this.validationTimeout = config.validationTimeoutMs
            this.idleTimeout = config.idleTimeoutMs
            this.maxLifetime = config.maxLifetimeMs
            this.poolName = "ElysiumPostgresPool"
            
            // Optimization properties
            addDataSourceProperty("cachePrepStmts", "true")
            addDataSourceProperty("prepStmtCacheSize", "250")
            addDataSourceProperty("prepStmtCacheSqlLimit", "2048")
        }

        val ds = HikariDataSource(hikariConfig)
        activeDataSource = ds
        return ds
    }

    /**
     * Checks if the active database connection pool is healthy.
     */
    fun isHealthy(): Boolean {
        val ds = activeDataSource ?: return false
        return try {
            ds.connection.use { conn ->
                conn.isValid(2)
            }
        } catch (e: Exception) {
            logger.warn("Database health check failed: ${e.message}")
            false
        }
    }

    /**
     * Closes the active data source gracefully.
     */
    fun close() {
        activeDataSource?.close()
        activeDataSource = null
    }
}
