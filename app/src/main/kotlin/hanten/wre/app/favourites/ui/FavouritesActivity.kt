package hanten.wre.app.favourites.ui

import android.os.Bundle
import hanten.wre.app.core.nav.AppRouter
import hanten.wre.app.core.ui.FragmentContainerActivity
import hanten.wre.app.favourites.ui.list.FavouritesListFragment

class FavouritesActivity : FragmentContainerActivity(FavouritesListFragment::class.java) {

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		val categoryTitle = intent.getStringExtra(AppRouter.KEY_TITLE)
		if (categoryTitle != null) {
			title = categoryTitle
		}
	}
}
