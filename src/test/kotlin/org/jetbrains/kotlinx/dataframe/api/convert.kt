package org.jetbrains.kotlinx.dataframe.api

import io.kotest.matchers.shouldBe
import org.jetbrains.kotlinx.dataframe.columnOf
import org.jetbrains.kotlinx.dataframe.hasNulls
import org.junit.Test
import java.time.LocalTime

class ConvertTests {

    @Test
    fun `convert nullable strings to time`() {
        val time by columnOf("11?22?33", null)
        val converted = time.toDataFrame().convert { time }.toLocalTime("HH?mm?ss")[time]
        converted.hasNulls shouldBe true
        converted[0] shouldBe LocalTime.of(11, 22, 33)
    }

    @Test
    fun `nullability persistence after conversion`() {
        val col by columnOf("1", null)
        col.convertToInt().forEach {
        }
    }
}
