package hanten.wre.app.reader.ui

import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import androidx.core.view.MenuProvider
import hanten.wre.app.R

class ReaderMenuProvider(
	private val viewModel: ReaderViewModel,
) : MenuProvider {

	override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
		menuInflater.inflate(R.menu.opt_reader, menu)
	}

	override fun onPrepareMenu(menu: Menu) {
		// Hanten 3.0: bookmark sits in the top bar (mockup); the icon shows whether this page is saved
		val isAdded = viewModel.isBookmarkAdded.value
		menu.findItem(R.id.action_bookmark)?.run {
			setIcon(if (isAdded) R.drawable.ic_bookmark_added else R.drawable.ic_bookmark)
			setTitle(if (isAdded) R.string.bookmark_remove else R.string.bookmark_add)
		}
	}

	override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
		return when (menuItem.itemId) {
			R.id.action_bookmark -> {
				viewModel.toggleBookmark()
				true
			}

			R.id.action_info -> {
				// TODO
				true
			}

			else -> false
		}
	}
}
