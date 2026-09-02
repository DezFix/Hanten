package hanten.wre.app.settings.nav.model

import androidx.annotation.StringRes
import hanten.wre.app.core.prefs.NavItem
import hanten.wre.app.list.ui.model.ListModel

data class NavItemConfigModel(
	val item: NavItem,
	@StringRes val disabledHintResId: Int,
) : ListModel {

	override fun areItemsTheSame(other: ListModel): Boolean {
		return other is NavItemConfigModel && other.item == item
	}
}
