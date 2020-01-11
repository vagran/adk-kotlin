package io.github.vagran.adk.async.db.mongo

import io.github.vagran.adk.async.Deferred
import io.github.vagran.adk.async.TaskThrottler
import io.github.vagran.adk.async.observable.One
import io.github.vagran.adk.log.*
import io.github.vagran.adk.log.LogConfiguration.Companion.DEFAULT_PATTERN
import io.github.vagran.adk.log.slf4j.api.Slf4jLogManager
import io.github.vagran.adk.omm.OmmField
import io.github.vagran.adk.omm.OmmOption
import com.mongodb.MongoClientSettings
import com.mongodb.ServerAddress
import com.mongodb.async.client.MongoClient
import com.mongodb.async.client.MongoClients
import com.mongodb.async.client.MongoDatabase
import org.bson.*
import org.bson.types.ObjectId
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import java.util.concurrent.ConcurrentSkipListSet
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertNull

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
private class MongodbTest {

    private val numCores = Runtime.getRuntime().availableProcessors()
    private lateinit var client: MongoClient
    private lateinit var database: MongoDatabase
    private lateinit var log: Logger

    @BeforeAll
    fun Setup()
    {
        val appender = LogConfiguration.Appender("console").apply {
            type = LogConfiguration.Appender.Type.CONSOLE
            pattern = DEFAULT_PATTERN
            level = LogLevel.TRACE
            consoleParams = LogConfiguration.Appender.ConsoleParams().apply {
                target = LogConfiguration.Appender.ConsoleParams.Target.STDOUT
            }
        }
        val appenders = listOf(appender)

        val rootLogger = LogConfiguration.Logger(LoggerName.ROOT).apply {
            this.level = LogLevel.TRACE
            this.appenders = appenders
        }

        val mongoProtocolLogger = LogConfiguration.Logger("org.mongodb.driver").apply {
            this.level = LogLevel.INFO
            this.appenders = appenders
        }

        val logConfig = LogConfiguration(LogConfiguration.Settings(), appenders,
                                         listOf(rootLogger, mongoProtocolLogger))
        Slf4jLogManager.logManager = LogManager().apply { Initialize(logConfig) }
        log = Slf4jLogManager.logManager.GetLogger("Test")

        client = MongoClients.create(
            MongoClientSettings.builder()
                .applyToConnectionPoolSettings {
                    it.maxSize(numCores * 2)
                    it.maxWaitQueueSize(50000)
                }
                .applyToClusterSettings {
                    it.hosts(arrayOf(ServerAddress("localhost")).asList())
                }
                .build()
        )

        database = client.getDatabase("local")
        for (name in arrayOf("mapped", "test")) {
            MongoCall(database.getCollection(name)::drop).WaitComplete()
        }
    }

    @AfterAll
    fun Teardown()
    {
        client.close()
        Slf4jLogManager.Shutdown()
    }

    @Test
    fun Basic0()
    {
        val collection = database.getCollection("test")

        val numDocs = 50_000

        assertEquals(0, MongoCall(collection::estimatedDocumentCount).WaitComplete().Get().toInt())

        log.Info("Inserting...")
        val docIdx = AtomicInteger(1)
        TaskThrottler(numCores) {
            val i = docIdx.getAndIncrement()
            if (i > numDocs) {
                return@TaskThrottler null
            }

            return@TaskThrottler MongoCall(
                collection::insertOne,
                MongoDoc {
                    "index" to i
                    "info" to {
                        "x" to i
                        "y" to i * 2
                    }
                })
        }.Run().WaitComplete()
        log.Info("Insertion done")

        run {
            val res = MongoCall(collection.find(MongoDoc("index", 42))::first)
                .WaitComplete().Get()
            assertEquals(42, res.get("info", Document::class.java).getInteger("x"))
        }

        assertEquals(numDocs, MongoCall(collection::estimatedDocumentCount)
            .WaitComplete().Get().toInt())

        log.Info("Verifying...")
        val docs = MongoObservable(collection.find())
        docs.batchSize = 1000
        val done = Deferred.Create<Void?>()
        val verified = ConcurrentSkipListSet<Int>()
        docs.SubscribeVoid { doc, error ->
            if (error != null) {
                System.err.format("Collection iteration error:%n")
                error.printStackTrace(System.err)
                throw Error("Unexpected error: $error")
            }
            if (!doc.isSet) {
                done.SetResult(null)
                return@SubscribeVoid
            }
            val x = doc.value.get("info", Document::class.java).getInteger("x")
            assertFalse(verified.contains(x))
            verified.add(x)
        }
        done.WaitComplete().Get()
        assertEquals(numDocs, verified.size)
        log.Info("Verification done")
    }

    interface I

    inner class A: I {
        @MongoId var id: ObjectId? = null
        val i = 42
        @OmmField var j = 43
        var pia: IntArray = arrayOf(1, 2, 3).toIntArray()
        @OmmField var ia: Array<Int?> = arrayOf(44, 45, null, 46)
        val s = "abc"
        @OmmField val col = ArrayList<Int?>()
        @OmmField val map = HashMap<String, Int?>()

        init {
            col.add(47)
            col.add(null)
            col.add(48)
            map["a"] = 50
            map["b"] = null
            map["c"] = 51
        }
    }

    @Test
    fun BasicMapping()
    {
        val mapper = MongoMapper()
        val doc = mapper.Encode(A())
        val json = doc.toJson()
        println(json)
        assertEquals("""
            { "col" : [47, null, 48], "s" : "abc", "ia" : [44, 45, null, 46], "i" : 42,
             "pia" : [1, 2, 3], "j" : 43, "map" : { "a" : 50, "b" : null, "c" : 51 } }
        """.trimIndent().replace("\n", ""), json)
    }

    class Valid {
        @MongoId
        var id: ObjectId? = null

        @OmmField
        var i: Int = 0
    }

    /* No mapped fields. */
    class Invalid1

    /* Invalid annotation. */
    class Invalid2 {
        @OmmField
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
        @OmmField
        var i: Int = 0

        @OmmField(name = "i")
        var j: Int = 0
    }

    /* Non-public mapped field. */
    class Invalid6 {
        @OmmField
        internal var i: Int = 0
    }

    /* Static mapped field. */
    object Invalid7 {
        @JvmStatic
        @OmmField
        var i: Int = 0
    }

    open class InvalidBase {
        @MongoId
        var id: ObjectId? = null

        @OmmField
        var i: Int = 0
    }

    /* Multiple MongoId, one in base class. */
    class Invalid8 : InvalidBase() {
        @MongoId
        var _id: ObjectId? = null
    }

    /* Duplicated field name in DB, one in base class. */
    class Invalid9 : InvalidBase() {
        @OmmField(name = "i")
        var j: Int = 0
    }

    /* No default constructor. */
    class Invalid10(@OmmField
                    var i: Int)

    /* Non-public default constructor. */
    class Invalid11 internal constructor(@OmmField
                                         var i: Int)

    @Test
    fun TestMapper_InvalidClasses()
    {
        MongoMapper().GetCodec<Valid>()
        assertThrows<IllegalArgumentException> { MongoMapper().GetCodec<Invalid1>() }
//        assertThrows<Error> { MongoMapper.ForClasses(Invalid2::class) }
        assertThrows<IllegalArgumentException> { MongoMapper().GetCodec<Invalid4>() }
        assertThrows<IllegalArgumentException> { MongoMapper().GetCodec<Invalid5>() }
        assertThrows<IllegalArgumentException> { MongoMapper().GetCodec<Invalid6>() }
        assertThrows<IllegalArgumentException> { MongoMapper().GetCodec<Invalid7>() }
        assertThrows<IllegalArgumentException> { MongoMapper().GetCodec<Invalid8>() }
        assertThrows<IllegalArgumentException> { MongoMapper().GetCodec<Invalid9>() }
//        assertThrows<IllegalArgumentException> { MongoMapper().GetCodec<Invalid10>() }
//        assertThrows<IllegalArgumentException> { MongoMapper().GetCodec<Invalid11>() }
    }

    open class ItemBase {

        @OmmField
        var id: Int = 0

        @OmmField
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

        val registry = MongoMapper(annotatedOnlyFields = true,
                                   allowUnmatchedFields = true)
        val collection = database.getCollection("mapped", item.javaClass)
            .withCodecRegistry(registry)

        MongoCall(collection::insertOne, item).WaitComplete().Get()

        return MongoObservable(collection.find(MongoDoc("id", id))).One().WaitComplete().Get()
    }

    class SubItem {

        @OmmField
        var i: Int = 0

        @Suppress("unused")
        constructor()

        constructor(i: Int)
        {
            this.i = i
        }
    }

    enum class TestEnum {
        A,
        B,
        C
    }

    class ItemBasic: ItemBase() {

        var derivedCtor: Int = 43

        @MongoId
        var mongoId: ObjectId? = null

        @OmmField
        var i: Int = 0

        @OmmField
        var b: Byte = 0

        @OmmField
        var sh: Short = 0

        @OmmField
        var f: Float = 0f

        @OmmField
        var s: String? = null

        @OmmField
        var nullBoxed: Int? = null

        @OmmField
        var nullS: String? = null

        @OmmField
        var nullArray: IntArray? = null

        @OmmField
        var e1: TestEnum = TestEnum.A

        @OmmField(enumByName = OmmOption.YES)
        var e2: TestEnum = TestEnum.A

        @OmmField(serializeNull = OmmOption.YES)
        var e3: TestEnum? = TestEnum.A
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
        item.e1 = TestEnum.B
        item.e2 = TestEnum.C
        item.e3 = null
        item = TestMapping(item, "Basic")
        assertNotNull(item.mongoId)
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
        assertEquals(TestEnum.B, item.e1)
        assertEquals(TestEnum.C, item.e2)
        assertNull(item.e3)
    }

    class ItemBoxed: ItemBase() {
        @OmmField
        var i: Int? = null

        @OmmField
        var nullInt: Int? = null

        @OmmField
        var b: Byte? = null

        @OmmField
        var s: Short? = null

        @OmmField
        var c: Char? = null

        @OmmField
        var f: Float? = null
    }

    @Test
    fun Boxed()
    {
        var item = ItemBoxed()
        item.i = 42
        item.b = 43
        item.c = 'A'
        item.f = 44.5f
        item.s = 45
        item = TestMapping(item, "Boxed")
        assertEquals(42, item.i!!)
        assertNull(item.nullInt)
        assertEquals(43, item.b!!)
        assertEquals('A', item.c!!)
        assertEquals(44.5f, item.f!!, 0.001f)
        assertEquals(45, item.s!!)
    }

    class ItemPrimitiveArray: ItemBase() {
        @OmmField
        var i: IntArray? = null
        @OmmField
        var f: FloatArray? = null
        @OmmField
        var b: ByteArray? = null
    }

    @Test
    fun PrimitiveArray()
    {
        var item = ItemPrimitiveArray()
        item.i = intArrayOf(1, 2, 3)
        item.f = floatArrayOf(4f, 5f, 6f)
        item.b = byteArrayOf(7, 8, 9)
        item = TestMapping(item, "Primitive array")
        assertEquals(1, item.i!![0])
        assertEquals(2, item.i!![1])
        assertEquals(3, item.i!![2])
        assertEquals(4f, item.f!![0], 0.01f)
        assertEquals(5f, item.f!![1], 0.01f)
        assertEquals(6f, item.f!![2], 0.01f)
        assertEquals(7, item.b!![0])
        assertEquals(8, item.b!![1])
        assertEquals(9, item.b!![2])
    }

    class ItemRefArray: ItemBase() {
        @OmmField
        var i: Array<Int?>? = null
        @OmmField
        var s: Array<String?>? = null
    }

    @Test
    fun RefArray()
    {
        var item = ItemRefArray()
        item.i = arrayOf(1, 2, 3, null)
        item.s = arrayOf("4", "5", "6", null)
        item = TestMapping(item, "References array")
        assertEquals(4, item.i!!.size)
        assertEquals(1, item.i!![0])
        assertEquals(2, item.i!![1])
        assertEquals(3, item.i!![2])
        assertNull(item.i!![3])
        assertEquals(4, item.s!!.size)
        assertEquals("4", item.s!![0])
        assertEquals("5", item.s!![1])
        assertEquals("6", item.s!![2])
        assertNull(item.s!![3])
    }

    class ItemDocument: ItemBase {

        @OmmField
        var doc: Document? = null

        @OmmField
        var i: Int = 0

        constructor()

        internal constructor(i: Int) {
            this.i = i
        }
    }

    @Test
    fun Document()
    {
        var item = ItemDocument()
        item.i = 42
        item.doc = MongoDoc {
            V("a", "b")
            V("subitem", ItemDocument(43))
        }
        item = TestMapping(item, "Document")
        assertEquals(42, item.i)
        assertNotNull(item.doc)
        assertEquals(43, item.doc!!.get("subitem", Document::class.java).getInteger("i").toInt())
        assertEquals("b", item.doc!!.getString("a"))
    }

    class ItemMappedArray: ItemBase() {
        @OmmField
        var subItems: Array<SubItem?>? = null

        @OmmField
        var nullSubItems: Array<SubItem?>? = null
    }

    @Test
    fun MappedArray()
    {
        var item = ItemMappedArray()
        item.subItems = arrayOf(SubItem(42), SubItem(43), null)
        item = TestMapping(item, "Mapped array")
        assertNull(item.nullSubItems)
        assertEquals(3, item.subItems!!.size)
        assertEquals(42, item.subItems!![0]!!.i)
        assertEquals(43, item.subItems!![1]!!.i)
        assertNull(item.subItems!![2])
    }

    class ItemInnerClass: ItemBase() {

        @OmmField
        var inner: InnerItem? = null

        inner class InnerItem {

            @OmmField
            var i: Int = 0

            @OmmField
            var inner: Inner2Item? = null

            inner class Inner2Item(/*XXX until bug fixed in Kotlin: @OmmField var l: Int = 45*/) {
                @OmmField
                var j: Int = 0

                var k: Int = 44

                fun GetOuter(): InnerItem
                {
                    return this@InnerItem
                }
            }

            fun GetOuter(): ItemInnerClass
            {
                return this@ItemInnerClass
            }
        }
    }

//    @Test
//    fun InnerClass()
//    {
//        var item = ItemInnerClass()
//        item.inner = item.InnerItem()
//        item.inner!!.i = 42
//        item.inner!!.inner = item.inner!!.Inner2Item()
//        item.inner!!.inner!!.j = 43
//        item = TestMapping(item, "Inner class")
//        assertNotNull(item.inner)
//        assertEquals(42, item.inner!!.i)
//        assertNotNull(item.inner!!.inner)
//        assertEquals(43, item.inner!!.inner!!.j)
//        assertEquals(44, item.inner!!.inner!!.k)
//        //XXX assertEquals(45, item.inner!!.inner!!.l)
//        assertEquals(item, item.inner!!.GetOuter())
//        assertEquals(item.inner, item.inner!!.inner!!.GetOuter())
//    }

    class ItemInnerClassArray: ItemBase() {

        @OmmField
        var inner: Array<InnerItem?>? = null

        inner class InnerItem {

            @OmmField
            var i: Int = 0

            @Suppress("unused")
            constructor()

            internal constructor(i: Int)
            {
                this.i = i
            }

            fun GetOuter(): ItemInnerClassArray
            {
                return this@ItemInnerClassArray
            }
        }
    }

//    @Test
//    fun InnerClassArray()
//    {
//        var item = ItemInnerClassArray()
//        item.inner = arrayOf(item.InnerItem(42), null, item.InnerItem(43))
//        item = TestMapping(item, "Inner class array")
//        assertNotNull(item.inner)
//        assertEquals(3, item.inner!!.size)
//        assertEquals(42, item.inner!![0]!!.i)
//        assertEquals(item, item.inner!![0]!!.GetOuter())
//        assertNull(item.inner!![1])
//        assertEquals(43, item.inner!![2]!!.i)
//        assertEquals(item, item.inner!![2]!!.GetOuter())
//    }

    class ItemCollection: ItemBase() {

        @OmmField
        var i: ArrayList<Int?>? = null

        @OmmField(name = "jj")
        lateinit var j: MutableList<Int?>
    }


    @Test
    fun CollectionTest()
    {
        var item = ItemCollection()
        item.i = ArrayList()
        item.i!!.add(1)
        item.i!!.add(null)
        item.i!!.add(2)
        item.j = ArrayList()
        item.j.add(3)
        item.j.add(null)
        item.j.add(4)
        item = TestMapping(item, "Collection")
        assertNotNull(item.i)
        assertEquals(3, item.i!!.size)
        assertEquals(1, item.i!![0])
        assertNull(item.i!![1])
        assertEquals(2, item.i!![2])
        assertNotNull(item.j)
        assertEquals(3, item.j.size)
        assertEquals(3, item.j[0]!!)
        assertNull(item.j[1])
        assertEquals(4, item.j[2]!!)
    }

    class ItemInnerClassCollection: ItemBase() {

        @OmmField
        var inner: ArrayList<InnerItem?>? = null

        inner class InnerItem {

            @OmmField
            var i: Int = 0

            @Suppress("unused")
            constructor()

            internal constructor(i: Int) {
                this.i = i
            }

            fun GetOuter(): ItemInnerClassCollection
            {
                return this@ItemInnerClassCollection
            }
        }
    }

//    @Test
//    fun InnerClassCollection()
//    {
//        var item = ItemInnerClassCollection()
//        item.inner = ArrayList()
//        item.inner!!.add(item.InnerItem(42))
//        item.inner!!.add(null)
//        item.inner!!.add(item.InnerItem(43))
//        item = TestMapping(item, "Inner class collection")
//        assertNotNull(item.inner)
//        assertEquals(3, item.inner!!.size)
//        assertEquals(42, item.inner!![0]!!.i)
//        assertEquals(item, item.inner!![0]!!.GetOuter())
//        assertNull(item.inner!![1])
//        assertEquals(43, item.inner!![2]!!.i)
//        assertEquals(item, item.inner!![2]!!.GetOuter())
//
//        /* Test replacing. */
//        item.inner = ArrayList()
//        item.inner!!.add(item.InnerItem(52))
//        item.inner!!.add(null)
//        item.inner!!.add(item.InnerItem(53))
//
//        val collection = MongoMapper.GetCollection(database, "mapped",
//                                                   ItemInnerClassCollection::class)
//        val updateResult = MongoCall(collection::replaceOne, MongoDoc("id", item.id), item)
//            .WaitComplete().Get()
//        assertEquals(1, updateResult.modifiedCount)
//
//        item = MongoObservable(collection.find(MongoDoc("id", item.id))).One().WaitComplete().Get()
//        assertNotNull(item.inner)
//        assertEquals(3, item.inner!!.size)
//        assertEquals(52, item.inner!![0]!!.i)
//        assertEquals(item, item.inner!![0]!!.GetOuter())
//        assertNull(item.inner!![1])
//        assertEquals(53, item.inner!![2]!!.i)
//        assertEquals(item, item.inner!![2]!!.GetOuter())
//    }

    class ItemBsonTypes: ItemBase() {
        @OmmField
        var i: BsonInt32? = null

        @OmmField
        var nullInt: BsonInt32? = null

        @OmmField
        var b: BsonBoolean? = null

        @OmmField
        var s: BsonString? = null

        @OmmField
        var f: BsonDouble? = null
    }

    @Test
    fun BsonTypes() {
        var item = ItemBsonTypes()
        item.i = BsonInt32(42)
        item.b = BsonBoolean(true)
        item.s = BsonString("test")
        item.f = BsonDouble(43.5)
        item = TestMapping(item, "BSON types")
        assertEquals(42, item.i!!.value)
        assertNull(item.nullInt)
        assertTrue(item.b!!.value)
        assertEquals("test", item.s!!.value)
        assertEquals(43.5, item.f!!.value)
    }

    class GetterSetterItem: ItemBase() {
        @OmmField
        var i: Int
            get() = _i * 2
            set(value)
            {
                _i = value * 3
            }

        val j: Int
            get() =_i

        private var _i = 0
    }

    @Test
    fun GetterSetter()
    {
        var item = GetterSetterItem()
        item.i = 5
        assertEquals(15, item.j)
        assertEquals(30, item.i)
        item = TestMapping(item, "Getter/setter")
        assertEquals(90, item.j)
        assertEquals(180, item.i)
    }

    abstract class GetterSetterBaseItem: ItemBase() {
        @OmmField
        abstract var i: Int
    }

    class GetterSetterDerivedItem: GetterSetterBaseItem() {

        override var i: Int
            get() = _i * 2
            set(value)
            {
                _i = value * 3
            }

        val j: Int
            get() =_i

        private var _i = 0
    }

    @Test
    fun AbstractGetterSetter()
    {
        var item = GetterSetterDerivedItem()
        item.i = 5
        assertEquals(15, item.j)
        assertEquals(30, item.i)
        item = TestMapping(item, "Abstract getter/setter")
        assertEquals(90, item.j)
        assertEquals(180, item.i)
    }

    data class DataItem(@OmmField var i: Int = 0,
                        @OmmField var s: String = "",
                        @OmmField var j: Int = 42):
        ItemBase()

    @Test
    fun DataClass()
    {
        val item = DataItem(i = 10, s = "Test")
        val item2 = TestMapping(item, "Data class")
        assertTrue(item == item2)
        assertEquals(10, item2.i)
        assertEquals("Test", item2.s)
        assertEquals(42, item2.j)
    }

    class AnyItemClass: ItemBase() {
        @OmmField
        lateinit var map: Map<String, *>
        @OmmField
        lateinit var list: List<*>
    }

    @Test
    fun AnyItem()
    {
        var item = AnyItemClass()
        item.map = HashMap<String, Any?>().also {
            it["a"] = 42
            it["b"] = null
            it["c"] = "abc"
            it["d"] = true
        }
        item.list = ArrayList<Any?>().also {
            it.add(42)
            it.add(null)
            it.add("abc")
            it.add(true)
        }
        item = TestMapping(item, "Any class")

        assertEquals(4, item.map.size)
        assertEquals(42, item.map["a"])
        assertNull(item.map["b"])
        assertEquals("abc", item.map["c"])
        assertEquals(true, item.map["d"])

        assertEquals(4, item.list.size)
        assertEquals(42, item.list[0])
        assertNull(item.list[1])
        assertEquals("abc", item.list[2])
        assertEquals(true, item.list[3])
    }
}
