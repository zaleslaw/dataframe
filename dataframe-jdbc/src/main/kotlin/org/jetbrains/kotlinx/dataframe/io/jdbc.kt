package org.jetbrains.kotlinx.dataframe.io

import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.kotlinx.dataframe.AnyFrame
import org.jetbrains.kotlinx.dataframe.DataFrame
import org.jetbrains.kotlinx.dataframe.api.toDataFrame
import org.jetbrains.kotlinx.dataframe.codeGen.AbstractDefaultReadMethod
import org.jetbrains.kotlinx.dataframe.codeGen.DefaultReadDfMethod
import org.jetbrains.kotlinx.dataframe.impl.schema.DataFrameSchemaImpl
import org.jetbrains.kotlinx.dataframe.io.db.DbType
import org.jetbrains.kotlinx.dataframe.io.db.extractDBTypeFromURL
import org.jetbrains.kotlinx.dataframe.schema.DataFrameSchema
import org.jetbrains.kotlinx.jupyter.api.Code
import java.io.File
import java.io.InputStream
import java.sql.*

private val logger = KotlinLogging.logger {}

// JDBC is not a file format, we need a hierarchy here
public class JDBC : SupportedCodeGenerationFormat, SupportedDataFrameFormat {
    public override fun readDataFrame(stream: InputStream, header: List<String>): AnyFrame = DataFrame.readJDBC(stream)

    public override fun readDataFrame(file: File, header: List<String>): AnyFrame = DataFrame.readJDBC(file)
    override fun readCodeForGeneration(
        stream: InputStream,
        name: String,
        generateHelperCompanionObject: Boolean
    ): Code {
        TODO("Not yet implemented")
    }

    override fun readCodeForGeneration(
        file: File,
        name: String,
        generateHelperCompanionObject: Boolean
    ): Code {
        TODO("Not yet implemented")
    }

    override fun acceptsExtension(ext: String): Boolean = ext == "jdbc"

    override fun acceptsSample(sample: SupportedFormatSample): Boolean = true // Extension is enough

    override val testOrder: Int = 40000

    override fun createDefaultReadMethod(pathRepresentation: String?): DefaultReadDfMethod {
        return DefaultReadJdbcMethod(pathRepresentation)
    }
}

private fun DataFrame.Companion.readJDBC(stream: File): DataFrame<*> {
    TODO("Not yet implemented")
}

private fun DataFrame.Companion.readJDBC(stream: InputStream): DataFrame<*> {
    TODO("Not yet implemented")
}

internal class DefaultReadJdbcMethod(path: String?) : AbstractDefaultReadMethod(path, MethodArguments.EMPTY, readJDBC)

private const val readJDBC = "readJDBC"

public data class JdbcColumn(val name: String, val sqlType: String, val jdbcType: Int, val size: Int)

public fun DataFrame.Companion.readResultSet(resultSet: ResultSet, dbType: DbType): AnyFrame {
    val tableColumns = getTableColumns(resultSet)
    val data = fetchAndConvertDataFromResultSet(tableColumns, resultSet, dbType, Int.MIN_VALUE)
    return data.toDataFrame()
}

public fun DataFrame.Companion.readResultSet(resultSet: ResultSet, connection: Connection, limit: Int): AnyFrame {
    val url = connection.metaData.url
    val dbType = extractDBTypeFromURL(url)

    val tableColumns = getTableColumns(resultSet)
    val data = fetchAndConvertDataFromResultSet(tableColumns, resultSet, dbType, limit)
    return data.toDataFrame()
}

public fun DataFrame.Companion.readResultSet(resultSet: ResultSet, connection: Connection): AnyFrame {
    return readResultSet(resultSet, connection, Int.MIN_VALUE)
}

// TODO: need to filter standard tables for each database
public fun DataFrame.Companion.readAllTables(connection: Connection, limit: Int): List<AnyFrame> {
    val metaData = connection.metaData

    val tables = metaData.getTables(null, null, "%", null)

    val dataFrames = mutableListOf<AnyFrame>()

    while (tables.next()) {
        val tableName = tables.getString("TABLE_NAME")
        val dataFrame = readSqlTable(connection, "",tableName, limit)
        dataFrames += dataFrame
    }

    return dataFrames
}

public fun DataFrame.Companion.readSqlQuery(connection: Connection, sqlQuery: String): AnyFrame {
    return readSqlQuery(connection, sqlQuery, Int.MIN_VALUE)
}

public fun DataFrame.Companion.readSqlQuery(connection: Connection, sqlQuery: String, limit: Int): AnyFrame {
    val url = connection.metaData.url
    val dbType = extractDBTypeFromURL(url)

    var internalSqlQuery = sqlQuery
    if (limit > 0) internalSqlQuery += " LIMIT $limit"

    connection.createStatement().use { st ->
        st.executeQuery(internalSqlQuery).use { rs ->
            val tableColumns = getTableColumns(rs)
            val data = fetchAndConvertDataFromResultSet(tableColumns, rs, dbType, Int.MIN_VALUE)
            return data.toDataFrame()
        }
    }
}

public data class DatabaseConfiguration(val user: String = "", val password: String = "", val url:String)

public fun DataFrame.Companion.readSqlTable(dbConfig: DatabaseConfiguration, tableName: String): AnyFrame {
    Class.forName("org.mariadb.jdbc.Driver")
    DriverManager.getConnection(dbConfig.url, dbConfig.user, dbConfig.password).use { connection ->
        return readSqlTable(connection, "", tableName, Int.MIN_VALUE)
    }
}

public fun DataFrame.Companion.readSqlTable(dbConfig: DatabaseConfiguration, tableName: String, limit: Int): AnyFrame {
    Class.forName("org.mariadb.jdbc.Driver")
    DriverManager.getConnection(dbConfig.url, dbConfig.user, dbConfig.password).use { connection ->
        return readSqlTable(connection, "", tableName, limit)
    }
}

// TODO: add methods for stats for SQL tables as dataframe or intermediate objects like Table (name, count)

public fun DataFrame.Companion.readSqlTable(connection: Connection, catalogName: String, tableName: String): AnyFrame {
    return readSqlTable(connection, catalogName, tableName, Int.MIN_VALUE)
}

public fun DataFrame.Companion.readSqlTable(connection: Connection, catalogName: String, tableName: String, limit: Int): AnyFrame {
    var preparedQuery = "SELECT * FROM $tableName"
    if (limit > 0) preparedQuery += " LIMIT $limit"

    val url = connection.metaData.url
    val dbType = extractDBTypeFromURL(url)

    connection.createStatement().use { st ->
        logger.debug { "Connection with url:${url} is established successfully." }
        val tableColumns = getTableColumns(connection, tableName)

        // TODO: dynamic SQL names - no protection from SQL injection
        // What if just try to match it before to the known SQL table names and if not to reject
        // What if check the name on the SQL commands and ;; commas to reject and throw exception

        // LIMIT 1000 because is very slow to copy into dataframe the whole table (do we need a fetch here? or limit)
        // or progress bar
        // ask the COUNT(*) for full table
        st.executeQuery(
            preparedQuery // TODO: work with limits correctly
            //
        ).use { rs ->
            val data = fetchAndConvertDataFromResultSet(tableColumns, rs, dbType, limit)
            return data.toDataFrame()
        }
    }
}

public fun DataFrame.Companion.getSchemaForResultSet(resultSet: ResultSet, connection: Connection): DataFrameSchema {
    val url = connection.metaData.url
    val dbType = extractDBTypeFromURL(url)

    val tableColumns = getTableColumns(resultSet)
    return buildSchemaByTableColumns(tableColumns, dbType)
}

public fun DataFrame.Companion.getSchemaForResultSet(resultSet: ResultSet, dbType: DbType): DataFrameSchema {
    val tableColumns = getTableColumns(resultSet)
    return buildSchemaByTableColumns(tableColumns, dbType)
}

public fun DataFrame.Companion.getSchemaForAllTables(connection: Connection): List<DataFrameSchema> {
    val metaData = connection.metaData

    val tables = metaData.getTables(null, null, "%", null)

    val dataFrameSchemas = mutableListOf<DataFrameSchema>()

    while (tables.next()) {
        val tableName = tables.getString("TABLE_NAME")
        val dataFrameSchema = getSchemaForSqlTable(connection, "",tableName)
        dataFrameSchemas += dataFrameSchema
    }

    return dataFrameSchemas
}

public fun DataFrame.Companion.getSchemaForSqlTable(dbConfig: DatabaseConfiguration, tableName: String): DataFrameSchema {
    Class.forName("org.mariadb.jdbc.Driver") // TODO: doesn't work because mariadb or special dependency could not be added to the module
    DriverManager.getConnection(dbConfig.url, dbConfig.user, dbConfig.password).use { connection ->
        return getSchemaForSqlTable(connection, "", tableName)
    }
}

public fun DataFrame.Companion.getSchemaForSqlTable(
    connection: Connection,
    catalogName: String,
    tableName: String
): DataFrameSchema {
    val url = connection.metaData.url
    val dbType = extractDBTypeFromURL(url)

    connection.createStatement().use {
        logger.debug { "Connection with url:${connection.metaData.url} is established successfully." }

        val tableColumns = getTableColumns(connection, tableName)

        return buildSchemaByTableColumns(tableColumns, dbType)
    }
}

public fun DataFrame.Companion.getSchemaForSqlQuery(connection: Connection, sqlQuery: String): DataFrameSchema {
    val url = connection.metaData.url
    val dbType = extractDBTypeFromURL(url)

    connection.createStatement().use { st ->
        st.executeQuery(sqlQuery).use { rs ->
            val tableColumns = getTableColumns(rs)
            return buildSchemaByTableColumns(tableColumns, dbType)
        }
    }
}

private fun buildSchemaByTableColumns(tableColumns: MutableMap<String, JdbcColumn>, dbType: DbType): DataFrameSchema {
    val schemaColumns = tableColumns.map {
        Pair(it.key, dbType.toColumnSchema(it.value))
    }.toMap()

    return DataFrameSchemaImpl(
        columns = schemaColumns
    )
}

private fun getTableColumns(rs: ResultSet): MutableMap<String, JdbcColumn> {
    val metaData: ResultSetMetaData = rs.metaData
    val numberOfColumns: Int = metaData.columnCount

    val tableColumns = mutableMapOf<String, JdbcColumn>()

    for (i in 1 until numberOfColumns + 1) {
        val name = metaData.getColumnName(i)
        val size = metaData.getColumnDisplaySize(i)
        val type = metaData.getColumnTypeName(i)
        val jdbcType = metaData.getColumnType(i)

        tableColumns += Pair(name, JdbcColumn(name, type, jdbcType, size))
    }
    return tableColumns
}

private fun getTableColumns(connection: Connection, tableName: String): MutableMap<String, JdbcColumn> {
    val dbMetaData: DatabaseMetaData = connection.metaData
    val columns: ResultSet = dbMetaData.getColumns(null, null, tableName, null)
    val tableColumns = mutableMapOf<String, JdbcColumn>()

    while (columns.next()) {
        val name = columns.getString("COLUMN_NAME")
        val type = columns.getString("TYPE_NAME")
        val jdbcType = columns.getInt("DATA_TYPE")
        val size = columns.getInt("COLUMN_SIZE")
        tableColumns += Pair(name, JdbcColumn(name, type, jdbcType, size))
    }
    return tableColumns
}

private fun fetchAndConvertDataFromResultSet(
    tableColumns: MutableMap<String, JdbcColumn>,
    rs: ResultSet,
    dbType: DbType,
    limit: Int
): MutableMap<String, MutableList<Any?>> {
    // map<columnName; columndata>
    val data = mutableMapOf<String, MutableList<Any?>>()

    // init data
    tableColumns.forEach { (columnName, _) ->
        data[columnName] = mutableListOf()
    }

    var counter = 0

    if (limit > 0) {
        while (rs.next() && counter < limit) {
            handleRow(tableColumns, data, dbType, rs)
            counter++
            if (counter % 1000 == 0) logger.debug { "Loaded $counter rows." }
        }
    } else {
        while (rs.next()) {
            handleRow(tableColumns, data, dbType, rs)
            counter++
            if (counter % 1000 == 0) logger.debug { "Loaded $counter rows." }
        }
    }

    return data
}

private fun handleRow(
    tableColumns: MutableMap<String, JdbcColumn>,
    data: MutableMap<String, MutableList<Any?>>,
    dbType: DbType,
    rs: ResultSet
) {
    tableColumns.forEach { (columnName, jdbcColumn) ->
        data[columnName] = (data[columnName]!! + dbType.convertDataFromResultSet(rs, jdbcColumn)).toMutableList()
    }
}


// TODO: slow solution could be optimized with batches control and fetching
// also better to manipulate whole row instead of asking by column, need to go to the rowset
// be sure that all the stuff is closed

// TODO: parser https://docs.oracle.com/javase/8/docs/api/java/sql/JDBCType.html

// TODO: counter
/*
// Your original query
String originalQuery = "SELECT * FROM your_table_name WHERE column_name = 'some_value'";

// Query to count the number of rows
String countQuery = "SELECT COUNT(*) as count FROM (" + originalQuery + ") AS count_query";

// Create a statement and execute the count query
Statement countStatement = connection.createStatement();
ResultSet countResultSet = countStatement.executeQuery(countQuery);

// Get the row count
int rowCount = 0;
if (countResultSet.next()) {
    rowCount = countResultSet.getInt("count");
}*/



