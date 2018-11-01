@file:JvmName("ConnectionUtil")

package me.ocpu.sql

import java.sql.*

operator fun <T> PreparedStatement.set(index: Int, value: T) = this.setObject(index + 1, value)
inline operator fun <reified T: Any> ResultSet.get(label: String): T = getObject(label) as T
