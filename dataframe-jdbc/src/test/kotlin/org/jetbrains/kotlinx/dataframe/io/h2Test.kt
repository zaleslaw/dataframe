package org.jetbrains.kotlinx.dataframe.io

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.h2.jdbc.JdbcSQLSyntaxErrorException
import org.intellij.lang.annotations.Language
import org.jetbrains.kotlinx.dataframe.DataFrame
import org.jetbrains.kotlinx.dataframe.annotations.DataSchema
import org.jetbrains.kotlinx.dataframe.api.*
import org.jetbrains.kotlinx.dataframe.io.db.H2
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.Test
import java.math.BigDecimal
import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet
import java.sql.SQLException
import kotlin.reflect.typeOf

private const val URL = "jdbc:h2:mem:test;DB_CLOSE_DELAY=-1;MODE=MySQL;DATABASE_TO_UPPER=false"

@DataSchema
interface Customer {
    val id: Int?
    val name: String?
    val age: Int?
}

@DataSchema
interface Sale {
    val id: Int?
    val customerId: Int?
    val amount: Double
}

@DataSchema
interface CustomerSales {
    val customerName: String?
    val totalSalesAmount: Double?
}

@DataSchema
interface TestTableData {
    val characterCol: String?
    val characterVaryingCol: String?
    val characterLargeObjectCol: String?
    val mediumTextCol: String?
    val varcharIgnoreCaseCol: String?
    val binaryCol: ByteArray?
    val binaryVaryingCol: ByteArray?
    val binaryLargeObjectCol: ByteArray?
    val booleanCol: Boolean?
    val tinyIntCol: Int?
    val smallIntCol: Int?
    val integerCol: Int?
    val bigIntCol: Long?
    val numericCol: BigDecimal?
    val realCol: Float?
    val doublePrecisionCol: Double?
    val decFloatCol: BigDecimal?
    val dateCol: String?
    val timeCol: String?
    val timeWithTimeZoneCol: String?
    val timestampCol: String?
    val timestampWithTimeZoneCol: String?
    val intervalCol: String?
    val javaObjectCol: Any?
    val enumCol: String?
    val jsonCol: String?
    val uuidCol: String?
}

class JdbcTest {
    companion object {
        private lateinit var connection: Connection

        @BeforeClass
        @JvmStatic
        fun setUpClass() {
            connection =
                DriverManager.getConnection(URL)

            // Create table Customer
            @Language("SQL")
            val createCustomerTableQuery = """
                CREATE TABLE Customer (
                    id INT PRIMARY KEY,
                    name VARCHAR(50),
                    age INT
                )
            """

            connection.createStatement().execute(createCustomerTableQuery)

            // Create table Sale
            @Language("SQL")
            val createSaleTableQuery = """
                CREATE TABLE Sale (
                    id INT PRIMARY KEY,
                    customerId INT,
                    amount DECIMAL(10, 2) NOT NULL
                )
            """

            connection.createStatement().execute(
                createSaleTableQuery
            )

            // add data to the Customer table
            connection.createStatement().execute("INSERT INTO Customer (id, name, age) VALUES (1, 'John', 40)")
            connection.createStatement().execute("INSERT INTO Customer (id, name, age) VALUES (2, 'Alice', 25)")
            connection.createStatement().execute("INSERT INTO Customer (id, name, age) VALUES (3, 'Bob', 47)")
            connection.createStatement().execute("INSERT INTO Customer (id, name, age) VALUES (4, NULL, NULL)")

            // add data to the Sale table
            connection.createStatement().execute("INSERT INTO Sale (id, customerId, amount) VALUES (1, 1, 100.50)")
            connection.createStatement().execute("INSERT INTO Sale (id, customerId, amount) VALUES (2, 2, 50.00)")
            connection.createStatement().execute("INSERT INTO Sale (id, customerId, amount) VALUES (3, 1, 75.25)")
            connection.createStatement().execute("INSERT INTO Sale (id, customerId, amount) VALUES (4, 3, 35.15)")
        }

        @AfterClass
        @JvmStatic
        fun tearDownClass() {
            try {
                connection.close()
            } catch (e: SQLException) {
                e.printStackTrace()
            }
        }
    }

    @Test
    fun `read from empty table`() {
        @Language("SQL")
        val createTableQuery = """
                CREATE TABLE EmptyTestTable (
                    characterCol CHAR(10),
                    characterVaryingCol VARCHAR(20)
                )
            """

        connection.createStatement().execute(createTableQuery.trimIndent())

        val tableName = "EmptyTestTable"

        val df = DataFrame.readSqlTable(connection, tableName)
        df.rowsCount() shouldBe 0

        val dataSchema = DataFrame.getSchemaForSqlTable(connection, tableName)
        dataSchema.columns.size shouldBe 2
        dataSchema.columns["characterCol"]!!.type shouldBe typeOf<Char?>()
    }

    @Test
    fun `read from huge table`() {
        @Language("SQL")
        val createTableQuery = """
                CREATE TABLE TestTable (
                    characterCol CHAR(10),
                    characterVaryingCol VARCHAR(20),
                    characterLargeObjectCol CLOB,
                    mediumTextCol CLOB,
                    varcharIgnoreCaseCol VARCHAR_IGNORECASE(30),
                    binaryCol BINARY(8),
                    binaryVaryingCol VARBINARY(16),
                    binaryLargeObjectCol BLOB,
                    booleanCol BOOLEAN,
                    tinyIntCol TINYINT,
                    smallIntCol SMALLINT,
                    integerCol INT,
                    bigIntCol BIGINT,
                    numericCol NUMERIC(10, 2),
                    realCol REAL,
                    doublePrecisionCol DOUBLE PRECISION,
                    decFloatCol DECFLOAT(16),
                    dateCol DATE,
                    timeCol TIME,
                    timeWithTimeZoneCol TIME WITH TIME ZONE,
                    timestampCol TIMESTAMP,
                    timestampWithTimeZoneCol TIMESTAMP WITH TIME ZONE,
                    javaObjectCol OBJECT,
                    enumCol VARCHAR(10),
                    jsonCol JSON,
                    uuidCol UUID
                )
            """

        connection.createStatement().execute(createTableQuery.trimIndent())

        connection.prepareStatement(
            """
                INSERT INTO TestTable VALUES (
                    'ABC', 'XYZ', 'Long text data for CLOB', 'Medium text data for CLOB',
                    'Varchar IgnoreCase', X'010203', X'040506', X'070809',
                    TRUE, 1, 100, 1000, 100000,
                    123.45, 1.23, 3.14, 2.71,
                    '2023-07-20', '08:30:00', '18:15:00', '2023-07-19 12:45:30',
                    '2023-07-18 12:45:30', NULL,
                    'Option1', '{"key": "value"}', '123e4567-e89b-12d3-a456-426655440000'
                )
            """.trimIndent()
        ).executeUpdate()

        connection.prepareStatement(
            """
                INSERT INTO TestTable VALUES (
                    'DEF', 'LMN', 'Another CLOB data', 'Different CLOB data',
                    'Another Varchar', X'101112', X'131415', X'161718',
                    FALSE, 2, 200, 2000, 200000,
                    234.56, 2.34, 4.56, 3.14,
                    '2023-07-21', '14:30:00', '22:45:00', '2023-07-20 18:15:30',
                    '2023-07-19 18:15:30', NULL,
                    'Option2', '{"key": "another_value"}', '234e5678-e89b-12d3-a456-426655440001'
                )
            """.trimIndent()
        ).executeUpdate()

        connection.prepareStatement(
            """
                INSERT INTO TestTable VALUES (
                    'GHI', 'OPQ', 'Third CLOB entry', 'Yet another CLOB data',
                    'Yet Another Varchar', X'192021', X'222324', X'252627',
                    TRUE, 3, 300, 3000, 300000,
                    345.67, 3.45, 5.67, 4.71,
                    '2023-07-22', '20:45:00', '03:30:00', '2023-07-21 23:45:15',
                    '2023-07-20 23:45:15', NULL,
                    'Option3', '{ "person": { "name": "John Doe", "age": 30 }, ' ||
                    '"address": { "street": "123 Main St", "city": "Exampleville", "zipcode": "12345"}}', 
                    '345e6789-e89b-12d3-a456-426655440002'
                )
            """.trimIndent()
        ).executeUpdate()

        val tableName = "TestTable"
        val df = DataFrame.readSqlTable(connection, tableName).cast<TestTableData>()
        df.rowsCount() shouldBe 3
        df.filter { it[TestTableData::integerCol]!! > 1000 }.rowsCount() shouldBe 2

        // testing numeric columns
        val result = df.select("tinyIntCol")
            .add("tinyIntCol2") { it[TestTableData::tinyIntCol] }

        result[0][1] shouldBe 1

        val result1 = df.select("smallIntCol")
            .add("smallIntCol2") { it[TestTableData::smallIntCol] }

        result1[0][1] shouldBe 100

        val result2 = df.select("bigIntCol")
            .add("bigIntCol2") { it[TestTableData::bigIntCol] }

        result2[0][1] shouldBe 100000

        val result3 = df.select("numericCol")
            .add("numericCol2") { it[TestTableData::numericCol] }

        BigDecimal("123.45").compareTo(result3[0][1] as BigDecimal) shouldBe 0

        val result4 = df.select("realCol")
            .add("realCol2") { it[TestTableData::realCol] }

        result4[0][1] shouldBe 1.23f

        val result5 = df.select("doublePrecisionCol")
            .add("doublePrecisionCol2") { it[TestTableData::doublePrecisionCol] }

        result5[0][1] shouldBe 3.14

        val result6 = df.select("decFloatCol")
            .add("decFloatCol2") { it[TestTableData::decFloatCol] }

        BigDecimal("2.71").compareTo(result6[0][1] as BigDecimal) shouldBe 0

        val schema = DataFrame.getSchemaForSqlTable(connection, tableName)

        schema.columns["tinyIntCol"]!!.type shouldBe typeOf<Int?>()
        schema.columns["smallIntCol"]!!.type shouldBe typeOf<Int?>()
        schema.columns["bigIntCol"]!!.type shouldBe typeOf<Long?>()
        schema.columns["numericCol"]!!.type shouldBe typeOf<BigDecimal?>()
        schema.columns["realCol"]!!.type shouldBe typeOf<Float?>()
        schema.columns["doublePrecisionCol"]!!.type shouldBe typeOf<Double?>()
        schema.columns["decFloatCol"]!!.type shouldBe typeOf<BigDecimal?>()
    }

    @Test
    fun `read from table`() {
        val tableName = "Customer"
        val df = DataFrame.readSqlTable(connection, tableName).cast<Customer>()

        df.rowsCount() shouldBe 4
        df.filter { it[Customer::age] != null && it[Customer::age]!! > 30 }.rowsCount() shouldBe 2
        df[0][1] shouldBe "John"

        val df1 = DataFrame.readSqlTable(connection, tableName, 1).cast<Customer>()

        df1.rowsCount() shouldBe 1
        df1.filter { it[Customer::age] != null && it[Customer::age]!! > 30 }.rowsCount() shouldBe 1
        df1[0][1] shouldBe "John"

        val dataSchema = DataFrame.getSchemaForSqlTable(connection, tableName)
        dataSchema.columns.size shouldBe 3
        dataSchema.columns["name"]!!.type shouldBe typeOf<String?>()

        val dbConfig = DatabaseConfiguration(url = URL)
        val df2 = DataFrame.readSqlTable(dbConfig, tableName).cast<Customer>()

        df2.rowsCount() shouldBe 4
        df2.filter { it[Customer::age] != null && it[Customer::age]!! > 30 }.rowsCount() shouldBe 2
        df2[0][1] shouldBe "John"

        val df3 = DataFrame.readSqlTable(dbConfig, tableName, 1).cast<Customer>()

        df3.rowsCount() shouldBe 1
        df3.filter { it[Customer::age] != null && it[Customer::age]!! > 30 }.rowsCount() shouldBe 1
        df3[0][1] shouldBe "John"

        val dataSchema1 = DataFrame.getSchemaForSqlTable(dbConfig, tableName)
        dataSchema1.columns.size shouldBe 3
        dataSchema1.columns["name"]!!.type shouldBe typeOf<String?>()
    }

    // to cover a reported case from https://github.com/Kotlin/dataframe/issues/494
    @Test
    fun `repeated read from table with limit`() {
        val tableName = "Customer"

        for (i in 1..10) {
            val df1 = DataFrame.readSqlTable(connection, tableName, 2).cast<Customer>()

            df1.rowsCount() shouldBe 2
            df1.filter { it[Customer::age] != null && it[Customer::age]!! > 30 }.rowsCount() shouldBe 1
            df1[0][1] shouldBe "John"

            val dbConfig = DatabaseConfiguration(url = URL)
            val df2 = DataFrame.readSqlTable(dbConfig, tableName, 2).cast<Customer>()

            df2.rowsCount() shouldBe 2
            df2.filter { it[Customer::age] != null && it[Customer::age]!! > 30 }.rowsCount() shouldBe 1
            df2[0][1] shouldBe "John"
        }
    }

    @Test
    fun `read from ResultSet`() {
        connection.createStatement(ResultSet.TYPE_SCROLL_SENSITIVE, ResultSet.CONCUR_UPDATABLE).use { st ->
            @Language("SQL")
            val selectStatement = "SELECT * FROM Customer"

            st.executeQuery(selectStatement).use { rs ->
                val df = DataFrame.readResultSet(rs, H2).cast<Customer>()

                df.rowsCount() shouldBe 4
                df.filter { it[Customer::age] != null && it[Customer::age]!! > 30 }.rowsCount() shouldBe 2
                df[0][1] shouldBe "John"

                rs.beforeFirst()

                val df1 = DataFrame.readResultSet(rs, H2, 1).cast<Customer>()

                df1.rowsCount() shouldBe 1
                df1.filter { it[Customer::age] != null && it[Customer::age]!! > 30 }.rowsCount() shouldBe 1
                df1[0][1] shouldBe "John"

                rs.beforeFirst()

                val dataSchema = DataFrame.getSchemaForResultSet(rs, H2)
                dataSchema.columns.size shouldBe 3
                dataSchema.columns["name"]!!.type shouldBe typeOf<String?>()

                rs.beforeFirst()

                val df2 = DataFrame.readResultSet(rs, connection).cast<Customer>()

                df2.rowsCount() shouldBe 4
                df2.filter { it[Customer::age] != null && it[Customer::age]!! > 30 }.rowsCount() shouldBe 2
                df2[0][1] shouldBe "John"

                rs.beforeFirst()

                val df3 = DataFrame.readResultSet(rs, connection, 1).cast<Customer>()

                df3.rowsCount() shouldBe 1
                df3.filter { it[Customer::age] != null && it[Customer::age]!! > 30 }.rowsCount() shouldBe 1
                df3[0][1] shouldBe "John"

                rs.beforeFirst()

                val dataSchema1 = DataFrame.getSchemaForResultSet(rs, connection)
                dataSchema1.columns.size shouldBe 3
                dataSchema1.columns["name"]!!.type shouldBe typeOf<String?>()
            }
        }
    }

    // to cover a reported case from https://github.com/Kotlin/dataframe/issues/494
    @Test
    fun `repeated read from ResultSet with limit`() {
        connection.createStatement(ResultSet.TYPE_SCROLL_SENSITIVE, ResultSet.CONCUR_UPDATABLE).use { st ->
            @Language("SQL")
            val selectStatement = "SELECT * FROM Customer"

            st.executeQuery(selectStatement).use { rs ->
                for (i in 1..10) {
                    rs.beforeFirst()

                    val df1 = DataFrame.readResultSet(rs, H2, 2).cast<Customer>()

                    df1.rowsCount() shouldBe 2
                    df1.filter { it[Customer::age] != null && it[Customer::age]!! > 30 }.rowsCount() shouldBe 1
                    df1[0][1] shouldBe "John"

                    rs.beforeFirst()

                    val df2 = DataFrame.readResultSet(rs, connection, 2).cast<Customer>()

                    df2.rowsCount() shouldBe 2
                    df2.filter { it[Customer::age] != null && it[Customer::age]!! > 30 }.rowsCount() shouldBe 1
                    df2[0][1] shouldBe "John"
                }
            }
        }
    }

    @Test
    fun `read from non-existing table`() {
        shouldThrow<JdbcSQLSyntaxErrorException> {
            DataFrame.readSqlTable(connection, "WrongTableName").cast<Customer>()
        }
    }

    // to cover a reported case from https://github.com/Kotlin/dataframe/issues/498
    @Test
    fun `read from incorrect SQL query`() {
        @Language("SQL")
        val createSQL = """
            CREATE TABLE Orders (
            order_id INT PRIMARY KEY,
            customer_id INT,
            order_date DATE,
            total_amount DECIMAL(10, 2))
            """

        @Language("SQL")
        val dropSQL = """
            DROP TABLE Customer
            """

        @Language("SQL")
        val alterSQL = """
            ALTER TABLE Customer
            ADD COLUMN email VARCHAR(100)
            """

        @Language("SQL")
        val deleteSQL = """
            DELETE FROM Customer
            WHERE id = 1
            """

        @Language("SQL")
        val repeatedSQL = """
            SELECT * FROM Customer
            WHERE id = 1;
            SELECT * FROM Customer
            WHERE id = 1;
            """

        shouldThrow<IllegalArgumentException> {
            DataFrame.readSqlQuery(connection, createSQL)
        }

        shouldThrow<IllegalArgumentException> {
            DataFrame.readSqlQuery(connection, dropSQL)
        }

        shouldThrow<IllegalArgumentException> {
            DataFrame.readSqlQuery(connection, alterSQL)
        }

        shouldThrow<IllegalArgumentException> {
            DataFrame.readSqlQuery(connection, deleteSQL)
        }

        shouldThrow<IllegalArgumentException> {
            DataFrame.readSqlQuery(connection, repeatedSQL)
        }
    }

    @Test
    fun `read from table with name from reserved SQL keywords`() {
        // Create table Sale
        @Language("SQL")
        val createAlterTableQuery = """
                CREATE TABLE "ALTER" (
                id INT PRIMARY KEY,
                description TEXT
                )
            """

        connection.createStatement().execute(
            createAlterTableQuery
        )

        @Language("SQL")
        val selectFromWeirdTableSQL = """
            SELECT * from "ALTER"
            """

        DataFrame.readSqlQuery(connection, selectFromWeirdTableSQL).rowsCount() shouldBe 0
    }

    @Test
    fun `read from non-existing jdbc url`() {
        shouldThrow<SQLException> {
            DataFrame.readSqlTable(DriverManager.getConnection("ddd"), "WrongTableName")
        }
    }

    @Test
    fun `read from sql query`() {
        @Language("SQL")
        val sqlQuery = """
            SELECT c.name as customerName, SUM(s.amount) as totalSalesAmount
            FROM Sale s
            INNER JOIN Customer c ON s.customerId = c.id
            WHERE c.age > 35
            GROUP BY s.customerId, c.name
        """.trimIndent()

        val df = DataFrame.readSqlQuery(connection, sqlQuery).cast<CustomerSales>()

        df.rowsCount() shouldBe 2
        df.filter { it[CustomerSales::totalSalesAmount]!! > 100 }.rowsCount() shouldBe 1
        df[0][0] shouldBe "John"

        val df1 = DataFrame.readSqlQuery(connection, sqlQuery, 1).cast<CustomerSales>()

        df1.rowsCount() shouldBe 1
        df1.filter { it[CustomerSales::totalSalesAmount]!! > 100 }.rowsCount() shouldBe 1
        df1[0][0] shouldBe "John"

        val dataSchema = DataFrame.getSchemaForSqlQuery(connection, sqlQuery)
        dataSchema.columns.size shouldBe 2
        dataSchema.columns["name"]!!.type shouldBe typeOf<String?>()

        val dbConfig = DatabaseConfiguration(url = URL)
        val df2 = DataFrame.readSqlQuery(dbConfig, sqlQuery).cast<CustomerSales>()

        df2.rowsCount() shouldBe 2
        df2.filter { it[CustomerSales::totalSalesAmount]!! > 100 }.rowsCount() shouldBe 1
        df2[0][0] shouldBe "John"

        val df3 = DataFrame.readSqlQuery(dbConfig, sqlQuery, 1).cast<CustomerSales>()

        df3.rowsCount() shouldBe 1
        df3.filter { it[CustomerSales::totalSalesAmount]!! > 100 }.rowsCount() shouldBe 1
        df3[0][0] shouldBe "John"

        val dataSchema1 = DataFrame.getSchemaForSqlQuery(dbConfig, sqlQuery)
        dataSchema1.columns.size shouldBe 2
        dataSchema1.columns["name"]!!.type shouldBe typeOf<String?>()
    }

    @Test
    fun `read from sql query with two repeated columns`() {
        @Language("SQL")
        val sqlQuery = """
            SELECT c1.name, c2.name
            FROM Customer c1
            INNER JOIN Customer c2 ON c1.id = c2.id
        """.trimIndent()

        val schema = DataFrame.getSchemaForSqlQuery(connection, sqlQuery)
        schema.columns.size shouldBe 2
        schema.columns.toList()[0].first shouldBe "name"
        schema.columns.toList()[1].first shouldBe "name_1"
    }

    @Test
    fun `read from sql query with three repeated columns`() {
        @Language("SQL")
        val sqlQuery = """
            SELECT c1.name as name, c2.name as name_1, c1.name as name_1
            FROM Customer c1
            INNER JOIN Customer c2 ON c1.id = c2.id
        """.trimIndent()

        val schema = DataFrame.getSchemaForSqlQuery(connection, sqlQuery)
        schema.columns.size shouldBe 3
        schema.columns.toList()[0].first shouldBe "name"
        schema.columns.toList()[1].first shouldBe "name_1"
        schema.columns.toList()[2].first shouldBe "name_2"
    }

    @Test
    fun `read from all tables`() {
        val dataframes = DataFrame.readAllSqlTables(connection)

        val customerDf = dataframes[0].cast<Customer>()

        customerDf.rowsCount() shouldBe 4
        customerDf.filter { it[Customer::age] != null && it[Customer::age]!! > 30 }.rowsCount() shouldBe 2
        customerDf[0][1] shouldBe "John"

        val saleDf = dataframes[1].cast<Sale>()

        saleDf.rowsCount() shouldBe 4
        saleDf.filter { it[Sale::amount] > 40 }.rowsCount() shouldBe 3
        (saleDf[0][2] as BigDecimal).compareTo(BigDecimal(100.50)) shouldBe 0

        val dataframes1 = DataFrame.readAllSqlTables(connection, limit = 1)

        val customerDf1 = dataframes1[0].cast<Customer>()

        customerDf1.rowsCount() shouldBe 1
        customerDf1.filter { it[Customer::age] != null && it[Customer::age]!! > 30 }.rowsCount() shouldBe 1
        customerDf1[0][1] shouldBe "John"

        val saleDf1 = dataframes1[1].cast<Sale>()

        saleDf1.rowsCount() shouldBe 1
        saleDf1.filter { it[Sale::amount] > 40 }.rowsCount() shouldBe 1
        (saleDf[0][2] as BigDecimal).compareTo(BigDecimal(100.50)) shouldBe 0

        val dataSchemas = DataFrame.getSchemaForAllSqlTables(connection)

        val customerDataSchema = dataSchemas[0]
        customerDataSchema.columns.size shouldBe 3
        customerDataSchema.columns["name"]!!.type shouldBe typeOf<String?>()

        val saleDataSchema = dataSchemas[1]
        saleDataSchema.columns.size shouldBe 3
        // TODO: fix nullability
        saleDataSchema.columns["amount"]!!.type shouldBe typeOf<BigDecimal>()

        val dbConfig = DatabaseConfiguration(url = URL)
        val dataframes2 = DataFrame.readAllSqlTables(dbConfig)

        val customerDf2 = dataframes2[0].cast<Customer>()

        customerDf2.rowsCount() shouldBe 4
        customerDf2.filter { it[Customer::age] != null && it[Customer::age]!! > 30 }.rowsCount() shouldBe 2
        customerDf2[0][1] shouldBe "John"

        val saleDf2 = dataframes2[1].cast<Sale>()

        saleDf2.rowsCount() shouldBe 4
        saleDf2.filter { it[Sale::amount] > 40 }.rowsCount() shouldBe 3
        (saleDf[0][2] as BigDecimal).compareTo(BigDecimal(100.50)) shouldBe 0

        val dataframes3 = DataFrame.readAllSqlTables(dbConfig, limit = 1)

        val customerDf3 = dataframes3[0].cast<Customer>()

        customerDf3.rowsCount() shouldBe 1
        customerDf3.filter { it[Customer::age] != null && it[Customer::age]!! > 30 }.rowsCount() shouldBe 1
        customerDf3[0][1] shouldBe "John"

        val saleDf3 = dataframes3[1].cast<Sale>()

        saleDf3.rowsCount() shouldBe 1
        saleDf3.filter { it[Sale::amount] > 40 }.rowsCount() shouldBe 1
        (saleDf[0][2] as BigDecimal).compareTo(BigDecimal(100.50)) shouldBe 0

        val dataSchemas1 = DataFrame.getSchemaForAllSqlTables(dbConfig)

        val customerDataSchema1 = dataSchemas1[0]
        customerDataSchema1.columns.size shouldBe 3
        customerDataSchema1.columns["name"]!!.type shouldBe typeOf<String?>()

        val saleDataSchema1 = dataSchemas1[1]
        saleDataSchema1.columns.size shouldBe 3
        saleDataSchema1.columns["amount"]!!.type shouldBe typeOf<BigDecimal>()
    }

    // TODO: add the same test for each particular database and refactor the scenario to the common test case
    // https://github.com/Kotlin/dataframe/issues/688
    @Test
    fun `infer nullability`() {
        // prepare tables and data
        @Language("SQL")
        val createTestTable1Query = """
                CREATE TABLE TestTable1 (
                    id INT PRIMARY KEY,
                    name VARCHAR(50),
                    surname VARCHAR(50),
                    age INT NOT NULL
                )
            """

        connection.createStatement().execute(createTestTable1Query)

        connection.createStatement().execute("INSERT INTO TestTable1 (id, name, surname, age) VALUES (1, 'John', 'Crawford', 40)")
        connection.createStatement().execute("INSERT INTO TestTable1 (id, name, surname, age) VALUES (2, 'Alice', 'Smith', 25)")
        connection.createStatement().execute("INSERT INTO TestTable1 (id, name, surname, age) VALUES (3, 'Bob', 'Johnson', 47)")
        connection.createStatement().execute("INSERT INTO TestTable1 (id, name, surname, age) VALUES (4, 'Sam', NULL, 15)")

        // start testing `readSqlTable` method

        // with default inferNullability: Boolean = true
        val tableName = "TestTable1"
        val df = DataFrame.readSqlTable(connection, tableName)
        df.schema().columns["id"]!!.type shouldBe typeOf<Int>()
        df.schema().columns["name"]!!.type shouldBe typeOf<String>()
        df.schema().columns["surname"]!!.type shouldBe typeOf<String?>()
        df.schema().columns["age"]!!.type shouldBe typeOf<Int>()

        val dataSchema = DataFrame.getSchemaForSqlTable(connection, tableName)
        dataSchema.columns.size shouldBe 4
        dataSchema.columns["id"]!!.type shouldBe typeOf<Int>()
        dataSchema.columns["name"]!!.type shouldBe typeOf<String?>()
        dataSchema.columns["surname"]!!.type shouldBe typeOf<String?>()
        dataSchema.columns["age"]!!.type shouldBe typeOf<Int>()

        // with inferNullability: Boolean = false
        val df1 = DataFrame.readSqlTable(connection, tableName, inferNullability = false)
        df1.schema().columns["id"]!!.type shouldBe typeOf<Int>()
        df1.schema().columns["name"]!!.type shouldBe typeOf<String?>() // <=== this column changed a type because it doesn't contain nulls
        df1.schema().columns["surname"]!!.type shouldBe typeOf<String?>()
        df1.schema().columns["age"]!!.type shouldBe typeOf<Int>()

        // end testing `readSqlTable` method

        // start testing `readSQLQuery` method

        // ith default inferNullability: Boolean = true
        @Language("SQL")
        val sqlQuery = """
            SELECT name, surname, age FROM TestTable1
        """.trimIndent()

        val df2 = DataFrame.readSqlQuery(connection, sqlQuery)
        df2.schema().columns["name"]!!.type shouldBe typeOf<String>()
        df2.schema().columns["surname"]!!.type shouldBe typeOf<String?>()
        df2.schema().columns["age"]!!.type shouldBe typeOf<Int>()

        val dataSchema2 = DataFrame.getSchemaForSqlQuery(connection, sqlQuery)
        dataSchema2.columns.size shouldBe 3
        dataSchema2.columns["name"]!!.type shouldBe typeOf<String?>()
        dataSchema2.columns["surname"]!!.type shouldBe typeOf<String?>()
        dataSchema2.columns["age"]!!.type shouldBe typeOf<Int>()

        // with inferNullability: Boolean = false
        val df3 = DataFrame.readSqlQuery(connection, sqlQuery, inferNullability = false)
        df3.schema().columns["name"]!!.type shouldBe typeOf<String?>() // <=== this column changed a type because it doesn't contain nulls
        df3.schema().columns["surname"]!!.type shouldBe typeOf<String?>()
        df3.schema().columns["age"]!!.type shouldBe typeOf<Int>()

        // end testing `readSQLQuery` method

        // start testing `readResultSet` method

        connection.createStatement(ResultSet.TYPE_SCROLL_SENSITIVE, ResultSet.CONCUR_UPDATABLE).use { st ->
            @Language("SQL")
            val selectStatement = "SELECT * FROM TestTable1"

            st.executeQuery(selectStatement).use { rs ->
                // ith default inferNullability: Boolean = true
                val df4 = DataFrame.readResultSet(rs, H2)
                df4.schema().columns["id"]!!.type shouldBe typeOf<Int>()
                df4.schema().columns["name"]!!.type shouldBe typeOf<String>()
                df4.schema().columns["surname"]!!.type shouldBe typeOf<String?>()
                df4.schema().columns["age"]!!.type shouldBe typeOf<Int>()

                rs.beforeFirst()

                val dataSchema3 = DataFrame.getSchemaForResultSet(rs, H2)
                dataSchema3.columns.size shouldBe 4
                dataSchema3.columns["id"]!!.type shouldBe typeOf<Int>()
                dataSchema3.columns["name"]!!.type shouldBe typeOf<String?>()
                dataSchema3.columns["surname"]!!.type shouldBe typeOf<String?>()
                dataSchema3.columns["age"]!!.type shouldBe typeOf<Int>()

                // with inferNullability: Boolean = false
                rs.beforeFirst()

                val df5 = DataFrame.readResultSet(rs, H2, inferNullability = false)
                df5.schema().columns["id"]!!.type shouldBe typeOf<Int>()
                df5.schema().columns["name"]!!.type shouldBe typeOf<String?>() // <=== this column changed a type because it doesn't contain nulls
                df5.schema().columns["surname"]!!.type shouldBe typeOf<String?>()
                df5.schema().columns["age"]!!.type shouldBe typeOf<Int>()
            }
        }
        // end testing `readResultSet` method

        connection.createStatement().execute("DROP TABLE TestTable1")
    }
}
