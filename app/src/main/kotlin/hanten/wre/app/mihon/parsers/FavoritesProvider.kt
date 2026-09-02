package hanten.wre.app.mihon.parsers

import hanten.wre.app.mihon.parsers.model.Content

interface FavoritesProvider {

    suspend fun fetchFavorites(): List<Content>
}
