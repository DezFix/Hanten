package hanten.wre.app.settings.sources.catalog

import hanten.wre.app.parsers.model.ContentType

data class SourcesCatalogFilter(
	val types: Set<ContentType>,
	val locale: String?,
	val isNewOnly: Boolean,
)
