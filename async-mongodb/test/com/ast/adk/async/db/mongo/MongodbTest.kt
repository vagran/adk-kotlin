package com.ast.adk.async.db.mongo

import com.ast.adk.async.Deferred
import com.ast.adk.async.TaskThrottler
import com.ast.adk.utils.Log
import com.mongodb.ServerAddress
import com.mongodb.async.client.MongoClient
import com.mongodb.async.client.MongoClientSettings
import com.mongodb.async.client.MongoClients
import com.mongodb.async.client.MongoDatabase
import com.mongodb.connection.ClusterSettings
import com.mongodb.connection.ConnectionPoolSettings
import org.apache.logging.log4j.Level
import org.apache.logging.log4j.Logger
import org.apache.logging.log4j.core.config.LoggerConfig
import org.bson.BsonDocument
import org.bson.BsonInt32
import org.bson.Document
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import java.util.TreeSet

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
private class ContextTest {

    private val numCores = Runtime.getRuntime().availableProcessors()
    private lateinit var client: MongoClient
    private lateinit var database: MongoDatabase
    private lateinit var log: Logger

    @BeforeAll
    fun Setup()
    {
        Log.InitTestLogging(LoggerConfig("org.mongodb.driver.protocol", Level.INFO, true))
        log = Log.GetLogger("MongodbTest")

        client = MongoClients.create(
            MongoClientSettings.builder()
                .connectionPoolSettings(
                    ConnectionPoolSettings.builder()
                        .maxSize(numCores * 2)
                        .maxWaitQueueSize(50000).build())
                .clusterSettings(
                    ClusterSettings.builder()
                        .hosts(arrayOf(ServerAddress("localhost")).asList()).build())
                .build())

        database = client.getDatabase("local")
        for (name in arrayOf("mapped", "test")) {
            MongoCall(database.getCollection(name)::drop).WaitComplete()
        }
    }

    @AfterAll
    fun Teardown()
    {
        client.close()
    }

    @Test
    fun Basic()
    {
        val collection = database.getCollection("test")

        val numDocs = 50_000

        assertEquals(0, MongoCall(collection::count).WaitComplete().Get().toInt())

        val it = (1..numDocs).iterator()
        TaskThrottler(numCores * 2, {
            val i = synchronized(it) {
                if (!it.hasNext()) {
                    return@TaskThrottler null
                }
                it.nextInt()
            }

            return@TaskThrottler MongoCall(
                collection::insertOne,
                Document("index", i)
                    .append("name", "test")
                    .append("info", Document("x", i).append("y", i * 2)))
        }).Run().WaitComplete()

        run {
            val res = MongoCall(collection.find(BsonDocument("index", BsonInt32(42)))::first)
                .WaitComplete().Get()
            assertEquals(42, res.get("info", Document::class.java).getInteger("x"))
        }

        assertEquals(numDocs, MongoCall(collection::count).WaitComplete().Get().toInt())

        val docs = MongoObservable(collection.find())
        docs.SetBatchSize(1000)
        val done = Deferred.Create<Void?>()
        val verified = TreeSet<Int>()
        docs.SubscribeVoid { doc, error ->
            if (error != null) {
                log.error("Collection iteration error: %s", Log.GetStackTrace(error))
                fail("Unexpected error: $error")
            }
            if (!doc.isSet) {
                done.SetResult(null)
                return@SubscribeVoid
            }
            val x = doc.value.get("info", Document::class.java).getInteger("x")
            synchronized(verified) {
                assertFalse(verified.contains(x))
                verified.add(x)
            }
        }
        done.WaitComplete().Get()
        assertEquals(numDocs, verified.size)
    }

    class A {
        val i = 42
    }

    @Test
    fun BasicMapping()
    {
        val codecs = MongoMapper.ForClasses(A::class)
        val doc = MongoMapper.EncodeObject(codecs, A())
        log.debug(doc.toJson())
    }
}
