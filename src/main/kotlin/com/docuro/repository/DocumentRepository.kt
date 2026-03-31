package com.docuro.repository

import com.docuro.domain.Document
import io.micronaut.data.jdbc.annotation.JdbcRepository
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.repository.CrudRepository
import java.util.UUID

@JdbcRepository(dialect = Dialect.POSTGRES)
interface DocumentRepository : CrudRepository<Document, UUID> {
    fun findByUserId(userId: UUID): List<Document>
    fun findByIdAndUserId(id: UUID, userId: UUID): Document?
}
