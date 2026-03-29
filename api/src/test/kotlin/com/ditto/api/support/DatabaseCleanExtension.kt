package com.ditto.api.support

import io.kotest.core.listeners.BeforeEachListener
import io.kotest.core.test.TestCase
import javax.sql.DataSource

class DatabaseCleanExtension(
    private val dataSource: DataSource,
) : BeforeEachListener {
    override suspend fun beforeEach(testCase: TestCase) {
        dataSource.connection.use { connection ->
            connection.createStatement().use { statement ->
                statement.execute("SET REFERENTIAL_INTEGRITY FALSE")
                val tables = statement.executeQuery(
                    "SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES WHERE UPPER(TABLE_SCHEMA) = 'PUBLIC'"
                )
                val tableNames = mutableListOf<String>()
                while (tables.next()) { tableNames.add(tables.getString("TABLE_NAME")) }
                tables.close()
                tableNames.forEach { statement.execute("TRUNCATE TABLE \"$it\"") }
                statement.execute("SET REFERENTIAL_INTEGRITY TRUE")
            }
        }
    }
}
