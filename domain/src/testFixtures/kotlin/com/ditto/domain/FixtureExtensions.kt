package com.ditto.domain

fun <T : Any> T.withId(id: Long): T {
    val field = this::class.java.getDeclaredField("id")
    field.isAccessible = true
    field.set(this, id)
    return this
}
