package hanten.wre.app.search.ui.suggestion.adapter

import android.widget.TextView
import com.hannesdorfmann.adapterdelegates4.dsl.adapterDelegate
import hanten.wre.app.R
import hanten.wre.app.search.ui.suggestion.model.SearchSuggestionItem

/** Hanten 3.0: section title in the search suggestions ("Recent searches", "Genres", ...). */
fun searchSuggestionHeaderAD() = adapterDelegate<SearchSuggestionItem.Header, SearchSuggestionItem>(
	R.layout.item_search_suggestion_header,
) {

	bind {
		(itemView as TextView).setText(item.titleResId)
	}
}
