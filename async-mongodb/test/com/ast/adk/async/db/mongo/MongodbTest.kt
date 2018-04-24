package com.ast.adk.async.db.mongo

import com.ast.adk.async.Task
import com.ast.adk.utils.Log
import com.mongodb.ServerAddress
import com.mongodb.async.client.MongoClient
import com.mongodb.async.client.MongoClientSettings
import com.mongodb.async.client.MongoClients
import com.mongodb.async.client.MongoDatabase
import com.mongodb.connection.ClusterSettings
import com.mongodb.connection.ConnectionPoolSettings
import org.apache.logging.log4j.Logger
import org.bson.Document
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
private class ContextTest {

    private lateinit var client: MongoClient
    private lateinit var database: MongoDatabase
    private lateinit var log: Logger

    @BeforeAll
    fun Setup()
    {
        Log.InitTestLogging()
        log = Log.GetLogger("MongodbTest")

        client = MongoClients.create(
            MongoClientSettings.builder()
                .connectionPoolSettings(
                    ConnectionPoolSettings.builder().maxSize(8).maxWaitQueueSize(50000).build())
                .clusterSettings(
                    ClusterSettings.builder()
                        .hosts(arrayOf(ServerAddress("localhost")).asList()).build())
                .build())

        database = client.getDatabase("local")
        for (name in arrayOf("mapped", "test")) {
            val dropped = MongoCallback<Void>()
            database.getCollection(name).drop(dropped)
            dropped.result.WaitComplete()
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

        val numDocs = 50000

        val task = Task.CreateDef {
            for (i in 0..numDocs) {
                val inserted = MongoCallback<Void?>()
                collection.insertOne(
                    Document("index", i)
                        .append("name", "test")
                        .append("info", Document("x", i).append("y", i * 2)),
                    inserted)
                inserted.Await()
            }
        }.also { it.Invoke() }.result.WaitComplete()

    }
}
