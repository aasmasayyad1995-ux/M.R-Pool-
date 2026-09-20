package com.mrpool.eightball.net

/**
 * A very small JSON reader and writer, covering exactly what the match protocol needs:
 * objects, arrays, strings, numbers, booleans and null.
 *
 * Android ships `org.json`, but that class is a stub in local unit tests, and the
 * conversion between a move and its wire form is precisely the part worth testing without
 * a device. A hundred lines here buys that, and keeps the app free of a JSON dependency.
 */
object Json {

    // ------------------------------------------------------------------------ writing

    fun write(value: Any?): String = StringBuilder().also { writeValue(it, value) }.toString()

    private fun writeValue(out: StringBuilder, value: Any?) {
        when (value) {
            null -> out.append("null")
            is String -> writeString(out, value)
            is Boolean -> out.append(value)
            is Int, is Long -> out.append(value)
            is Float -> writeNumber(out, value.toDouble())
            is Double -> writeNumber(out, value)
            is Map<*, *> -> {
                out.append('{')
                var first = true
                for ((key, item) in value) {
                    if (!first) out.append(',')
                    first = false
                    writeString(out, key.toString())
                    out.append(':')
                    writeValue(out, item)
                }
                out.append('}')
            }

            is Iterable<*> -> {
                out.append('[')
                var first = true
                for (item in value) {
                    if (!first) out.append(',')
                    first = false
                    writeValue(out, item)
                }
                out.append(']')
            }

            else -> writeString(out, value.toString())
        }
    }

    /** JSON has no way to say NaN or Infinity, so they are written as null. */
    private fun writeNumber(out: StringBuilder, value: Double) {
        if (value.isNaN() || value.isInfinite()) out.append("null") else out.append(value)
    }

    private fun writeString(out: StringBuilder, value: String) {
        out.append('"')
        for (char in value) {
            when (char) {
                '"' -> out.append("\\\"")
                '\\' -> out.append("\\\\")
                '\n' -> out.append("\\n")
                '\r' -> out.append("\\r")
                '\t' -> out.append("\\t")
                '\b' -> out.append("\\b")
                '\u000C' -> out.append("\\f")
                else ->
                    if (char < ' ') out.append("\\u%04x".format(char.code)) else out.append(char)
            }
        }
        out.append('"')
    }

    // ------------------------------------------------------------------------ reading

    /** Returns null for anything malformed, rather than throwing at the caller. */
    fun read(text: String): Any? = try {
        val reader = Reader(text)
        val value = reader.readValue()
        reader.skipWhitespace()
        if (reader.done()) value else null
    } catch (t: Throwable) {
        null
    }

    @Suppress("UNCHECKED_CAST")
    fun readObject(text: String): Map<String, Any?>? = read(text) as? Map<String, Any?>

    private class Reader(private val text: String) {
        private var at = 0

        fun done(): Boolean = at >= text.length

        fun skipWhitespace() {
            while (at < text.length && text[at].isWhitespace()) at++
        }

        fun readValue(): Any? {
            skipWhitespace()
            if (done()) fail()
            return when (text[at]) {
                '{' -> readObject()
                '[' -> readArray()
                '"' -> readString()
                't' -> literal("true", true)
                'f' -> literal("false", false)
                'n' -> literal("null", null)
                else -> readNumber()
            }
        }

        private fun readObject(): Map<String, Any?> {
            expect('{')
            val out = LinkedHashMap<String, Any?>()
            skipWhitespace()
            if (peek() == '}') {
                at++
                return out
            }
            while (true) {
                skipWhitespace()
                val key = readString()
                skipWhitespace()
                expect(':')
                out[key] = readValue()
                skipWhitespace()
                when (peek()) {
                    ',' -> at++
                    '}' -> {
                        at++
                        return out
                    }

                    else -> fail()
                }
            }
        }

        private fun readArray(): List<Any?> {
            expect('[')
            val out = ArrayList<Any?>()
            skipWhitespace()
            if (peek() == ']') {
                at++
                return out
            }
            while (true) {
                out.add(readValue())
                skipWhitespace()
                when (peek()) {
                    ',' -> at++
                    ']' -> {
                        at++
                        return out
                    }

                    else -> fail()
                }
            }
        }

        private fun readString(): String {
            expect('"')
            val out = StringBuilder()
            while (true) {
                if (done()) fail()
                when (val char = text[at++]) {
                    '"' -> return out.toString()
                    '\\' -> {
                        if (done()) fail()
                        when (val escape = text[at++]) {
                            '"' -> out.append('"')
                            '\\' -> out.append('\\')
                            '/' -> out.append('/')
                            'n' -> out.append('\n')
                            'r' -> out.append('\r')
                            't' -> out.append('\t')
                            'b' -> out.append('\b')
                            'f' -> out.append('\u000C')
                            'u' -> {
                                if (at + 4 > text.length) fail()
                                out.append(text.substring(at, at + 4).toInt(16).toChar())
                                at += 4
                            }

                            else -> out.append(escape)
                        }
                    }

                    else -> out.append(char)
                }
            }
        }

        private fun readNumber(): Double {
            val start = at
            if (peek() == '-' || peek() == '+') at++
            while (!done() && (text[at].isDigit() || text[at] in ".eE+-")) at++
            return text.substring(start, at).toDoubleOrNull() ?: fail()
        }

        private fun literal(word: String, value: Any?): Any? {
            if (!text.startsWith(word, at)) fail()
            at += word.length
            return value
        }

        private fun peek(): Char = if (done()) fail() else text[at]

        private fun expect(char: Char) {
            if (done() || text[at] != char) fail()
            at++
        }

        private fun fail(): Nothing = throw IllegalArgumentException("bad JSON at $at")
    }
}
