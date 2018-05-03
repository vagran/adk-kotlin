package com.ast.adk.async.db.mongo

import com.ast.adk.async.Deferred
import com.ast.adk.async.TaskThrottler
import com.ast.adk.async.observable.One
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
import org.bson.types.ObjectId
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import java.util.*
import java.util.concurrent.atomic.AtomicInteger

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
private class MongodbTest {

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
    fun Basic0()
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
                throw Error("Unexpected error: $error")
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

    interface I

    inner class A: I {
        @MongoId var id: ObjectId? = null
        val i = 42
        @MongoField var j = 43
        @MongoField var ia: Array<Int?> = arrayOf(44, 45, null, 46)
        @MongoField val col = ArrayList<Int>()

        init {
            col.add(47)
            col.add(48)
            col.add(49)
        }
    }

    @Test
    fun BasicMapping()
    {
        val codecs = MongoMapper.ForClasses(A::class)
        val doc = MongoMapper.EncodeObject(codecs, A())
        log.debug(doc.toJson())
    }

    //XXX required tests
    // duplicated MongoId
    // duplicated field name
    // Any class mapped
    // inner classes
    // data classes?
    // class hierarchy
    // collections
    // arrays
    // collections and arrays of inner classes
    // BSON types
    // nullable/non-nullable types
    // getter/setter, virtual


    class Valid {
        @MongoId
        var id: ObjectId? = null

        @MongoField
        var i: Int = 0
    }

    /* No mapped fields. */
    class Invalid1

    /* Invalid annotation. */
    class Invalid2 {
        @MongoField
        @MongoId
        var i: Int = 0
    }

    /* Invalid type for MongoId, should be ObjectId. */
    class Invalid3 {
        @MongoId
        var i: Int = 0
    }

    /* Multiple MongoId */
    class Invalid4 {
        @MongoId
        var i: ObjectId? = null

        @MongoId
        var j: ObjectId? = null
    }

    /* Duplicated field name in DB. */
    class Invalid5 {
        @MongoField
        var i: Int = 0

        @MongoField(name = "i")
        var j: Int = 0
    }

    /* Non-public mapped field. */
    class Invalid6 {
        @MongoField
        internal var i: Int = 0
    }

    /* Static mapped field. */
    object Invalid7 {
        @MongoField
        var i: Int = 0
    }

    open class InvalidBase {
        @MongoId
        var id: ObjectId? = null

        @MongoField
        var i: Int = 0
    }

    /* Multiple MongoId, on in base class. */
    class Invalid8 : InvalidBase() {
        @MongoId
        var _id: ObjectId? = null
    }

    /* Duplicated field name in DB, one in base class. */
    class Invalid9 : InvalidBase() {
        @MongoField(name = "i")
        var j: Int = 0
    }

    /* No default constructor. */
    class Invalid10(@field:MongoField
                    var i: Int)

    /* Non-public default constructor. */
    class Invalid11 internal constructor(@field:MongoField
                                         var i: Int)

    @Test
    fun TestMapper_InvalidClasses()
    {
        MongoMapper.ForClasses(Valid::class)
        assertThrows<Error> { MongoMapper.ForClasses(Invalid1::class) }
        assertThrows<Error> { MongoMapper.ForClasses(Invalid2::class) }
        assertThrows<Error> { MongoMapper.ForClasses(Invalid3::class) }
        assertThrows<Error> { MongoMapper.ForClasses(Invalid4::class) }
        assertThrows<Error> { MongoMapper.ForClasses(Invalid5::class) }
        assertThrows<Error> { MongoMapper.ForClasses(Invalid6::class) }
        assertThrows<Error> { MongoMapper.ForClasses(Invalid7::class) }
        assertThrows<Error> { MongoMapper.ForClasses(Invalid8::class) }
        assertThrows<Error> { MongoMapper.ForClasses(Invalid9::class) }
        assertThrows<Error> { MongoMapper.ForClasses(Invalid10::class) }
        assertThrows<Error> { MongoMapper.ForClasses(Invalid11::class) }
    }

    open class ItemBase {

        @MongoField
        var id: Int = 0

        @MongoField
        var testName: String? = null

        var baseCtor: Int = 42

        companion object {
            internal val curId = AtomicInteger()
        }
    }

    /** Inserts the item, then reads it back and returns the decoded one.  */
    fun <T: ItemBase> TestMapping(item: T, testName: String): T
    {
        val id = ItemBase.curId.incrementAndGet()
        item.id = id
        item.testName = testName

        val registry = MongoMapper.ForClasses(item::class)
        val collection = database.getCollection("mapped", item.javaClass)
            .withCodecRegistry(registry)

        MongoCall(collection::insertOne, item).WaitComplete().Get()

        return MongoObservable(collection.find(Document("id", id))).One().WaitComplete().Get()
    }

    class SubItem {

        @MongoField
        var i: Int = 0

        constructor() {}

        constructor(i: Int)
        {
            this.i = i
        }
    }

    class ItemBasic: ItemBase() {

        var derivedCtor: Int = 43

        @MongoId
        var _id: ObjectId? = null

        @MongoField
        var i: Int = 0

        @MongoField
        var b: Byte = 0

        @MongoField
        var sh: Short = 0

        @MongoField
        var f: Float = 0f

        @MongoField
        var s: String? = null

        @MongoField
        var nullBoxed: Int? = null

        @MongoField
        var nullS: String? = null

        @MongoField
        var nullArray: IntArray? = null
    }

    @Test
    fun Basic()
    {
        var item = ItemBasic()
        item.i = 42
        item.b = 43
        item.sh = 44
        item.f = 45.5f
        item.s = "test string"
        item = TestMapping(item, "Basic")
        assertNotNull(item._id)
        assertEquals(42, item.i.toLong())
        assertEquals(43, item.b.toLong())
        assertEquals(44, item.sh.toLong())
        assertEquals(45.5f, item.f, 0.001f)
        assertEquals("test string", item.s)
        assertEquals(42, item.baseCtor.toLong())
        assertEquals(43, item.derivedCtor.toLong())
        assertNull(item.nullS)
        assertNull(item.nullBoxed)
        assertNull(item.nullArray)
    }
}
