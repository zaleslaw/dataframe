package org.jetbrains.kotlinx.dataframe.io.db

import java.sql.SQLException

/**
 * Extracts the database type from the given JDBC URL.
 *
 * @param [url] the JDBC URL.
 * @return the corresponding [DbType].
 * @throws RuntimeException if the url is null.
 */
public fun extractDBTypeFromUrl(url: String?): DbType {
    if (url != null) {
        return when {
            H2.dbTypeInJdbcUrl in url -> H2
            MariaDb.dbTypeInJdbcUrl in url -> MariaDb
            MySql.dbTypeInJdbcUrl in url -> MySql
            Sqlite.dbTypeInJdbcUrl in url -> Sqlite
            PostgreSql.dbTypeInJdbcUrl in url -> PostgreSql
            MsSql.dbTypeInJdbcUrl in url -> MsSql
            else -> throw IllegalArgumentException(
                "Unsupported database type in the url: $url. " +
                    "Only H2, MariaDB, MySQL, MSSQL, SQLite and PostgreSQL are supported!"
            )
        }
    } else {
        throw SQLException("Database URL could not be null. The existing value is $url")
    }
}

/**
 * Retrieves the driver class name from the given JDBC URL.
 *
 * @param [url] The JDBC URL to extract the driver class name from.
 * @return The driver class name as a [String].
 */
public fun driverClassNameFromUrl(url: String): String {
    val dbType = extractDBTypeFromUrl(url)
    return dbType.driverClassName
}
