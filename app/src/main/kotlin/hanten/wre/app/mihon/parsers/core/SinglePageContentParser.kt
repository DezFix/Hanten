package hanten.wre.app.mihon.parsers.core

import hanten.wre.app.mihon.parsers.ContentLoaderContext
import hanten.wre.app.mihon.parsers.InternalParsersApi
import hanten.wre.app.mihon.parsers.model.Content
import hanten.wre.app.mihon.parsers.model.ContentListFilter
import hanten.wre.app.mihon.parsers.model.ContentSource
import hanten.wre.app.mihon.parsers.model.SortOrder

@InternalParsersApi
public abstract class SinglePageContentParser(
	context: ContentLoaderContext,
	source: ContentSource,
) : AbstractContentParser(context, source) {

	final override suspend fun getList(offset: Int, order: SortOrder, filter: ContentListFilter): List<Content> {
		if (offset > 0) {
			return emptyList()
		}
		return getList(order, filter)
	}

	public abstract suspend fun getList(order: SortOrder, filter: ContentListFilter): List<Content>
}

