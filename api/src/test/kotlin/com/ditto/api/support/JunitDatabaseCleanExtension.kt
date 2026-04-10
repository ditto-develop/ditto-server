package com.ditto.api.support

import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.springframework.test.context.junit.jupiter.SpringExtension
import javax.sql.DataSource

class JunitDatabaseCleanExtension : BeforeEachCallback {

    override fun beforeEach(context: ExtensionContext) {
        val dataSource = SpringExtension.getApplicationContext(context).getBean(DataSource::class.java)
        dataSource.connection.use { connection ->
            connection.createStatement().use { statement ->
                statement.execute("SET REFERENTIAL_INTEGRITY FALSE")
                val tables = statement.executeQuery(
                    "SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = 'PUBLIC'",
                )
                val tableNames = mutableListOf<String>()
                while (tables.next()) {
                    tableNames.add(tables.getString("TABLE_NAME"))
                }
                tables.close()
                tableNames.forEach { statement.execute("TRUNCATE TABLE \"$it\"") }
                statement.execute("SET REFERENTIAL_INTEGRITY TRUE")
            }
        }
    }
}
