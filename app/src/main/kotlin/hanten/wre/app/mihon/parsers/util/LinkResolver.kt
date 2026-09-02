package hanten.wre.app.mihon.parsers.util

import okhttp3.HttpUrl
import hanten.wre.app.mihon.parsers.model.Content
import hanten.wre.app.mihon.parsers.model.ContentSource

public interface LinkResolver {
    public val link: HttpUrl
    public suspend fun getSource(): ContentSource?
    public suspend fun getContent(): Content?
}

