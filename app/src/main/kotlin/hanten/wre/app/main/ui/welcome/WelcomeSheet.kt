package hanten.wre.app.main.ui.welcome

import android.accounts.AccountManager
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isGone
import androidx.core.view.updatePadding
import androidx.fragment.app.activityViewModels
import com.google.android.material.chip.Chip
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import hanten.wre.app.R
import hanten.wre.app.core.model.titleResId
import hanten.wre.app.core.nav.router
import hanten.wre.app.core.prefs.ListMode
import hanten.wre.app.core.ui.sheet.BaseAdaptiveSheet
import hanten.wre.app.core.ui.widgets.ChipsView
import hanten.wre.app.core.util.ext.consume
import hanten.wre.app.core.util.ext.getDisplayName
import hanten.wre.app.core.util.ext.observe
import hanten.wre.app.core.util.ext.tryLaunch
import hanten.wre.app.databinding.SheetWelcomeBinding
import hanten.wre.app.filter.ui.model.FilterProperty
import hanten.wre.app.parsers.model.ContentType
import java.util.Locale

@AndroidEntryPoint
class WelcomeSheet : BaseAdaptiveSheet<SheetWelcomeBinding>(), ChipsView.OnChipClickListener, View.OnClickListener,
	ActivityResultCallback<Uri?> {

	// Activity-scoped: tapping Done dismisses this sheet immediately, and a fragment-scoped
	// ViewModel would cancel a queued source/locale commit that the user just made
	private val viewModel by activityViewModels<WelcomeViewModel>()
	private val backupSelectCall = registerForActivityResult(
		ActivityResultContracts.OpenDocument(),
		this,
	)

	// Accidental protection without a trap: a stray tap outside must not skip setup, while the back
	// button and dragging keep working. The sheet used to be locked and non-cancelable, which could
	// leave the Done button below the fold with no way to reach it.
	override fun onStart() {
		super.onStart()
		dialog?.setCanceledOnTouchOutside(false)
	}

	override fun onCreateViewBinding(inflater: LayoutInflater, container: ViewGroup?): SheetWelcomeBinding {
		return SheetWelcomeBinding.inflate(inflater, container, false)
	}

	override fun onViewBindingCreated(binding: SheetWelcomeBinding, savedInstanceState: Bundle?) {
		super.onViewBindingCreated(binding, savedInstanceState)
		binding.textViewWelcomeTitle.isGone = resources.getBoolean(R.bool.is_tablet)
		binding.chipsLocales.onChipClickListener = this
		binding.chipsType.onChipClickListener = this
		binding.chipsListMode.onChipClickListener = this
		binding.chipBackup.setOnClickListener(this)
		binding.chipSync.setOnClickListener(this)
		binding.chipDirectories.setOnClickListener(this)

		viewModel.locales.observe(viewLifecycleOwner, ::onLocalesChanged)
		viewModel.types.observe(viewLifecycleOwner, ::onTypesChanged)
		viewModel.listMode.observe(viewLifecycleOwner, ::onListModeChanged)
	}

	override fun onApplyWindowInsets(v: View, insets: WindowInsetsCompat): WindowInsetsCompat {
		val typeMask = WindowInsetsCompat.Type.systemBars()
		viewBinding?.scrollView?.updatePadding(
			bottom = insets.getInsets(typeMask).bottom,
		)
		return insets.consume(v, typeMask, bottom = true)
	}

	override fun onChipClick(chip: Chip, data: Any?) {
		when (data) {
			is ContentType -> viewModel.setTypeChecked(data, !chip.isChecked)
			is Locale -> viewModel.setLocaleChecked(data, !chip.isChecked)
			is ListMode -> viewModel.setListMode(data)
		}
	}

	override fun onClick(v: View) {
		when (v.id) {
			R.id.chip_backup -> {
				if (!backupSelectCall.tryLaunch(arrayOf("*/*"))) {
					Snackbar.make(
						v, R.string.operation_not_supported, Snackbar.LENGTH_SHORT,
					).show()
				}
			}

			R.id.chip_sync -> {
				val am = AccountManager.get(v.context)
				val accountType = getString(R.string.account_type_sync)
				am.addAccount(accountType, accountType, null, null, requireActivity(), null, null)
			}

            R.id.chip_directories -> {
                router.openDirectoriesSettings()
            }
		}
	}

	override fun onActivityResult(result: Uri?) {
		if (result != null) {
			router.showBackupRestoreDialog(result)
		}
	}

	private fun onLocalesChanged(value: FilterProperty<Locale>) {
		val chips = viewBinding?.chipsLocales ?: return
		chips.setChips(
			value.availableItems.map {
				ChipsView.ChipModel(
					title = it.getDisplayName(chips.context),
					isChecked = it in value.selectedItems,
					data = it,
				)
			},
		)
	}

	private fun onTypesChanged(value: FilterProperty<ContentType>) {
		val chips = viewBinding?.chipsType ?: return
		chips.setChips(
			value.availableItems.map {
				ChipsView.ChipModel(
					title = getString(it.titleResId),
					isChecked = it in value.selectedItems,
					data = it,
				)
			},
		)
	}

	private fun onListModeChanged(selected: ListMode) {
		val chips = viewBinding?.chipsListMode ?: return
		chips.setChips(
			ListMode.entries.map {
				ChipsView.ChipModel(
					title = getString(it.titleResId),
					isChecked = it == selected,
					data = it,
				)
			},
		)
	}

	@get:StringRes
	private val ListMode.titleResId: Int
		get() = when (this) {
			ListMode.LIST -> R.string.list
			ListMode.DETAILED_LIST -> R.string.detailed_list
			ListMode.GRID -> R.string.grid
		}

}
