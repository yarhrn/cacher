import java.util.concurrent.TimeUnit

import cats.implicits._
import cats.effect.{Async, Effect}
import cats.effect.implicits._
import zio.{DefaultRuntime, Task, UIO, ZIO}
import zio.clock.Clock
import zio.duration.Duration
import zio.interop.catz._

import scala.concurrent.duration.FiniteDuration
import scala.language.higherKinds

trait CacherF[F[_], K, V] {

  def get(key: K): F[V]

}

object CacherF {

  type InnnerZIO[V] = ZIO[Clock, Throwable, V]
  type ZZIO[V] = ZIO[Any, Nothing, V]
  implicit val runtime = new DefaultRuntime {}


  def build[F[_] : Effect : cats.effect.Timer, K, V](resolver: K => F[V]): F[CacherF[F, K, V]] = {
    val zioResolver = (key: K) => resolver(key).toIO.to[InnnerZIO]

    val cacher: Task[Cacher[Clock, Throwable, K, V]] = Cacher.build(zioResolver, Duration(1, TimeUnit.SECONDS))

    val zioClock = new Clock {
      override val clock = new Clock.Service[Any] {
        override def currentTime(unit: TimeUnit): ZZIO[Long] = cats.effect.Clock[F].realTime(unit).toIO.to[Task].orDie
        override val nanoTime = cats.effect.Clock[F].realTime(TimeUnit.NANOSECONDS).toIO.to[Task].orDie
        override def sleep(duration: Duration) = cats.effect.Timer[F].sleep(FiniteDuration(duration.toMillis, TimeUnit.MILLISECONDS)).toIO.to[Task].orDie
      }
    }

    cacher.toIO.to[F].map(cacher => (key: K) => (cacher.get(key).provide(zioClock): Task[V]).toIO.to[F])
  }


}