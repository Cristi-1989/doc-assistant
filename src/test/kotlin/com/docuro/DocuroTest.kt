package com.docuro

import io.micronaut.runtime.EmbeddedApplication
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import io.micronaut.test.support.TestPropertyProvider
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.testcontainers.containers.GenericContainer
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.utility.DockerImageName

/**
 * Integration smoke test — verifies the application context starts successfully
 * with real PostgreSQL and MinIO containers.
 *
 * Containers are started eagerly in the companion object so they are running
 * before TestPropertyProvider.getProperties() is called. Using @Testcontainers
 * + @Container causes an ordering conflict with @MicronautTest's BeforeAllCallback,
 * which would call getProperties() before containers finish starting. Ryuk handles
 * cleanup automatically when the JVM exits.
 *
 * Requires Docker.
 */
@MicronautTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DocuroTest : TestPropertyProvider {

    @Inject
    lateinit var application: EmbeddedApplication<*>

    @Test
    fun `application context starts and is running`() {
        assertTrue(application.isRunning)
    }

    override fun getProperties(): Map<String, String> = mapOf(
        // Construct JDBC URL manually — postgres.jdbcUrl can return a partial value if the
        // property is read before the container finishes starting; getMappedPort is always safe.
        "datasources.default.url" to
                "jdbc:postgresql://${postgres.host}:${postgres.getMappedPort(5432)}/${postgres.databaseName}",
        "datasources.default.username" to postgres.username,
        "datasources.default.password" to postgres.password,
        "aws.services.s3.endpoint-override" to "http://${minio.host}:${minio.getMappedPort(9000)}",
        "docuro.gemini.api-key" to "test-key",
    )

    companion object {
        val postgres: PostgreSQLContainer = PostgreSQLContainer(DockerImageName.parse("postgres:18"))
            .withDatabaseName("docuro")
            .withUsername("docuro")
            .withPassword("docuro")
            .also { it.start() }

        val minio: GenericContainer<*> = GenericContainer("minio/minio:latest")
            .withCommand("server /data")
            .withEnv("MINIO_ROOT_USER", "minioadmin")
            .withEnv("MINIO_ROOT_PASSWORD", "minioadmin")
            .withExposedPorts(9000)
            .waitingFor(Wait.forLogMessage(".*API.*\\n", 1))
            .also { it.start() }
    }
}
