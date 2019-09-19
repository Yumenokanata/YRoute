package indi.yume.tools.yroute

import arrow.core.toT
import arrow.fx.IO
import arrow.mtl.runId
import indi.yume.tools.yroute.datatype.Fail
import indi.yume.tools.yroute.datatype.YRoute
import indi.yume.tools.yroute.datatype.lazy.LazyYRoute
import indi.yume.tools.yroute.datatype.lazy.lazyR1
import indi.yume.tools.yroute.datatype.lazy.mapResult
import indi.yume.tools.yroute.datatype.lazy.routeF
import indi.yume.tools.yroute.datatype.mapResult
import indi.yume.tools.yroute.datatype.runRoute
import java.net.URI
import java.net.URLDecoder


object UriRoute {
    fun <S> build(host: String, f: RouteNaviBuilder<S>.() -> Unit): RouteNavi<S> {
        val builder = RouteNaviBuilder<S>(host)
        builder.f()
        return builder.create()
    }
}

class RouteNaviBuilder<S>(val host: String) {
    var routeMap: MutableMap<String, LazyYRoute<S, URI, Any?>> = emptyMap<String, LazyYRoute<S, URI, Any?>>().toMutableMap()

    fun <R> putRoute(path: String, route: YRoute<S, R>): RouteNaviBuilder<S> {
        routeMap.put(path, lazyR1 { route.mapResult { it as Any? } })
        return this
    }

    /**
     * @param path path of uri, eg: "/page1/other"
     */
    fun <R> put(path: String, route: LazyYRoute<S, URI, R>): RouteNaviBuilder<S> {
        routeMap.put(path, route.mapResult { it as Any? })
        return this
    }

    fun <R> put(path: String, route: (URI) -> YRoute<S, R>): RouteNaviBuilder<S> {
        routeMap.put(path, lazyR1(route).mapResult { it as Any? })
        return this
    }

    fun create(): RouteNavi<S> {
        val map = routeMap.toMap()
        return routeF { state, routeCxt, uriString ->
            val uri = URI(uriString)
            uri.query
            if (uri.host != host) IO.just(state toT Fail("Host is not fit, host is $host, but uri=$uriString"))
            val targetRoute = map[uri.path]
            if (targetRoute == null) IO.just(state toT Fail("Can not found target route from uri=$uriString"))
            else targetRoute.runId(uri).runRoute(state, routeCxt)
        }
    }
}

typealias RouteNavi<S> = LazyYRoute<S, String, Any?>

fun String.getAllQuery(): Map<String, String> =
    split("&").map {
        val item = it.split("=")
        item[0] to URLDecoder.decode(item[1], "UTF-8")
    }.toMap()

