package com.ast.adk.async.db.mongo

import com.ast.adk.async.Deferred
import com.ast.adk.async.observable.Observable
import com.mongodb.async.AsyncBatchCursor
import com.mongodb.async.client.MongoIterable


/** Make Observable from MongoIterator.  */
class MongoObservable<T> (private val source: MongoIterable<T>,
                          isConnected: Boolean = true):
    Observable<T> by Observable.Create(IterableSource(source), isConnected) {

    fun SetBatchSize(batchSize: Int): MongoObservable<T>
    {
        source.batchSize(batchSize)
        return this
    }

    private class IterableSource<T> (private var iterable: MongoIterable<T>): Observable.Source<T> {
        private var cursor: AsyncBatchCursor<T>? = null
        private var curBatchIterator: Iterator<T>? = null
        private var curRequest: Deferred<Observable.Value<T>>? = null

        override fun Get(): Deferred<Observable.Value<T>>
        {
            synchronized(this) {
                assert(curRequest == null)
                val r = Deferred.Create<Observable.Value<T>>()
                curRequest = r
                if (cursor == null) {
                    iterable.batchCursor({ cursor, error -> this.OnCursorProvided(cursor, error) })
                } else {
                    GetNextItem()
                }
                return r
            }
        }

        private fun OnCursorProvided(cursor: AsyncBatchCursor<T>, error: Throwable?)
        {
            synchronized(this) {
                if (error != null) {
                    curRequest!!.SetError(error)
                    curRequest = null
                    return
                }
                this.cursor = cursor
                cursor.next({ batch, error -> this.OnNextBatch(batch, error) })
            }
        }

        private fun OnNextBatch(batch: List<T>?, error: Throwable?)
        {
            synchronized(this) {
                if (error != null) {
                    curRequest!!.SetError(error)
                    curRequest = null
                    cursor = null
                    return
                }
                if (batch == null) {
                    curRequest!!.SetResult(Observable.Value.None())
                    curRequest = null
                    cursor = null
                    return
                }
                curBatchIterator = batch.iterator()
                GetNextItem()
            }
        }

        private fun GetNextItem()
        {
            if (curBatchIterator!!.hasNext()) {
                val r = curRequest!!
                curRequest = null
                r.SetResult(Observable.Value.Of(curBatchIterator!!.next()))
                return
            }
            curBatchIterator = null
            cursor!!.next({ batch, error -> this.OnNextBatch(batch, error) })
        }
    }

}