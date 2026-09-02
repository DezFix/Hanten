package hanten.wre.app.core.model.parcelable

import android.os.Parcel
import android.os.Parcelable
import kotlinx.parcelize.Parceler
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.TypeParceler
import hanten.wre.app.core.util.ext.readEnumSet
import hanten.wre.app.core.util.ext.readParcelableCompat
import hanten.wre.app.core.util.ext.readSerializableCompat
import hanten.wre.app.core.util.ext.writeEnumSet
import hanten.wre.app.parsers.model.ContentRating
import hanten.wre.app.parsers.model.ContentType
import hanten.wre.app.parsers.model.Demographic
import hanten.wre.app.parsers.model.MangaListFilter
import hanten.wre.app.parsers.model.MangaState

object MangaListFilterParceler : Parceler<MangaListFilter> {

	override fun MangaListFilter.write(parcel: Parcel, flags: Int) {
		parcel.writeString(query)
		parcel.writeParcelable(ParcelableMangaTags(tags), 0)
		parcel.writeParcelable(ParcelableMangaTags(tagsExclude), 0)
		parcel.writeSerializable(locale)
		parcel.writeSerializable(originalLocale)
		parcel.writeEnumSet(states)
		parcel.writeEnumSet(contentRating)
		parcel.writeEnumSet(types)
		parcel.writeEnumSet(demographics)
		parcel.writeInt(year)
		parcel.writeInt(yearFrom)
		parcel.writeInt(yearTo)
		parcel.writeString(author)
	}

	override fun create(parcel: Parcel) = MangaListFilter(
		query = parcel.readString(),
		tags = parcel.readParcelableCompat<ParcelableMangaTags>()?.tags.orEmpty(),
		tagsExclude = parcel.readParcelableCompat<ParcelableMangaTags>()?.tags.orEmpty(),
		locale = parcel.readSerializableCompat(),
		originalLocale = parcel.readSerializableCompat(),
		states = parcel.readEnumSet<MangaState>().orEmpty(),
		contentRating = parcel.readEnumSet<ContentRating>().orEmpty(),
		types = parcel.readEnumSet<ContentType>().orEmpty(),
		demographics = parcel.readEnumSet<Demographic>().orEmpty(),
		year = parcel.readInt(),
		yearFrom = parcel.readInt(),
		yearTo = parcel.readInt(),
		author = parcel.readString(),
	)
}

@Parcelize
@TypeParceler<MangaListFilter, MangaListFilterParceler>
data class ParcelableMangaListFilter(val filter: MangaListFilter) : Parcelable
