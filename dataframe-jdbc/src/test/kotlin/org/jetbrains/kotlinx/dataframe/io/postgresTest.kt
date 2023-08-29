package org.jetbrains.kotlinx.dataframe.io

import org.jetbrains.kotlinx.dataframe.DataFrame
import org.jetbrains.kotlinx.dataframe.api.cast
import org.jetbrains.kotlinx.dataframe.api.print
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.BeforeClass
import org.junit.Test
import org.postgresql.util.PGobject
import java.math.BigDecimal
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException
import java.util.*


private const val URL = "jdbc:postgresql://localhost:5432/test"
private const val USER_NAME = "postgres"
private const val PASSWORD = "pass"

data class Table1(
    val id: Int,
    val bigintCol: Long,
    val bigserialCol: Long,
    val bitCol: Boolean,
    val bitVaryingCol: String,
    val booleanCol: Boolean,
    val boxCol: String,
    val byteaCol: ByteArray,
    val characterCol: String,
    val characterNCol: String,
    val charCol: String,
    val cidrCol: String,
    val circleCol: String,
    val dateCol: java.sql.Date,
    val doubleCol: Double,
    val inetCol: String,
    val integerCol: Int,
    val intervalCol: String,
    val jsonCol: String,
    val jsonbCol: String
)

data class Table2(
    val id: Int,
    val lineCol: String,
    val lsegCol: String,
    val macaddrCol: String,
    val moneyCol: String,
    val numericCol: String,
    val pathCol: String,
    val pointCol: String,
    val polygonCol: String,
    val realCol: Float,
    val smallintCol: Short,
    val smallserialCol: Int,
    val serialCol: Int,
    val textCol: String,
    val timeCol: String,
    val timeWithZoneCol: String,
    val timestampCol: String,
    val timestampWithZoneCol: String,
    val tsvectorCol: String,
    val uuidCol: String,
    val xmlCol: String
)

class PostgresTest {
    companion object {
        private lateinit var connection: Connection

        @BeforeClass
        @JvmStatic
        fun setUpClass() {
            connection = DriverManager.getConnection(URL, USER_NAME, PASSWORD)

            connection.createStatement().execute(
                """
                  CREATE TABLE IF NOT EXISTS table1 (
                id serial PRIMARY KEY,
                bigint_col bigint,
                bigserial_col bigserial,
                boolean_col boolean,
                box_col box,
                bytea_col bytea,
                character_col character,
                character_n_col character(10),
                char_col char,
                circle_col circle,
                date_col date,
                double_col double precision,
                integer_col integer,
                interval_col interval,
                json_col json,
                jsonb_col jsonb
            )
            """.trimIndent()
            )

            // Create table Sale
            connection.createStatement().execute(
                """
                CREATE TABLE IF NOT EXISTS table2 (
                id serial PRIMARY KEY,
                line_col line,
                lseg_col lseg,
                macaddr_col macaddr,
                money_col money,
                numeric_col numeric,
                path_col path,
                point_col point,
                polygon_col polygon,
                real_col real,
                smallint_col smallint,
                smallserial_col smallserial,
                serial_col serial,
                text_col text,
                time_col time,
                time_with_zone_col time with time zone,
                timestamp_col timestamp,
                timestamp_with_zone_col timestamp with time zone,
                tsquery_col tsquery,
                tsvector_col tsvector,
                txid_snapshot_col txid_snapshot,
                uuid_col uuid,
                xml_col xml
            )
            """.trimIndent()
            )

            val insertData1 = """
            INSERT INTO table1 (
                bigint_col, bigserial_col,  boolean_col, 
                box_col, bytea_col, character_col, character_n_col, char_col, 
                 circle_col, date_col, double_col, 
                integer_col, interval_col, json_col, jsonb_col
            ) VALUES (?, ?,  ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """
            val insertData2 = """
            INSERT INTO table2 (
                line_col, lseg_col, macaddr_col, money_col, numeric_col, 
                path_col, point_col, polygon_col, real_col, smallint_col, 
                smallserial_col, serial_col, text_col, time_col, 
                time_with_zone_col, timestamp_col, timestamp_with_zone_col, 
                uuid_col, xml_col
            ) VALUES (?, ?, ?, ?, ?, ?,  ?, ?, ?, ?,  ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """

            connection.prepareStatement(insertData1).use { statement1 ->
                // Insert data into table1
                for (i in 1..3) {
                    statement1.setLong(1, i * 1000L)
                    statement1.setLong(2, 1000000000L + i)
                    statement1.setBoolean(3, i % 2 == 1)
                    statement1.setObject(4, org.postgresql.geometric.PGbox("(1,1),(2,2)"))
                    statement1.setBytes(5, byteArrayOf(1, 2, 3))
                    statement1.setString(6, "A")
                    statement1.setString(7, "Hello")
                    statement1.setString(8, "A")
                    statement1.setObject(9, org.postgresql.geometric.PGcircle("<(1,2),3>"))
                    statement1.setDate(10, java.sql.Date.valueOf("2023-08-01"))
                    statement1.setDouble(11, 12.34)
                    statement1.setInt(12, 12345)
                    statement1.setObject(13, org.postgresql.util.PGInterval("1 year"))

                    val jsonbObject = PGobject()
                    jsonbObject.type = "jsonb"
                    jsonbObject.value = "{\"key\": \"value\"}"

                    statement1.setObject(14, jsonbObject)
                    statement1.setObject(15, jsonbObject)
                    statement1.executeUpdate()
                }
            }

            connection.prepareStatement(insertData2).use { statement2 ->
                // Insert data into table2
                for (i in 1..3) {
                    statement2.setObject(1, org.postgresql.geometric.PGline( "{1,2,3}"))
                    statement2.setObject(2, org.postgresql.geometric.PGlseg("[(-1,0),(1,0)]"))

                    val macaddrObject = PGobject()
                    macaddrObject.type = "macaddr"
                    macaddrObject.value = "00:00:00:00:00:0$i"

                    statement2.setObject(3, macaddrObject)
                    statement2.setBigDecimal(4, BigDecimal("123.45"))
                    statement2.setBigDecimal(5, BigDecimal("12.34"))
                    statement2.setObject(6, org.postgresql.geometric.PGpath("((1,2),(3,4))"))
                    statement2.setObject(7, org.postgresql.geometric.PGpoint("(1,2)"))
                    statement2.setObject(8, org.postgresql.geometric.PGpolygon("((1,1),(2,2),(3,3))"))
                    statement2.setFloat(9, 12.34f)
                    statement2.setShort(10, (i * 100).toShort())
                    statement2.setInt(11, 1000 + i)
                    statement2.setInt(12, 1000000 + i)
                    statement2.setString(13, "Text data $i")
                    statement2.setTime(14, java.sql.Time.valueOf("12:34:56"))

                    statement2.setTimestamp(15, java.sql.Timestamp(System.currentTimeMillis()))
                    statement2.setTimestamp(16, java.sql.Timestamp(System.currentTimeMillis()))
                    statement2.setTimestamp(17, java.sql.Timestamp(System.currentTimeMillis()))

                    statement2.setObject(18, UUID.randomUUID(), java.sql.Types.OTHER)
                    val xmlObject = PGobject()
                    xmlObject.type = "xml"
                    xmlObject.value = "<root><element>data</element></root>"

                    statement2.setObject(19, xmlObject)
                    statement2.executeUpdate()
                }
            }
        }

        @AfterClass
        @JvmStatic
        fun tearDownClass() {
            try {
                //TODO: add drop tables statements
                connection.close()
            } catch (e: SQLException) {
                e.printStackTrace()
            }
        }
    }

    @Test
    fun `basic test for reading sql tables`() {
        connection.use { connection ->
            val df1 = DataFrame.readSqlTable(connection, "dsdfs", "table1").cast<Table1>()
            df1.print()
            assertEquals(3, df1.rowsCount())

            val df2 = DataFrame.readSqlTable(connection, "dsdfs", "table2").cast<Table2>()
            df2.print()
            assertEquals(3, df2.rowsCount())
        }
    }
}
