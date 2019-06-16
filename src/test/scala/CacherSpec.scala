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

  trait ctx {
    var calls = List.empty[String]
    val runtime: DefaultRuntime = new DefaultRuntime {}
    val cacher: Cacher[Clock, Throwable, String, String] = Cacher.build((key: String) => ZIO {
      calls = calls :+ key
      key
    }, Duration(1, TimeUnit.SECONDS)).runn


    implicit class ZIORUN[E, A](zio: ZIO[Clock, E, A]) {
      def runn = runtime.unsafeRun[E, A](zio)
    }

  }

}
