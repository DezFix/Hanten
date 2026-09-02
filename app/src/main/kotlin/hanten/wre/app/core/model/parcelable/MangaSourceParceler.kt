package hanten.wre.app.core.model.parcelable

import android.os.Parcel
import kotlinx.parcelize.Parceler
import hanten.wre.app.core.model.MangaSource
import hanten.wre.app.parsers.model.MangaSource

class MangaSourceParceler : Parceler<MangaSource> {

	override fun create(parcel: Parcel): MangaSource = MangaSource(parcel.readString())

	override fun MangaSource.write(parcel: Parcel, flags: Int) {
		parcel.writeString(name)
	}
}
