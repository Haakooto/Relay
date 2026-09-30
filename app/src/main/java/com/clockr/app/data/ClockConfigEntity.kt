package com.clockr.app.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class ClockStyle { DIGITAL, ANALOGUE }

/** "Seven-segment"/custom LCD-style digit font is deferred — needs a bundled font asset, not a
 *  system-font choice like these three. */
enum class ClockDigitalFont { DEFAULT, SERIF, MONOSPACE }

enum class ClockAnalogueVariant { MINIMAL, SLEEK, CLASSIC, TECHY }

/** Maps to the brand palette in ui/theme/Color.kt — a free color picker was explicitly ruled out
 *  in favor of this existing, smaller set. */
enum class ClockColor { INK, ACCENT, TEAL, BRASS }

/** Widget-only — the in-app Clock pane always follows the app theme, no separate background
 *  setting. [ClockConfigEntity.backgroundColor] only matters when this is SOLID. */
enum class ClockBackground { FOLLOW_THEME, TRANSPARENT, SOLID }

/** Widget-only. */
enum class ClockCornerStyle { SHARP, ROUNDED, PILL }

/**
 * One styled, independently-configurable clock — either an in-app Clock-pane entry
 * ([appWidgetId] null) or one specific placed home-screen widget instance ([appWidgetId] set to
 * that widget's real Android app-widget id). Every instance of either kind is just its own row;
 * that's what makes per-instance configuration trivial rather than needing separate "in-app clock"
 * and "widget instance" tables. No `@ForeignKey` — this codebase never uses Room FK constraints
 * (see AlarmExceptionEntity's doc comment); cleanup on delete is explicit application code.
 */
@Entity(tableName = "clock_configs", indices = [Index("appWidgetId")])
data class ClockConfigEntity(
    @PrimaryKey val id: String,
    val name: String,
    val style: ClockStyle,
    val digitalFont: ClockDigitalFont,
    val analogueVariant: ClockAnalogueVariant,
    val color: ClockColor,
    val showSeconds: Boolean,
    val showDate: Boolean,
    val background: ClockBackground,
    /** Only used when [background] == SOLID. */
    val backgroundColor: ClockColor,
    val cornerStyle: ClockCornerStyle,
    /** Border color always matches [color] — not a separately pickable fourth color. */
    val borderEnabled: Boolean,
    val borderWidthDp: Int,
    /** In-app list ordering only — same fractional/append-at-end convention as
     *  [LibraryHeaderEntity.sortOrder]. Meaningless for a widget instance. */
    val sortOrder: Long = System.currentTimeMillis(),
    val appWidgetId: Int? = null,
    /** Reserved for future per-clock timezone support — no picker UI reads/writes this yet. */
    val timezoneId: String? = null,
) {
    companion object {
        /** Today's exact existing look — used both as the first-run seed for the in-app Clocks
         *  list and as a safety fallback if a widget's config row is ever missing. */
        fun default(id: String, appWidgetId: Int? = null, name: String = "") = ClockConfigEntity(
            id = id,
            name = name,
            style = ClockStyle.DIGITAL,
            digitalFont = ClockDigitalFont.DEFAULT,
            analogueVariant = ClockAnalogueVariant.CLASSIC,
            color = ClockColor.INK,
            showSeconds = true,
            showDate = true,
            background = ClockBackground.FOLLOW_THEME,
            backgroundColor = ClockColor.INK,
            cornerStyle = ClockCornerStyle.ROUNDED,
            borderEnabled = false,
            borderWidthDp = 2,
            appWidgetId = appWidgetId,
        )
    }
}
