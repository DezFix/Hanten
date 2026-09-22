package hanten.wre.app.suggestions.trending

import androidx.fragment.app.viewModels
import hanten.wre.app.list.ui.MangaListFragment

class TrendingFragment : MangaListFragment() {

	override val viewModel by viewModels<TrendingViewModel>()
	override val isSwipeRefreshEnabled = true

	override fun onScrolledToEnd() = Unit
}
