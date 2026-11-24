package hanten.wre.app.more.ui

import hanten.wre.app.databinding.ItemMoreRowBinding
import androidx.core.text.inSpans
import android.text.style.RelativeSizeSpan
import android.text.style.ForegroundColorSpan
import android.text.SpannableStringBuilder
import android.graphics.drawable.GradientDrawable
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.text.InputFilter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.GridLayout
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import dagger.hilt.android.AndroidEntryPoint
import hanten.wre.app.R
import hanten.wre.app.core.nav.AppRouter
import hanten.wre.app.core.nav.router
import hanten.wre.app.core.prefs.AppSettings
import hanten.wre.app.core.ui.BaseFragment
import hanten.wre.app.core.util.ext.getThemeColor
import hanten.wre.app.databinding.FragmentMoreBinding
import hanten.wre.app.profile.ProfileActivity
import hanten.wre.app.profile.ProfileStyle
import javax.inject.Inject
import androidx.appcompat.R as appcompatR
import com.google.android.material.R as materialR

/**
 * "More" tab: the local profile, personal sections (downloads, history, bookmarks…), incognito and
 * the settings sections, so settings are one tap away instead of hidden in the toolbar menu.
 */
@AndroidEntryPoint
class MoreFragment : BaseFragment<FragmentMoreBinding>() {

	@Inject
	lateinit var settings: AppSettings

	private class Entry(
		@DrawableRes val icon: Int,
		@StringRes val title: Int,
		@StringRes val hint: Int = 0,
		val action: AppRouter.() -> Unit,
	)

	private val quick = listOf(
		Entry(R.drawable.ic_download, R.string.downloads) { openDownloads() },
		Entry(R.drawable.ic_history, R.string.history) { openHistory() },
		Entry(R.drawable.ic_bookmark, R.string.bookmarks) { openBookmarks() },
		// «На устройстве» lives on the Catalog tab
		// «Лента» is reachable from Today («Новые главы → Все»); «Обновления» showed the same in another form
		Entry(R.drawable.ic_updated, R.string.upcoming_title) { openUpcoming() },
	)

	private val sections = listOf(
		Entry(R.drawable.ic_appearance, R.string.appearance, R.string.more_hint_appearance) { openAppearanceSettings() },
		Entry(R.drawable.ic_reader_ltr, R.string.reader_settings, R.string.more_hint_reader) { openReaderSettings() },
		Entry(R.drawable.ic_web, R.string.remote_sources, R.string.more_hint_sources) { openSourcesSettings() },
		Entry(R.drawable.ic_notification, R.string.check_for_new_chapters, R.string.more_hint_tracker) {
			openTrackerSettings()
		},
		Entry(R.drawable.ic_download, R.string.downloads, R.string.more_hint_downloads) { openDownloadsSetting() },
		Entry(R.drawable.ic_storage, R.string.storage_and_network, R.string.more_hint_storage) { openStorageSettings() },
		Entry(R.drawable.ic_services, R.string.services, R.string.more_hint_services) { openServicesSettings() },
		Entry(R.drawable.ic_backup_restore, R.string.backup_screen_title, R.string.more_hint_backup) { openBackupSettings() },
		Entry(R.drawable.ic_manga_source, R.string.onboarding_rerun, R.string.onboarding_rerun_hint) { showOnboardingAgain() },
		Entry(R.drawable.ic_info_outline, R.string.about, R.string.more_hint_about) { openAboutSettings() },
	)

	override fun onCreateViewBinding(inflater: LayoutInflater, container: ViewGroup?) =
		FragmentMoreBinding.inflate(inflater, container, false)

	override fun onViewBindingCreated(binding: FragmentMoreBinding, savedInstanceState: Bundle?) {
		super.onViewBindingCreated(binding, savedInstanceState)
		val inflater = LayoutInflater.from(binding.root.context)
		val accent = binding.root.context.getThemeColor(appcompatR.attr.colorPrimary)
		val muted = binding.root.context.getThemeColor(materialR.attr.colorOnSurfaceVariant)
		quick.forEach { entry ->
			val tile = inflater.inflate(R.layout.item_more_tile, binding.gridQuick, false) as TextView
			tile.setText(entry.title)
			tile.setCompoundDrawablesRelativeWithIntrinsicBounds(0, entry.icon, 0, 0)
			tile.compoundDrawableTintList = ColorStateList.valueOf(accent)
			tile.setOnClickListener { entry.action.invoke(router) }
			tile.layoutParams = GridLayout.LayoutParams(
				GridLayout.spec(GridLayout.UNDEFINED),
				GridLayout.spec(GridLayout.UNDEFINED, 1f),
			).apply {
				width = 0
				setMargins(TILE_MARGIN, TILE_MARGIN, TILE_MARGIN, TILE_MARGIN)
			}
			binding.gridQuick.addView(tile)
		}
		sections.forEach { entry ->
			val row = ItemMoreRowBinding.inflate(inflater, binding.listSettings, false)
			row.textTitle.setText(entry.title)
			row.imageIcon.setImageResource(entry.icon)
			row.imageIcon.imageTintList = ColorStateList.valueOf(muted)
			if (entry.hint != 0) row.textHint.setText(entry.hint) else row.textHint.text = null
			row.root.setOnClickListener { entry.action.invoke(router) }
			binding.listSettings.addView(row.root)
		}
		// Incognito with a short explanation under the title (mockup)
		binding.switchIncognito.text = SpannableStringBuilder(getString(R.string.incognito_mode))
			.append('\n')
			.inSpans(
				RelativeSizeSpan(HINT_SIZE),
				ForegroundColorSpan(muted),
			) { append(getString(R.string.more_incognito_hint)) }
		binding.buttonProfile.setOnClickListener {
			startActivity(Intent(it.context, ProfileActivity::class.java))
		}
		binding.switchIncognito.isChecked = settings.isIncognitoModeEnabled
		binding.switchIncognito.setOnCheckedChangeListener { _, isChecked ->
			settings.isIncognitoModeEnabled = isChecked
		}
		binding.cardProfile.setOnClickListener {
			startActivity(Intent(it.context, ProfileActivity::class.java))
		}
		binding.cardProfile.setOnLongClickListener {
			showProfileNameDialog()
			true
		}
	}

	override fun onResume() {
		super.onResume()
		bindProfile()
		viewBinding?.switchIncognito?.isChecked = settings.isIncognitoModeEnabled
	}

	override fun onApplyWindowInsets(v: View, insets: WindowInsetsCompat): WindowInsetsCompat = insets

	private fun bindProfile() {
		val binding = viewBinding ?: return
		val name = settings.profileName
		binding.textProfileName.text = name ?: getString(R.string.profile_no_name)
		binding.textAvatar.text = ProfileStyle.avatarText(settings.profileAvatar, name)
		// avatar in the profile color with an accent ring (mockup)
		binding.textAvatar.backgroundTintList = null
		binding.textAvatar.background = GradientDrawable().apply {
			shape = GradientDrawable.OVAL
			setColor(ProfileStyle.color(settings.profileColor))
			setStroke(
				resources.getDimensionPixelSize(R.dimen.profile_choice_ring),
				binding.root.context.getThemeColor(appcompatR.attr.colorPrimary),
			)
		}
	}

	private fun showProfileNameDialog() {
		val context = context ?: return
		val input = TextInputEditText(context).apply {
			setText(settings.profileName)
			hint = getString(R.string.profile_name_hint)
			filters = arrayOf(InputFilter.LengthFilter(MAX_NAME_LENGTH))
			setSingleLine()
		}
		MaterialAlertDialogBuilder(context)
			.setTitle(R.string.onboarding_profile_title)
			.setView(input)
			.setNegativeButton(android.R.string.cancel, null)
			.setPositiveButton(android.R.string.ok) { _, _ ->
				settings.profileName = input.text?.toString()
				bindProfile()
			}
			.show()
	}

	private companion object {

		const val TILE_MARGIN = 8
		const val HINT_SIZE = 0.85f
		const val MAX_NAME_LENGTH = 24
	}
}
