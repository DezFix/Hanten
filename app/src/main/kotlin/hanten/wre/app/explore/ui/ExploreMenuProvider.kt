package hanten.wre.app.explore.ui

import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import androidx.core.view.MenuProvider
import hanten.wre.app.R
import hanten.wre.app.core.nav.AppRouter

class ExploreMenuProvider(
	private val router: AppRouter,
	private val viewModel: ExploreViewModel,
) : MenuProvider {

	override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
		menuInflater.inflate(R.menu.opt_explore, menu)
	}

	override fun onPrepareMenu(menu: Menu) {
		menu.findItem(R.id.action_grid_mode)?.setIcon(
			if (viewModel.isGrid.value) R.drawable.ic_list else R.drawable.ic_grid,
		)
	}

	override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
		return when (menuItem.itemId) {
			R.id.action_grid_mode -> {
				viewModel.toggleGridMode()
				true
			}

			R.id.action_manage -> {
				router.openSourcesSettings()
				true
			}

			else -> false
		}
	}
}
