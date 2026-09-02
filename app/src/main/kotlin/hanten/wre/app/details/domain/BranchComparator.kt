package hanten.wre.app.details.domain

import hanten.wre.app.core.util.LocaleStringComparator
import hanten.wre.app.details.ui.model.MangaBranch

class BranchComparator : Comparator<MangaBranch> {

	private val delegate = LocaleStringComparator()

	override fun compare(o1: MangaBranch, o2: MangaBranch): Int = delegate.compare(o1.name, o2.name)
}
