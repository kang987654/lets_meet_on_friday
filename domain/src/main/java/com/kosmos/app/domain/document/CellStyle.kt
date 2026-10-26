package com.kosmos.app.domain.document

/**
 * 셀 서식 — 화면 표시용 원본 서식(배경·글자색·굵기·가로 정렬). 색은 불투명 `0xFFRRGGBB`, 원본에 없으면 null.
 *
 * [WHY] 서식은 글자와 따로 둔다 — 모델에 보내는 표 글자([DocumentText])에는 서식이 섞이면 안 된다(0.38.7 계획 목표).
 */
data class CellStyle(
    val fill: Int? = null,
    val fontColor: Int? = null,
    val bold: Boolean = false,
    val align: Align? = null
) {
    enum class Align { LEFT, CENTER, RIGHT }

    /** 원본 서식이 아무것도 없는가. */
    val isPlain: Boolean get() = fill == null && fontColor == null && !bold && align == null
}

/**
 * xlsx 색 표기를 실제 색으로 — 직접 RGB, 테마 색 + 밝기 조정(tint), 옛 색표 번호(indexed).
 *
 * ### Key Flow
 * `rgb` 가 있으면 그대로, `theme` 이면 통합 문서 테마(`theme1.xml` 의 clrScheme)에서 고른 뒤 [applyTint], `indexed` 면 [INDEXED].
 */
object XlsxColor {

    /**
     * 테마 색 번호 → clrScheme 원소 이름.
     *
     * [WHY] 엑셀은 앞 네 개를 파일 순서(dk1, lt1, dk2, lt2)와 바꿔 쓴다 — 번호 0 이 lt1(배경), 1 이 dk1(글자)이다.
     */
    val THEME_ORDER = listOf("lt1", "dk1", "lt2", "dk2", "accent1", "accent2", "accent3", "accent4", "accent5", "accent6", "hlink", "folHlink")

    /** `rgb="FFRRGGBB"`(또는 6자리) → 불투명 색. 알파는 무시한다(엑셀도 화면 표시에 쓰지 않는다). */
    fun parseRgb(rgb: String?): Int? {
        val hex = rgb?.trim()?.takeLast(6)?.takeIf { it.length == 6 } ?: return null
        return hex.toIntOrNull(16)?.let { it or OPAQUE }
    }

    /** 색 하나 해석 — 알 수 없거나 "자동"이면 null. */
    fun resolve(rgb: String?, theme: Int?, tint: Double?, indexed: Int?, themeColors: List<Int>): Int? {
        val base = parseRgb(rgb)
            ?: theme?.let { themeColors.getOrNull(it) }
            ?: indexed?.let { INDEXED.getOrNull(it) }
            ?: return null
        return if (tint == null || tint == 0.0) base else applyTint(base, tint)
    }

    /** 엑셀 tint — HSL 밝기를 어둡게(음수) 또는 밝게(양수). */
    fun applyTint(color: Int, tint: Double): Int {
        val (h, s, l) = toHsl(color)
        val lum = if (tint < 0) l * (1 + tint) else l * (1 - tint) + tint
        return fromHsl(h, s, lum.coerceIn(0.0, 1.0))
    }

    fun toHsl(color: Int): Triple<Double, Double, Double> {
        val r = (color shr 16 and 0xFF) / 255.0
        val g = (color shr 8 and 0xFF) / 255.0
        val b = (color and 0xFF) / 255.0
        val max = maxOf(r, g, b)
        val min = minOf(r, g, b)
        val l = (max + min) / 2
        if (max == min) return Triple(0.0, 0.0, l)
        val d = max - min
        val s = if (l > 0.5) d / (2 - max - min) else d / (max + min)
        val h = when (max) {
            r -> ((g - b) / d + (if (g < b) 6 else 0))
            g -> (b - r) / d + 2
            else -> (r - g) / d + 4
        } / 6
        return Triple(h, s, l)
    }

    fun fromHsl(h: Double, s: Double, l: Double): Int {
        fun channel(t0: Double, p: Double, q: Double): Int {
            val t = (t0 + 1) % 1
            val v = when {
                t < 1.0 / 6 -> p + (q - p) * 6 * t
                t < 1.0 / 2 -> q
                t < 2.0 / 3 -> p + (q - p) * (2.0 / 3 - t) * 6
                else -> p
            }
            return (v * 255 + 0.5).toInt().coerceIn(0, 255)
        }
        if (s == 0.0) {
            val v = (l * 255 + 0.5).toInt().coerceIn(0, 255)
            return OPAQUE or (v shl 16) or (v shl 8) or v
        }
        val q = if (l < 0.5) l * (1 + s) else l + s - l * s
        val p = 2 * l - q
        return OPAQUE or (channel(h + 1.0 / 3, p, q) shl 16) or (channel(h, p, q) shl 8) or channel(h - 1.0 / 3, p, q)
    }

    private const val OPAQUE = 0xFF shl 24

    /** 옛 색표(indexed 0~63) — 0~7 은 8~15 와 같다. 64·65(시스템 글자·배경)는 "자동"이라 담지 않는다. */
    val INDEXED: List<Int> = listOf(
        0x000000, 0xFFFFFF, 0xFF0000, 0x00FF00, 0x0000FF, 0xFFFF00, 0xFF00FF, 0x00FFFF,
        0x000000, 0xFFFFFF, 0xFF0000, 0x00FF00, 0x0000FF, 0xFFFF00, 0xFF00FF, 0x00FFFF,
        0x800000, 0x008000, 0x000080, 0x808000, 0x800080, 0x008080, 0xC0C0C0, 0x808080,
        0x9999FF, 0x993366, 0xFFFFCC, 0xCCFFFF, 0x660066, 0xFF8080, 0x0066CC, 0xCCCCFF,
        0x000080, 0xFF00FF, 0xFFFF00, 0x00FFFF, 0x800080, 0x800000, 0x008080, 0x0000FF,
        0x00CCFF, 0xCCFFFF, 0xCCFFCC, 0xFFFF99, 0x99CCFF, 0xFF99CC, 0xCC99FF, 0xFFCC99,
        0x3366FF, 0x33CCCC, 0x99CC00, 0xFFCC00, 0xFF9900, 0xFF6600, 0x666699, 0x969696,
        0x003366, 0x339966, 0x003300, 0x333300, 0x993300, 0x993366, 0x333399, 0x333333
    ).map { it or OPAQUE }
}
