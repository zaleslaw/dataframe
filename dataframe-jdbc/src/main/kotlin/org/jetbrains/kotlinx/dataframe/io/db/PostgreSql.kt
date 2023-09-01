package org.jetbrains.kotlinx.dataframe.io.db

import org.jetbrains.kotlinx.dataframe.io.JdbcColumn
import org.jetbrains.kotlinx.dataframe.schema.ColumnSchema
import java.sql.ResultSet
import kotlin.reflect.typeOf

/**
 * NOTE: all date and timestamp related types converted to String to avoid java.sql.* types
 */
public object PostgreSql : DbType("postgresql") {
    override fun convertDataFromResultSet(rs: ResultSet, jdbcColumn: JdbcColumn): Any? {
        // TODO: improve mapping with the https://www.instaclustr.com/blog/postgresql-data-types-mappings-to-sql-jdbc-and-java-data-types/
        val name = jdbcColumn.name
        return when (jdbcColumn.sqlType) {
            "serial" -> rs.getInt(name)
            "int8", "bigint", "bigserial" -> rs.getLong(name)
            "bool" -> rs.getBoolean(name)
            "box" -> rs.getString(name)
            "bytea" -> rs.getBytes(name)
            "character", "bpchar" -> rs.getString(name)
            "circle" -> rs.getString(name)
            "date" -> rs.getDate(name).toString()
            "float8", "double precision" -> rs.getDouble(name)
            "int4", "integer" -> rs.getInt(name)
            "interval" -> rs.getString(name)
            "json", "jsonb" -> rs.getString(name)
            "line" -> rs.getString(name)
            "lseg" -> rs.getString(name)
            "macaddr" -> rs.getString(name)
            "money" -> rs.getString(name)
            "numeric" -> rs.getString(name)
            "path" -> rs.getString(name)
            "point" -> rs.getString(name)
            "polygon" -> rs.getString(name)
            "float4", "real" -> rs.getFloat(name)
            "int2", "smallint" -> rs.getShort(name)
            "smallserial" -> rs.getInt(name)
            "text" -> rs.getString(name)
            "time" -> rs.getString(name)
            "timetz", "time with time zone" -> rs.getString(name)
            "timestamp" -> rs.getString(name)
            "timestamptz", "timestamp with time zone" -> rs.getString(name)
            "uuid" -> rs.getString(name)
            "xml" -> rs.getString(name)
            else -> throw IllegalArgumentException("Unsupported PostgreSQL type: ${jdbcColumn.sqlType}")
        }
    }

    override fun toColumnSchema(jdbcColumn: JdbcColumn): ColumnSchema {
        return when (jdbcColumn.sqlType) {
            "serial" -> ColumnSchema.Value(typeOf<Int>())
            "int8", "bigint", "bigserial" -> ColumnSchema.Value(typeOf<Long>())
            "bool" -> ColumnSchema.Value(typeOf<Boolean>())
            "box" -> ColumnSchema.Value(typeOf<String>())
            "bytea" -> ColumnSchema.Value(typeOf<ByteArray>())
            "character", "bpchar" -> ColumnSchema.Value(typeOf<String>())
            "circle" -> ColumnSchema.Value(typeOf<String>())
            "date" -> ColumnSchema.Value(typeOf<String>())
            "float8", "double precision" -> ColumnSchema.Value(typeOf<Double>())
            "int4", "integer" -> ColumnSchema.Value(typeOf<Int>())
            "interval" -> ColumnSchema.Value(typeOf<String>())
            "json", "jsonb" -> ColumnSchema.Value(typeOf<String>())
            "line" -> ColumnSchema.Value(typeOf<String>())
            "lseg" -> ColumnSchema.Value(typeOf<String>())
            "macaddr" -> ColumnSchema.Value(typeOf<String>())
            "money" -> ColumnSchema.Value(typeOf<String>())
            "numeric" -> ColumnSchema.Value(typeOf<String>())
            "path" -> ColumnSchema.Value(typeOf<String>())
            "point" -> ColumnSchema.Value(typeOf<String>())
            "polygon" -> ColumnSchema.Value(typeOf<String>())
            "float4", "real" -> ColumnSchema.Value(typeOf<Float>())
            "int2", "smallint" -> ColumnSchema.Value(typeOf<Float>())
            "smallserial" -> ColumnSchema.Value(typeOf<Int>())
            "text" -> ColumnSchema.Value(typeOf<String>())
            "time" -> ColumnSchema.Value(typeOf<String>())
            "timetz", "time with time zone" -> ColumnSchema.Value(typeOf<String>())
            "timestamp" -> ColumnSchema.Value(typeOf<String>())
            "timestamptz", "timestamp with time zone" -> ColumnSchema.Value(typeOf<String>())
            "uuid" -> ColumnSchema.Value(typeOf<String>())
            "xml" -> ColumnSchema.Value(typeOf<String>())
            else -> throw IllegalArgumentException("Unsupported PostgreSQL type: ${jdbcColumn.sqlType}")
        }
    }
}
