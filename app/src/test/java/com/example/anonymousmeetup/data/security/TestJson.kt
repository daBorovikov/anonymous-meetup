package com.example.anonymousmeetup.data.security

object TestJson {
    fun parseObject(text: String): Map<String, Any?> {
        require(text.startsWith("{") && text.endsWith("}")) { "Invalid JSON object: $text" }
        val values = linkedMapOf<String, Any?>()
        var index = 1
        while (index < text.length - 1) {
            index = skipWhitespace(text, index)
            if (index >= text.length - 1) break
            if (text[index] == ',') {
                index++
                continue
            }
            val (key, afterKey) = parseString(text, index)
            index = skipWhitespace(text, afterKey)
            require(text[index] == ':') { "Expected ':' after key" }
            index = skipWhitespace(text, index + 1)
            val (value, afterValue) = parseValue(text, index)
            values[key] = value
            index = skipWhitespace(text, afterValue)
            if (index < text.length - 1 && text[index] == ',') index++
        }
        return values
    }

    fun string(map: Map<String, Any?>, key: String): String? = map[key]?.toString()

    fun int(map: Map<String, Any?>, key: String, default: Int = 0): Int {
        val value = map[key] ?: return default
        return when (value) {
            is Number -> value.toInt()
            else -> value.toString().toIntOrNull() ?: default
        }
    }

    fun long(map: Map<String, Any?>, key: String, default: Long = 0L): Long {
        val value = map[key] ?: return default
        return when (value) {
            is Number -> value.toLong()
            else -> value.toString().toLongOrNull() ?: default
        }
    }

    private fun parseValue(text: String, start: Int): Pair<Any?, Int> {
        return when (text[start]) {
            '"' -> parseString(text, start)
            't' -> Pair(true, start + 4)
            'f' -> Pair(false, start + 5)
            'n' -> Pair(null, start + 4)
            else -> parseNumber(text, start)
        }
    }

    private fun parseNumber(text: String, start: Int): Pair<Any, Int> {
        var index = start
        while (index < text.length && text[index] !in charArrayOf(',', '}')) {
            index++
        }
        val raw = text.substring(start, index).trim()
        return when {
            raw.contains('.') || raw.contains('e', ignoreCase = true) -> Pair(raw.toDouble(), index)
            else -> Pair(raw.toLong(), index)
        }
    }

    private fun parseString(text: String, start: Int): Pair<String, Int> {
        require(text[start] == '"') { "Expected string at $start" }
        val out = StringBuilder()
        var index = start + 1
        while (index < text.length) {
            val ch = text[index]
            when (ch) {
                '\\' -> {
                    val next = text[index + 1]
                    out.append(
                        when (next) {
                            '\\', '"', '/' -> next
                            'b' -> '\b'
                            'f' -> '\u000C'
                            'n' -> '\n'
                            'r' -> '\r'
                            't' -> '\t'
                            'u' -> {
                                val code = text.substring(index + 2, index + 6)
                                index += 4
                                code.toInt(16).toChar()
                            }
                            else -> next
                        }
                    )
                    index += 2
                }
                '"' -> return Pair(out.toString(), index + 1)
                else -> {
                    out.append(ch)
                    index++
                }
            }
        }
        error("Unterminated string")
    }

    private fun skipWhitespace(text: String, start: Int): Int {
        var index = start
        while (index < text.length && text[index].isWhitespace()) index++
        return index
    }
}
