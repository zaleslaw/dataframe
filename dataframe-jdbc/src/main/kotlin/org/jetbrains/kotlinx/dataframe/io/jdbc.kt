package org.jetbrains.kotlinx.dataframe.io

import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.kotlinx.dataframe.AnyFrame
import org.jetbrains.kotlinx.dataframe.DataFrame
import org.jetbrains.kotlinx.dataframe.api.toDataFrame
import org.jetbrains.kotlinx.dataframe.codeGen.AbstractDefaultReadMethod
import org.jetbrains.kotlinx.dataframe.codeGen.DefaultReadDfMethod
import org.jetbrains.kotlinx.dataframe.impl.schema.DataFrameSchemaImpl
import org.jetbrains.kotlinx.dataframe.schema.ColumnSchema
import org.jetbrains.kotlinx.dataframe.schema.DataFrameSchema
import org.jetbrains.kotlinx.jupyter.api.Code
import java.io.File
import java.io.InputStream
import java.sql.Connection
import java.sql.DatabaseMetaData
import java.sql.ResultSet
import java.sql.ResultSetMetaData
import kotlin.reflect.typeOf

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

public data class JdbcColumn(val name: String, val type: String, val size: Int)

public fun DataFrame.Companion.readResultSet(resultSet: ResultSet, dbType: DbType): AnyFrame {
    val tableColumns = getTableColumns(resultSet)
    val data = fetchAndConvertDataFromResultSet(tableColumns, resultSet, dbType)
    return data.toDataFrame()
}

public fun DataFrame.Companion.readResultSet(resultSet: ResultSet, connection: Connection): AnyFrame {
    val url = connection.metaData.url
    val dbType = extractDBTypeFromURL(url)

    val tableColumns = getTableColumns(resultSet)
    val data = fetchAndConvertDataFromResultSet(tableColumns, resultSet, dbType)
    return data.toDataFrame()
}

public fun DataFrame.Companion.readAllTables(connection: Connection): List<AnyFrame> {
    val metaData = connection.metaData

    val tables = metaData.getTables(null, null, "%", null)

    val dataFrames = mutableListOf<AnyFrame>()

    while (tables.next()) {
        val tableName = tables.getString("TABLE_NAME")
        val dataFrame = readSqlTable(connection, "",tableName)
        dataFrames += dataFrame
    }

    return dataFrames
}

public fun DataFrame.Companion.readSqlQuery(connection: Connection, sqlQuery: String): AnyFrame {
    val url = connection.metaData.url
    val dbType = extractDBTypeFromURL(url)

    connection.createStatement().use { st ->
        st.executeQuery(sqlQuery).use { rs ->
            val tableColumns = getTableColumns(rs)
            val data = fetchAndConvertDataFromResultSet(tableColumns, rs, dbType)
            return data.toDataFrame()
        }
    }
}

public fun DataFrame.Companion.readSqlTable(connection: Connection, catalogName: String, tableName: String): AnyFrame {
    val preparedQuery = ("SELECT * FROM $tableName")

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
            val data = fetchAndConvertDataFromResultSet(tableColumns, rs, dbType)
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

        tableColumns += Pair(name, JdbcColumn(name, type, size))
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
        val size = columns.getInt("COLUMN_SIZE")
        tableColumns += Pair(name, JdbcColumn(name, type, size))
    }
    return tableColumns
}

private fun fetchAndConvertDataFromResultSet(
    tableColumns: MutableMap<String, JdbcColumn>,
    rs: ResultSet,
    dbType: DbType
): MutableMap<String, MutableList<Any?>> {
    // map<columnName; columndata>
    val data = mutableMapOf<String, MutableList<Any?>>()

    // init data
    tableColumns.forEach { (columnName, _) ->
        data[columnName] = mutableListOf()
    }

    var counter = 0
    while (rs.next()) {
        tableColumns.forEach { (columnName, jdbcColumn) ->
            data[columnName] = (data[columnName]!! + dbType.convertDataFromResultSet(rs, jdbcColumn)).toMutableList()
        }
        counter++
        if (counter % 1000 == 0) println("Loaded yet 1000, percentage = $counter")
    }
    return data
}

public fun extractDBTypeFromURL(url: String?): DbType {
    if (url != null) {
        return when {
            H2.jdbcName in url -> H2
            MariaDb.jdbcName in url -> MariaDb
            MySql.jdbcName in url -> MySql
            else -> MariaDb // probably better to add default SQL databases without vendor name
        }
    } else {
        throw RuntimeException("Database URL could not be null. The existing value is $url")
    }
}

// TODO: lools like we need here more then enum, but hierarchy of sealed classes with some fields
// Basic Type: supported database with mapping of types and jdbcProtocol names
public sealed class DbType(public val jdbcName: String) {
    public abstract fun convertDataFromResultSet(rs: ResultSet, jdbcColumn: JdbcColumn): Any?
    public abstract fun toColumnSchema(jdbcColumn: JdbcColumn): ColumnSchema
}

public object H2 : DbType("h2") {
    override fun convertDataFromResultSet(rs: ResultSet, jdbcColumn: JdbcColumn): Any? {
        return when (jdbcColumn.type) {
            "INTEGER" -> rs.getInt(jdbcColumn.name)
            "CHARACTER VARYING" -> rs.getString(jdbcColumn.name)
            "REAL", "NUMERIC" -> rs.getFloat(jdbcColumn.name)
            "MEDIUMTEXT" -> rs.getString(jdbcColumn.name)
            else -> null
        }
    }

    override fun toColumnSchema(jdbcColumn: JdbcColumn): ColumnSchema {
        return when (jdbcColumn.type) {
            "INTEGER" -> ColumnSchema.Value(typeOf<Int>())
            "CHARACTER VARYING" -> ColumnSchema.Value(typeOf<String>())
            "REAL", "NUMERIC" -> ColumnSchema.Value(typeOf<Float>())
            "MEDIUMTEXT" -> ColumnSchema.Value(typeOf<String>())
            else -> ColumnSchema.Value(typeOf<Any>())
        }
    }
}

public object MariaDb : DbType("mariadb") {
    override fun convertDataFromResultSet(rs: ResultSet, jdbcColumn: JdbcColumn): Any? {
        return when (jdbcColumn.type) {
            "INT" -> rs.getInt(jdbcColumn.name)
            "VARCHAR" -> rs.getString(jdbcColumn.name)
            "FLOAT" -> rs.getFloat(jdbcColumn.name)
            "MEDIUMTEXT" -> rs.getString(jdbcColumn.name)
            else -> null
        }
    }

    override fun toColumnSchema(jdbcColumn: JdbcColumn): ColumnSchema {
        return when (jdbcColumn.type) {
            "INTEGER" -> ColumnSchema.Value(typeOf<Int>())
            "CHARACTER VARYING" -> ColumnSchema.Value(typeOf<String>())
            "REAL", "NUMERIC" -> ColumnSchema.Value(typeOf<Float>())
            "MEDIUMTEXT" -> ColumnSchema.Value(typeOf<String>())
            else -> ColumnSchema.Value(typeOf<Any>())
        }
    }
}

public object MySql : DbType("mysql") {
    override fun convertDataFromResultSet(rs: ResultSet, jdbcColumn: JdbcColumn): Any? {
        return when (jdbcColumn.type) {
            "INT" -> rs.getInt(jdbcColumn.name)
            "VARCHAR" -> rs.getString(jdbcColumn.name)
            "FLOAT" -> rs.getFloat(jdbcColumn.name)
            "MEDIUMTEXT" -> rs.getString(jdbcColumn.name)
            else -> null
        }
    }

    override fun toColumnSchema(jdbcColumn: JdbcColumn): ColumnSchema {
        return when (jdbcColumn.type) {
            "INTEGER" -> ColumnSchema.Value(typeOf<Int>())
            "CHARACTER VARYING" -> ColumnSchema.Value(typeOf<String>())
            "REAL", "NUMERIC" -> ColumnSchema.Value(typeOf<Float>())
            "MEDIUMTEXT" -> ColumnSchema.Value(typeOf<String>())
            else -> ColumnSchema.Value(typeOf<Any>())
        }
    }
}

// TODO: slow solution could be optimized with batches control and fetching
// also better to manipulate whole row instead of asking by column, need to go to the rowset
// be sure that all the stuff is closed

// TODO: parser https://docs.oracle.com/javase/8/docs/api/java/sql/JDBCType.html

