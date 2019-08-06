/*
 * Copyright 2018-2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package me.liuwj.ktorm.schema

import me.liuwj.ktorm.entity.Entity
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1

/**
 * Base class of Ktorm's table objects, represents relational tables in the database.
 *
 * @property tableName the table's name.
 * @property alias the table's alias.
 */
open class Table<E : Entity<E>>(
    tableName: String,
    alias: String? = null,
    entityClass: KClass<E>? = null
) : BaseTable<E>(tableName, alias, entityClass) {

    override fun aliased(alias: String): Table<E> {
        val result = Table(tableName, alias, entityClass)
        result.copyDefinitionsFrom(this)
        return result
    }

    /**
     * Bind the column to a reference table, equivalent to a foreign key in relational databases.
     * Entity finding functions would automatically left join all references (recursively) by default.
     *
     * @param referenceTable the reference table, will be copied by calling its [aliased] function with
     * an alias like `_refN`.
     *
     * @param selector a lambda in which we should return the property used to hold the referenced entities.
     * For exmaple: `val departmentId by int("department_id").references(Departments) { it.department }`.
     *
     * @return this column registration.
     *
     * @see me.liuwj.ktorm.entity.joinReferencesAndSelect
     * @see me.liuwj.ktorm.entity.createEntity
     */
    inline fun <C : Any, R : Entity<R>> ColumnRegistration<C>.references(
        referenceTable: Table<R>,
        selector: (E) -> R?
    ): ColumnRegistration<C> {
        val properties = detectBindingProperties(selector)

        if (properties.size > 1) {
            throw IllegalArgumentException("Reference binding doesn't support nested properties.")
        } else {
            return doBindInternal(ReferenceBinding(referenceTable, properties[0]))
        }
    }

    /**
     * Bind the column to nested properties, eg. `employee.manager.department.id`.
     *
     * @param selector a lambda in which we should return the property we want to bind.
     * For example: `val name by varchar("name").bindTo { it.name }`.
     *
     * @return this column registration.
     */
    inline fun <C : Any> ColumnRegistration<C>.bindTo(selector: (E) -> C?): ColumnRegistration<C> {
        val properties = detectBindingProperties(selector)
        return doBindInternal(NestedBinding(properties))
    }

    @PublishedApi
    @Suppress("UNCHECKED_CAST")
    internal inline fun detectBindingProperties(selector: (E) -> Any?): List<KProperty1<*, *>> {
        val entityClass = entityClass ?: error("No entity class configured for table: $tableName")
        val properties = java.util.ArrayList<KProperty1<*, *>>()

        val proxy = ColumnBindingHandler.createProxy(entityClass, properties)
        selector(proxy as E)

        if (properties.isEmpty()) {
            throw IllegalArgumentException("No binding properties found.")
        } else {
            return properties
        }
    }
}
