package io.github.vagran.adk.async.observable

import io.github.vagran.adk.async.Deferred
import java.util.stream.Stream

/** Observable source based on the provided iterator. */
class IteratorSource<T>(private val it: Iterator<T>): Observable.Source<T> {

    override fun Get(): Deferred<Observable.Value<T>>
    {
        if (!it.hasNext()) {
            return Deferred.ForResult(Observable.Value.None())
        }
        return Deferred.ForResult(Observable.Value.Of(it.next()))
    }
}

fun <T> Observable.Companion.From(it: Iterator<T>): Observable<T>
{
    return Observable.Create(IteratorSource(it))
}

fun <T> Observable.Companion.From(vararg values: T): Observable<T>
{
    return Observable.Create(IteratorSource(values.iterator()))
}

fun <T> Observable.Companion.From(values: Iterable<T>): Observable<T>
{
    return Observable.Create(IteratorSource(values.iterator()))
}

fun <T> Observable.Companion.From(values: Stream<T>): Observable<T>
{
    return Observable.Create(IteratorSource(values.iterator()))
}
