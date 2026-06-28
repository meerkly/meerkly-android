package com.meerkly.android.util

import org.junit.Assert.assertEquals
import org.junit.Test

class MiniJsonTest {

    @Test
    fun encodesPrimitives() {
        assertEquals("null", MiniJson.encode(null))
        assertEquals("true", MiniJson.encode(true))
        assertEquals("42", MiniJson.encode(42))
        assertEquals("42", MiniJson.encode(42L))
        assertEquals("\"hi\"", MiniJson.encode("hi"))
    }

    @Test
    fun encodesObjectsInInsertionOrder() {
        val obj = linkedMapOf("b" to 1, "a" to "x")
        assertEquals("{\"b\":1,\"a\":\"x\"}", MiniJson.encode(obj))
    }

    @Test
    fun encodesArrays() {
        assertEquals("[1,2,3]", MiniJson.encode(listOf(1, 2, 3)))
    }

    @Test
    fun encodesNestedStructures() {
        val obj = linkedMapOf("data" to linkedMapOf("success" to true, "items" to listOf("a", "b")))
        assertEquals("{\"data\":{\"success\":true,\"items\":[\"a\",\"b\"]}}", MiniJson.encode(obj))
    }

    @Test
    fun escapesQuotesNewlinesTabsAndBackslashes() {
        assertEquals("\"a\\\"b\"", MiniJson.encode("a\"b"))
        assertEquals("\"line1\\nline2\"", MiniJson.encode("line1\nline2"))
        assertEquals("\"tab\\there\"", MiniJson.encode("tab\there"))
        assertEquals("\"a\\\\b\"", MiniJson.encode("a\\b"))
    }

    @Test
    fun escapesLowControlCharactersAsUnicode() {
        val controlChar = Char(1).toString()
        assertEquals("\"\\u0001\"", MiniJson.encode(controlChar))
    }
}
