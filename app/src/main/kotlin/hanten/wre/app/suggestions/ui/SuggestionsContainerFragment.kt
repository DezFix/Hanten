package hanten.wre.app.suggestions.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.google.android.material.tabs.TabLayoutMediator
import dagger.hilt.android.AndroidEntryPoint
import hanten.wre.app.R
import hanten.wre.app.core.ui.BaseFragment
import hanten.wre.app.databinding.FragmentSuggestionsContainerBinding
import hanten.wre.app.suggestions.trending.TrendingFragment

@AndroidEntryPoint
class SuggestionsContainerFragment : BaseFragment<FragmentSuggestionsContainerBinding>() {

	override fun onCreateViewBinding(
		inflater: LayoutInflater,
		container: ViewGroup?,
	) = FragmentSuggestionsContainerBinding.inflate(inflater, container, false)

	override fun onViewBindingCreated(binding: FragmentSuggestionsContainerBinding, savedInstanceState: Bundle?) {
		super.onViewBindingCreated(binding, savedInstanceState)
		binding.pager.adapter = SuggestionsContainerAdapter(this)
		binding.pager.offscreenPageLimit = 1
		TabLayoutMediator(binding.tabs, binding.pager) { tab, position ->
			tab.setText(
				if (position == 0) R.string.suggestions_personal else R.string.suggestions_trending,
			)
		}.attach()
	}

	override fun onApplyWindowInsets(v: View, insets: WindowInsetsCompat): WindowInsetsCompat = insets

	private class SuggestionsContainerAdapter(
		fragment: Fragment,
	) : FragmentStateAdapter(fragment) {

		override fun getItemCount() = 2

		override fun createFragment(position: Int): Fragment = if (position == 0) {
			SuggestionsFragment()
		} else {
			TrendingFragment()
		}
	}
}
