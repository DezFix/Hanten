@file:JvmName("ContentParsersUtils")

package hanten.wre.app.mihon.parsers.util

import hanten.wre.app.mihon.parsers.model.ContentChapter
import hanten.wre.app.mihon.parsers.model.ContentListFilter
import kotlin.contracts.contract

fun ContentListFilter?.isNullOrEmpty(): Boolean {
	contract {
		returns(false) implies (this@isNullOrEmpty != null)
	}
	return this == null || this.isEmpty()
}

fun Collection<ContentChapter>.findById(chapterId: Long): ContentChapter? = find { x ->
	x.id == chapterId
}
