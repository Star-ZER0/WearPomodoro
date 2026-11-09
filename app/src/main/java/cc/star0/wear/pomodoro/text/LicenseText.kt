package cc.star0.wear.pomodoro.text

/** Bound each watch list item without dropping any part of the original license text. */
fun licenseTextBlocks(text: String, maxChars: Int = 240): List<String> {
    require(maxChars >= 2)
    return buildList {
        var start = 0
        while (start < text.length) {
            var end = minOf(start + maxChars, text.length)
            if (end < text.length) {
                val boundary = text.lastIndexOfAny(charArrayOf(' ', '\n', '\t'), end - 1)
                if (boundary >= start) end = boundary + 1
                if (text[end - 1].isHighSurrogate() && text[end].isLowSurrogate()) end--
            }
            add(text.substring(start, end))
            start = end
        }
    }
}
