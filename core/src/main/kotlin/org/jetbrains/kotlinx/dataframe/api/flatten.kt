package org.jetbrains.kotlinx.dataframe.api

import org.jetbrains.kotlinx.dataframe.ColumnsSelector
import org.jetbrains.kotlinx.dataframe.DataFrame
import org.jetbrains.kotlinx.dataframe.columns.ColumnReference
import org.jetbrains.kotlinx.dataframe.columns.toColumnSet
import org.jetbrains.kotlinx.dataframe.impl.api.flattenImpl
import kotlin.reflect.KProperty

// region DataFrame

public fun <T> DataFrame<T>.flatten(keepParentNameForColumns: Boolean = false): DataFrame<T> = flatten(keepParentNameForColumns) { all() }

public fun <T, C> DataFrame<T>.flatten(keepParentNameForColumns: Boolean = false, columns: ColumnsSelector<T, C>): DataFrame<T> = flattenImpl(columns, keepParentNameForColumns)

public fun <T> DataFrame<T>.flatten(vararg columns: String, keepParentNameForColumns: Boolean = false): DataFrame<T> = flatten(keepParentNameForColumns) { columns.toColumnSet() }

public fun <T, C> DataFrame<T>.flatten(vararg columns: ColumnReference<C>, keepParentNameForColumns: Boolean = false): DataFrame<T> =
    flatten(keepParentNameForColumns) { columns.toColumnSet() }

public fun <T, C> DataFrame<T>.flatten(vararg columns: KProperty<C>, keepParentNameForColumns: Boolean = false): DataFrame<T> =
    flatten(keepParentNameForColumns) { columns.toColumnSet() }

// endregion
