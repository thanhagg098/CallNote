package vn.thanh.callnote

/** Dọn lỗi "kẹt" của whisper: một cụm từ lặp lại nhiều lần liên tiếp chỉ giữ 1 lần. */
object TextClean {
    fun removeLoops(text: String): String {
        val lines = text.lines().map { line ->
            val ts = Regex("""^\[\d+:\d+]\s*""").find(line)?.value ?: ""
            ts + collapse(line.removePrefix(ts))
        }
        // Bỏ dòng trùng y hệt dòng ngay trước (bỏ qua mốc thời gian)
        val out = ArrayList<String>()
        var prev = ""
        for (l in lines) {
            val body = l.substringAfter("] ", l).trim()
            if (body.isNotEmpty() && body == prev) continue
            out.add(l); prev = body
        }
        return out.joinToString("\n")
    }

    /** Cụm 1–8 từ lặp liên tiếp ≥3 lần → giữ 1 lần. */
    private fun collapse(s: String): String {
        var w = s.split(' ').filter { it.isNotEmpty() }
        var changed = true
        while (changed) {
            changed = false
            loop@ for (n in 1..8) {
                var i = 0
                while (i + 3 * n <= w.size) {
                    var reps = 1
                    while (i + (reps + 1) * n <= w.size && same(w, i, i + reps * n, n)) reps++
                    if (reps >= 3) {
                        w = w.subList(0, i + n) + w.subList(i + reps * n, w.size)
                        changed = true
                        break@loop
                    }
                    i++
                }
            }
        }
        return w.joinToString(" ")
    }

    private fun norm(x: String) = x.lowercase().trim(',', '.', '!', '?', ';', ':')

    private fun same(w: List<String>, a: Int, b: Int, n: Int): Boolean {
        for (k in 0 until n) if (norm(w[a + k]) != norm(w[b + k])) return false
        return true
    }
}
