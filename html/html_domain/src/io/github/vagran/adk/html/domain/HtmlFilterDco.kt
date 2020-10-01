package io.github.vagran.adk.html.domain

import io.github.vagran.adk.domain.httpserver.Endpoint
import io.github.vagran.adk.domain.httpserver.HttpError
import io.github.vagran.adk.domain.httpserver.HttpRequestContext
import io.github.vagran.adk.html.HtmlFilter
import io.github.vagran.adk.html.HtmlFilterParser
import io.github.vagran.adk.omm.OmmField


/** Domain controller object for HtmlFilter instance. Can work in conjunction with WDK
 * HtmlFilterEditor component.
 */
class HtmlFilterDco(@OmmField(delegatedRepresentation = true) val filter: HtmlFilter,
                    private val modifyWrapperFunc: (suspend (req: HttpRequestContext,
                                                             block: () -> Unit) -> Unit)? = null) {

    /**
     * Get list of selectors string representation for the specified filter node. Selectors are all
     * leaf branches reached from the specified node.
     */
    @Endpoint()
    fun BeginEdit(nodeId: Int): List<String>
    {
        val node = GetNode(nodeId)
        return filter.GetSelectors(node)
    }

    @Endpoint
    suspend fun Clear(req: HttpRequestContext)
    {
        Modify(req) {
            filter.Clear()
        }
    }

    @Endpoint
    suspend fun Modify(req: HttpRequestContext, nodeId: Int, selectors: String)
    {
        val node = GetNode(nodeId)
        val newFilter = HtmlFilterParser.Expression.GetFilter(HtmlFilterParser().Parse(selectors))
        Modify(req) {
            filter.DeleteNode(node, true)
            filter.MergeWith(newFilter)
        }
    }

    @Endpoint
    suspend fun Add(req: HttpRequestContext, selectors: String)
    {
        val newFilter = HtmlFilterParser.Expression.GetFilter(HtmlFilterParser().Parse(selectors))
        Modify(req) {
            filter.MergeWith(newFilter)
        }
    }

    @Endpoint
    suspend fun Delete(req: HttpRequestContext, nodeId: Int, deleteBranch: Boolean)
    {
        val node = GetNode(nodeId)
        Modify(req) {
            filter.DeleteNode(node, deleteBranch)
        }
    }

    @Endpoint(unpackArguments = false)
    suspend fun AddTransform(req: HttpRequestContext, name: String)
    {
        Modify(req) {
            if (filter.transforms.putIfAbsent(name, HtmlFilter.TagTransform("^.*$", "$0")) != null) {
                throw HttpError(409, "The specified tag transform already exists: $name")
            }
        }
    }

    @Endpoint
    suspend fun DeleteTransform(req: HttpRequestContext, tagName: String)
    {
        Modify(req) {
            if (filter.transforms.remove(tagName) == null) {
                throw HttpError(404, "The specified tag transform not found: $tagName")
            }
        }
    }

    @Endpoint
    suspend fun ModifyTransform(req: HttpRequestContext, tagName: String, matchPattern: String,
                                replacePattern: String)
    {
        Modify(req) {
            val f = HtmlFilter()
            f.transforms[tagName] = HtmlFilter.TagTransform(matchPattern, replacePattern)
            f.MakeContext()
            filter.MergeWith(f)
        }
    }

    // /////////////////////////////////////////////////////////////////////////////////////////////

    private fun GetNode(id: Int): HtmlFilter.Node
    {
        return filter.GetNode(id) ?: throw IllegalArgumentException("Node not found: #$id")
    }

    private suspend fun Modify(req: HttpRequestContext, op: () -> Unit)
    {
        if (modifyWrapperFunc != null) {
            modifyWrapperFunc.invoke(req, op)
        } else {
            op()
        }
    }
}
