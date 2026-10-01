package com.relay.app.widget

import android.content.Context
import android.content.Intent
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.currentState
import androidx.glance.layout.fillMaxSize
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import com.relay.app.MainActivity
import com.relay.app.appContainer

/**
 * The home-screen clock (spec §09). Each placed instance draws its own [WidgetSpec] into a
 * bitmap. Everything the drawing needs lives in the widget's Glance state, so a minute tick or a
 * config change only has to update that state and recompose.
 */
class ClockWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact
    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        val container = context.appContainer
        val spec = container.clocks.widget(appWidgetId)?.spec
        val is24h = container.settings.current().clockFormat24h
        updateAppWidgetState(context, id) { prefs ->
            if (spec != null) prefs[KEY_SPEC] = spec
            prefs[KEY_24H] = is24h
        }
        WidgetTicker.schedule(context)
        provideContent {
            val prefs = currentState<Preferences>()
            // Read so a tick recomposes; the drawing itself uses the real current time.
            prefs[KEY_TICK]
            val size = LocalSize.current
            val ctx = LocalContext.current
            val bitmap = WidgetRenderer.render(
                ctx, WidgetSpec.decode(prefs[KEY_SPEC]), size.width.value, size.height.value,
                prefs[KEY_24H] ?: true, System.currentTimeMillis(),
            )
            Image(
                provider = ImageProvider(bitmap),
                contentDescription = null,
                modifier = GlanceModifier.fillMaxSize().clickable(
                    actionStartActivity(
                        Intent(ctx, MainActivity::class.java)
                            .putExtra(MainActivity.EXTRA_OPEN_CLOCK, true)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                    ),
                ),
            )
        }
    }

    companion object {
        val KEY_SPEC = stringPreferencesKey("spec")
        val KEY_24H = booleanPreferencesKey("is24h")
        val KEY_TICK = longPreferencesKey("tick")

        /** Redraws every placed widget now (minute tick, time change, settings change). */
        suspend fun refreshAll(context: Context) {
            val manager = GlanceAppWidgetManager(context)
            val is24h = context.appContainer.settings.current().clockFormat24h
            val now = System.currentTimeMillis()
            for (id in manager.getGlanceIds(ClockWidget::class.java)) {
                updateAppWidgetState(context, id) { it[KEY_TICK] = now; it[KEY_24H] = is24h }
                ClockWidget().update(context, id)
            }
        }

        /** Applies a newly saved spec to one widget. */
        suspend fun apply(context: Context, appWidgetId: Int, spec: String) {
            val id = GlanceAppWidgetManager(context).getGlanceIdBy(appWidgetId)
            updateAppWidgetState(context, id) { it[KEY_SPEC] = spec; it[KEY_TICK] = System.currentTimeMillis() }
            ClockWidget().update(context, id)
        }
    }
}
