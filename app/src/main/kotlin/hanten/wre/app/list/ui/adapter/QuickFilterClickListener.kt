package hanten.wre.app.list.ui.adapter

import hanten.wre.app.list.domain.ListFilterOption

interface QuickFilterClickListener {

	fun onFilterOptionClick(option: ListFilterOption)
}
