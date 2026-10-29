package net.kuafuai.andee.acceptance

import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import net.kuafuai.andee.R
import net.kuafuai.andee.config.VaultBioGate
import net.kuafuai.andee.i18n.AppLocale
import net.kuafuai.andee.ui.SettingsUi
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Phase 10A isolated Android UI regression. No Overlay grant, no live APIs,
 * no credentials, and no changes to saved Android settings.
 */
@RunWith(AndroidJUnit4::class)
class SettingsNavigationTest {
    private fun newSettings(): SettingsUi = SettingsUi(
        context = InstrumentationRegistry.getInstrumentation().targetContext,
        onDismiss = {},
        onFactoryReset = {},
        onClearChat = {},
        onClearNotes = {},
    )

    private fun setPage(ui: SettingsUi, name: String) {
        val field = SettingsUi::class.java.getDeclaredField("page").apply { isAccessible = true }
        val item = field.type.enumConstants.first { (it as Enum<*>).name == name }
        field.set(ui, item)
    }

    private fun render(ui: SettingsUi): View =
        SettingsUi::class.java.getDeclaredMethod("build").apply { isAccessible = true }
            .invoke(ui) as View

    private fun stage(ui: SettingsUi) {
        SettingsUi::class.java.getDeclaredMethod("stageVisibleEdits")
            .apply { isAccessible = true }.invoke(ui)
    }

    @Suppress("UNCHECKED_CAST")
    private fun fields(ui: SettingsUi): MutableMap<String, EditText> =
        SettingsUi::class.java.getDeclaredField("fields").apply { isAccessible = true }
            .get(ui) as MutableMap<String, EditText>

    private fun clearViews(ui: SettingsUi) {
        fields(ui).clear()
        val picks = SettingsUi::class.java.getDeclaredField("picks").apply { isAccessible = true }
            .get(ui) as MutableMap<*, *>
        picks.clear()
    }

    @Test
    fun fiveHomeRoutesAndExistingSettingsPagesBuildWithVaultLocked() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        VaultBioGate.lockNow()
        val ui = newSettings()
        val home = render(ui)
        val titles = listOf(
            R.string.settings_nav_ai_voice, R.string.settings_nav_appearance,
            R.string.settings_nav_memory, R.string.settings_nav_device,
            R.string.settings_nav_advanced,
        ).map { AppLocale.wrap(app).getString(it) }
        fun visibleText(view: View): String = when (view) {
            is android.widget.TextView -> view.text.toString()
            is ViewGroup -> (0 until view.childCount).joinToString("\n") {
                visibleText(view.getChildAt(it))
            }
            else -> ""
        }
        val homeText = visibleText(home)
        for (title in titles) assertTrue("Missing home route: $title", homeText.contains(title))

        for (page in listOf("AI_VOICE", "APPEARANCE", "MEMORY", "DEVICE", "ADVANCED")) {
            clearViews(ui)
            setPage(ui, page)
            assertNotNull("$page must render on a device with locked Vault", render(ui))
        }
        assertFalse("Settings must not unlock Vault", VaultBioGate.ready(app))
    }

    @Test
    fun unsavedApiEditSurvivesNavigationWithoutTouchingPreferences() {
        val ui = newSettings()
        setPage(ui, "AI_VOICE")
        render(ui)
        val input = fields(ui)["llm_model"]
        assertNotNull("original model setting must remain", input)
        input!!.setText("phase10a-unsaved-model-fixture")
        stage(ui)

        clearViews(ui)
        setPage(ui, "MEMORY")
        render(ui)
        stage(ui)

        clearViews(ui)
        setPage(ui, "AI_VOICE")
        render(ui)
        assertEquals("phase10a-unsaved-model-fixture", fields(ui)["llm_model"]?.text?.toString())
        // No Save was invoked; this test must never call a real provider.
    }
}
