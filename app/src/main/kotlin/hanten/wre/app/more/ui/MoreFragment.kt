package hanten.wre.app.more.ui

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

	private class Entry(@DrawableRes val icon: Int, @StringRes val title: Int, val action: AppRouter.() -> Unit)

	private val quick = listOf(
		Entry(R.drawable.ic_download, R.string.downloads) { openDownloads() },
		Entry(R.drawable.ic_history, R.string.history) { openHistory() },
		Entry(R.drawable.ic_bookmark, R.string.bookmarks) { openBookmarks() },
		Entry(R.drawable.ic_book_page, R.string.statistics) { openStatistic() },
		Entry(R.drawable.ic_updated, R.string.more_updates) { openMangaUpdates() },
		Entry(R.drawable.ic_folder_file, R.string.library_folders) { openFavoriteCategories() },
	)

	private val sections = listOf(
		Entry(R.drawable.ic_appearance, R.string.appearance) { openAppearanceSettings() },
		Entry(R.drawable.ic_reader_ltr, R.string.reader_settings) { openReaderSettings() },
		Entry(R.drawable.ic_web, R.string.remote_sources) { openSourcesSettings() },
		Entry(R.drawable.ic_notification, R.string.check_for_new_chapters) { openTrackerSettings() },
		Entry(R.drawable.ic_download, R.string.downloads) { openDownloadsSetting() },
		Entry(R.drawable.ic_storage, R.string.storage_and_network) { openStorageSettings() },
		Entry(R.drawable.ic_services, R.string.services) { openServicesSettings() },
		Entry(R.drawable.ic_backup_restore, R.string.backup_restore) { openBackupSettings() },
		Entry(R.drawable.ic_info_outline, R.string.about) { openAboutSettings() },
		Entry(R.drawable.ic_settings, R.string.settings) { openSettings() },
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
			val row = inflater.inflate(R.layout.item_more_row, binding.listSettings, false) as TextView
			row.setText(entry.title)
			row.setCompoundDrawablesRelativeWithIntrinsicBounds(
				ContextCompat.getDrawable(row.context, entry.icon), null, null, null,
			)
			row.compoundDrawableTintList = ColorStateList.valueOf(muted)
			row.setOnClickListener { entry.action.invoke(router) }
			binding.listSettings.addView(row)
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
		binding.textAvatar.backgroundTintList = ColorStateList.valueOf(ProfileStyle.color(settings.profileColor))
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
		const val MAX_NAME_LENGTH = 24
	}
}
