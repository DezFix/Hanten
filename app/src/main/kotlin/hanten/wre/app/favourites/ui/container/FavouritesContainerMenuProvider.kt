package hanten.wre.app.favourites.ui.container

import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import androidx.core.view.MenuProvider
import hanten.wre.app.R
import hanten.wre.app.core.nav.AppRouter

class FavouritesContainerMenuProvider(
	private val router: AppRouter,
	private val onNewFolder: () -> Unit,
) : MenuProvider {

	override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
		menuInflater.inflate(R.menu.opt_favourites_container, menu)
	}

	override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
		when (menuItem.itemId) {
			R.id.action_manage -> {
				router.openFavoriteCategories()
			}

			R.id.action_new_folder -> onNewFolder()

			else -> return false
		}
		return true
	}
}
