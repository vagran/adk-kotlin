/*
 * This file is part of ADK project.
 * Copyright (c) 2017-2021 Artyom Lebedev <artyom.lebedev@gmail.com>. All rights reserved.
 * See LICENSE file for full license details.
 */

package io.github.vagran.adk.domain.httpserver

import io.github.vagran.adk.LruCache
import io.github.vagran.adk.async.Context
import io.github.vagran.adk.async.Task
import java.net.HttpCookie

/** Helper for managing local session objects. The objects are identified via HTTP cookie header.
 * They have limited lifetime.
 * @param timeout Session expiration timeout in seconds.
 */
class SessionHelper<TSession>(private val cookieName: String,
                              private val timeout: Int,
                              private val sessionFactory: (id: String) -> TSession,
                              private val closeHandler: ((session: TSession) -> Unit)? = null) {

    /** Get existing or create a new session object. */
    fun GetSession(reqCtx: HttpRequestContext): TSession
    {
        val cookieHdr = reqCtx.request.requestHeaders["Cookie"]?.firstOrNull()
        if (cookieHdr != null) {
            for (cookie in cookieHdr.split(';').flatMap { HttpCookie.parse(it) }) {
                if (cookie.name == cookieName) {
                    val id = cookie.value
                    if (id.length > 16) {
                        break
                    }
                    return sessions.ComputeIfAbsent(id) {
                        SessionWrapper(id, sessionFactory(id))
                    }.session
                }
            }
        }
        val s = sessions.Add { id -> SessionWrapper(id, sessionFactory(id)) }
        reqCtx.request.responseHeaders.add("Set-Cookie",
                                           "$cookieName=${s.id}; Path=/; HttpOnly; Secure")
        return s.session
    }

    /** Schedule periodic cleanup for session objects.
     * @param ctx Scheduling context. Should support scheduling.
     */
    fun ScheduleCleanup(ctx: Context)
    {
        Task.CreateDef {
            while (true) {
                sessions.Cleanup()
                ctx.Delay(sessions.timeout / 2)
            }
        }.Submit(ctx)
    }

    fun Clear()
    {
        sessions.Clear()
    }

    // /////////////////////////////////////////////////////////////////////////////////////////////
    private val sessions = LruCache<SessionWrapper<TSession>>(
        timeout * 1000L,
        if (closeHandler != null) { { closeHandler.invoke(it.session) } } else null
    )

    private class SessionWrapper<TSession>(val id: String, val session: TSession)
}