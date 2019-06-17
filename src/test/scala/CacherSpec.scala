import java.util.UUID
import java.util.concurrent.TimeUnit

import org.scalatest.{FlatSpec, FunSuite}
import zio.clock.Clock
import zio.duration.Duration
import zio.{DefaultRuntime, ZIO}

class CacherSpec extends FlatSpec {

  "Cacher" should "not call resolve method for cached value" in new ctx {
    val id = UUID.randomUUID().toString

    assert(cacher.get(id).runn == id)
    assert(calls.length == 1)
    assert(calls.head == id)

    assert(cacher.get(id).runn == id)
    assert(calls.length == 1)
    assert(calls.head == id)
    ZIO.sleep(Duration(1, TimeUnit.SECONDS)).runn

    assert(cacher.get(id).runn == id)
    assert(calls.length == 2)
    assert(calls.head == id)
    assert(calls.tail.head == id)


  }

  it should "retry 5 times(30 seconds)" in new ctx {
    val key = UUID.randomUUID().toString
    val someException = new RuntimeException("some ex")
    override def exception = {
      Some(someException)
    }

    val start = System.currentTimeMillis()

    assertThrows[Throwable] {
      cacher.get(key).runn
    }

    assert((System.currentTimeMillis() - start) / 1000 == 30)
    assert(calls == List.fill(5)(key))


  }

  it should "recover after retries end" in new ctx {
    val key = UUID.randomUUID().toString
    override def exception = {
      if (calls.length <= 5) {

        Some(new RuntimeException("ex"))
      } else {
        None
      }
    }
    assertThrows[Throwable] {
      cacher.get(key).runn
    }

    assert(cacher.get(key).runn == key)
    assert(calls == List.fill(6)(key))
  }

  trait ctx {
    var calls = List.empty[String]
    def exception: Option[RuntimeException] = None
    val runtime: DefaultRuntime = new DefaultRuntime {}
    val cacher: Cacher[Clock, Throwable, String, String] = Cacher.build((key: String) => ZIO {
      calls = calls :+ key
      println(s"Cacher called with key $key, count of calls: ${calls.length} current list of calls $calls")
      val ex = exception
      if (exception.isDefined) {
        throw ex.get
      }
      key
    }, Duration(1, TimeUnit.SECONDS)).runn


    implicit class ZIORUN[E, A](zio: ZIO[Clock, E, A]) {
      def runn = runtime.unsafeRun[E, A](zio)
    }

  }

}
