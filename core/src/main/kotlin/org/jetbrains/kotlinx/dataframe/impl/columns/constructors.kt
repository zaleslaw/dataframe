package org.jetbrains.kotlinx.dataframe.impl.columns

import org.jetbrains.kotlinx.dataframe.AnyCol
import org.jetbrains.kotlinx.dataframe.AnyFrame
import org.jetbrains.kotlinx.dataframe.AnyRow
import org.jetbrains.kotlinx.dataframe.ColumnsContainer
import org.jetbrains.kotlinx.dataframe.ColumnsSelector
import org.jetbrains.kotlinx.dataframe.DataColumn
import org.jetbrains.kotlinx.dataframe.DataFrame
import org.jetbrains.kotlinx.dataframe.DataRow
import org.jetbrains.kotlinx.dataframe.Selector
import org.jetbrains.kotlinx.dataframe.api.AddDataRow
import org.jetbrains.kotlinx.dataframe.api.AddExpression
import org.jetbrains.kotlinx.dataframe.api.ColumnsSelectionDsl
import org.jetbrains.kotlinx.dataframe.api.Infer
import org.jetbrains.kotlinx.dataframe.api.PivotColumnsSelector
import org.jetbrains.kotlinx.dataframe.api.PivotDsl
import org.jetbrains.kotlinx.dataframe.api.SortColumnsSelector
import org.jetbrains.kotlinx.dataframe.api.SortDsl
import org.jetbrains.kotlinx.dataframe.api.asDataColumn
import org.jetbrains.kotlinx.dataframe.api.cast
import org.jetbrains.kotlinx.dataframe.api.concat
import org.jetbrains.kotlinx.dataframe.api.dataFrameOf
import org.jetbrains.kotlinx.dataframe.api.indices
import org.jetbrains.kotlinx.dataframe.api.toColumnOf
import org.jetbrains.kotlinx.dataframe.api.toDataFrame
import org.jetbrains.kotlinx.dataframe.columns.*
import org.jetbrains.kotlinx.dataframe.impl.DataFrameReceiver
import org.jetbrains.kotlinx.dataframe.impl.DataRowImpl
import org.jetbrains.kotlinx.dataframe.impl.asList
import org.jetbrains.kotlinx.dataframe.impl.eraseGenericTypeParameters
import org.jetbrains.kotlinx.dataframe.impl.guessValueType
import org.jetbrains.kotlinx.dataframe.index
import org.jetbrains.kotlinx.dataframe.nrow
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.full.withNullability

// region create DataColumn

internal class AddDataRowImpl<T>(index: Int, owner: DataFrame<T>, private val container: List<*>) :
    DataRowImpl<T>(index, owner),
    AddDataRow<T> {

    override fun <C> AnyRow.newValue() = container[index] as C
}

@PublishedApi
internal fun <T, R> ColumnsContainer<T>.newColumn(
    type: KType,
    name: String = "",
    infer: Infer = Infer.Nulls,
    expression: AddExpression<T, R>,
): DataColumn<R> {
    val df = this as? DataFrame<T> ?: dataFrameOf(columns()).cast()
    val (nullable, values) = computeValues(df, expression)
    return when (infer) {
        Infer.Nulls -> DataColumn.create(
            name = name,
            values = values,
            type = type.withNullability(nullable).eraseGenericTypeParameters(),
            infer = Infer.None,
        )

        Infer.Type -> DataColumn.createWithTypeInference(
            name = name,
            values = values,
            nullable = nullable,
        )

        Infer.None -> DataColumn.create(
            name = name,
            values = values,
            type = type.eraseGenericTypeParameters(),
            infer = Infer.None,
        )
    }
}

@PublishedApi
internal fun <T, R> ColumnsContainer<T>.newColumnWithActualType(
    name: String,
    expression: AddExpression<T, R>,
): DataColumn<R> {
    val (_, values) = computeValues(this as DataFrame<T>, expression)
    return guessColumnType(name, values)
}

internal fun <T, R> computeValues(df: DataFrame<T>, expression: AddExpression<T, R>): Pair<Boolean, List<R>> {
    var nullable = false
    val list = ArrayList<R>(df.nrow)
    df.indices().forEach {
        val row = AddDataRowImpl(it, df, list)
        val value = expression(row, row)
        if (value == null) nullable = true
        list.add(value)
    }
    return nullable to list
}

@Suppress("UNCHECKED_CAST")
@PublishedApi
internal fun <T> createColumn(values: Iterable<T>, suggestedType: KType, guessType: Boolean = false): DataColumn<T> =
    when {
        // values is a non-empty list of AnyRows
        values.any() && values.all { it is AnyRow } ->
            DataColumn.createColumnGroup(
                name = "",
                df = (values as Iterable<AnyRow>).toDataFrame(),
            ).asDataColumn().cast()

        // values is a non-empty list of DataColumns
        values.any() && values.all { it is AnyCol } ->
            DataColumn.createColumnGroup(
                name = "",
                df = (values as Iterable<AnyCol>).toDataFrame()
            ).asDataColumn().cast()

        // values is a non-empty list of DataFrames and nulls
        // (but not just nulls; we cannot assume that should create a FrameColumn)
        values.any() && values.all { it is AnyFrame? } && !values.all { it == null } ->
            DataColumn.createFrameColumn(
                name = "",
                groups = values.map { it as? AnyFrame ?: DataFrame.empty() }
            ).asDataColumn().cast()

        guessType ->
            guessColumnType(
                name = "",
                values = values.asList(),
                suggestedType = suggestedType,
                suggestedTypeIsUpperBound = true,
            ).cast()

        else ->
            DataColumn.createValueColumn(
                name = "",
                values = values.toList(),
                type = suggestedType,
            )
    }

// endregion

// region create Columns

internal fun <C> createColumnSet(resolver: (ColumnResolutionContext) -> List<ColumnWithPath<C>>): ColumnSet<C> =
    object : ColumnSet<C> {
        override fun resolve(context: ColumnResolutionContext) = resolver(context)
    }

// region toColumnSet

// region DSL

internal fun <TD, T : DataFrame<TD>, C> Selector<T, ColumnSet<C>>.toColumnSet(
    createReceiver: (ColumnResolutionContext) -> T,
): ColumnSet<C> =
    createColumnSet {
        val receiver = createReceiver(it)
        val columnSet = this(receiver, receiver)
        columnSet.resolve(receiver, it.unresolvedColumnsPolicy)
    }

@JvmName("toColumnSetForPivot")
internal fun <T, C> PivotColumnsSelector<T, C>.toColumnSet(): ColumnSet<C> = toColumnSet {
    object : DataFrameReceiver<T>(it.df.cast(), it.unresolvedColumnsPolicy), PivotDsl<T> {}
}

@JvmName("toColumnSetForSort")
internal fun <T, C> SortColumnsSelector<T, C>.toColumnSet(): ColumnSet<C> = toColumnSet {
    object : DataFrameReceiver<T>(it.df.cast(), it.unresolvedColumnsPolicy), SortDsl<T> {}
}

internal fun <T, C> ColumnsSelector<T, C>.toColumnSet(): ColumnSet<C> = toColumnSet {
    object : DataFrameReceiver<T>(it.df.cast(), it.unresolvedColumnsPolicy), ColumnsSelectionDsl<T> {}
}

// endregion

// endregion

// region toComparableColumns

internal fun Array<out String>.toComparableColumns() = toColumnsSetOf<Comparable<Any?>>()

internal fun String.toComparableColumn() = toColumnOf<Comparable<Any?>>()

// endregion

internal fun Array<out String>.toNumberColumns() = toColumnsSetOf<Number>()

// endregion

internal fun <T> guessColumnType(name: String, values: List<T>) = guessColumnType(name, values, null)

@PublishedApi
internal fun <T> guessColumnType(
    name: String,
    values: List<T>,
    suggestedType: KType? = null,
    suggestedTypeIsUpperBound: Boolean = false,
    defaultValue: T? = null,
    nullable: Boolean? = null,
): DataColumn<T> {
    val detectType = suggestedType == null || suggestedTypeIsUpperBound
    val type = if (detectType) guessValueType(
        values = values.asSequence(),
        upperBound = suggestedType,
        listifyValues = false,
    ) else suggestedType!!

    return when (type.classifier!! as KClass<*>) {
        DataRow::class -> {
            val df = values.map { (it as AnyRow?)?.toDataFrame() ?: DataFrame.empty(1) }.concat()
            DataColumn.createColumnGroup(name, df).asDataColumn().cast()
        }

        DataFrame::class -> {
            val frames = values.map {
                when (it) {
                    null -> DataFrame.empty()
                    is AnyFrame -> it
                    is AnyRow -> it.toDataFrame()
                    is List<*> -> (it as List<AnyRow>).toDataFrame()
                    is Iterable<*> -> (it as Iterable<AnyRow>).toDataFrame()
                    else -> throw IllegalStateException()
                }
            }
            DataColumn.createFrameColumn(name, frames).asDataColumn().cast()
        }

        List::class -> {
            val nullable = type.isMarkedNullable
            var isListOfRows: Boolean? = null
            val lists = values.map {
                when (it) {
                    null -> if (nullable) null else emptyList()
                    is List<*> -> {
                        if (isListOfRows != false && it.isNotEmpty()) isListOfRows = it.all { it is AnyRow }
                        it
                    }

                    else -> { // if !detectType and suggestedType is a list, we wrap the values in lists
                        if (isListOfRows != false) isListOfRows = it is AnyRow
                        listOf(it)
                    }
                }
            }
            if (isListOfRows == true) {
                val frames = lists.map {
                    if (it == null) DataFrame.empty()
                    else (it as List<AnyRow>).concat()
                }
                DataColumn.createFrameColumn(name, frames).cast()
            } else {
                DataColumn.createValueColumn(name, lists, type, defaultValue = defaultValue).cast()
            }
        }

        else -> {
            if (nullable == null) {
                DataColumn.createValueColumn(
                    name = name,
                    values = values,
                    type = type,
                    infer = if (detectType) Infer.None else Infer.Nulls,
                    defaultValue = defaultValue,
                )
            } else DataColumn.createValueColumn(
                name = name,
                values = values,
                type = type.withNullability(nullable),
                defaultValue = defaultValue,
            )
        }
    }
}
