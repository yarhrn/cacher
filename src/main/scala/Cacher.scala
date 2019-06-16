import java.util.concurrent.TimeUnit

import zio._
import zio.clock.Clock
import zio.duration.Duration

trait Cacher[R, E, K, V] {

  def get(key: K): ZIO[R, E, V]

}


object Cacher {

  sealed trait CachedValue[T, +E]

  case class Resolved[T](value: T, expiresAt: Long) extends CachedValue[T, Nothing]

  case class Resolving[T, E](value: Promise[E, T]) extends CachedValue[T, E]

  def build[R <: Clock, E, K, V](resolver: K => ZIO[R, E, V], expiration: Duration): UIO[Cacher[R, E, K, V]] = {

    for {
      cache <- Ref.make(Map.empty[K, CachedValue[V, E]])
    } yield {
      new Cacher[R, E, K, V] {
        override def get(key: K) = {
          for {
            currentTime <- UIO(System.currentTimeMillis())
            maybeValue <- optimisticGet(key, currentTime)
            value <- maybeValue.fold(pessimisticGet(key, currentTime))(ZIO.succeed)
          } yield value
        }


        def optimisticGet(key: K, currentTime: Long): ZIO[R, E, Option[V]] = {
          for {
            map <- cache.get
            value <- map.get(key) match {
              case Some(Resolved(v, expiresAt)) if expiresAt > currentTime => ZIO.succeed(Option(v))
              case Some(Resolving(v)) => v.await.map(Option(_))
              case _ => ZIO.succeed(None)
            }
          } yield value
        }

        def pessimisticGet(key: K, currentTime: Long) = {
          for {
            promise <- Promise.make[E, V]
            intent <- cache.modify { map =>
              map.get(key) match {
                case Some(Resolved(_, expiresAt)) if expiresAt < currentTime => (Left(promise), map + (key -> Resolving(promise)))
                case Some(cachedValue) => (Right(cachedValue), map)
                case None => (Left(promise), map + (key -> Resolving(promise)))
              }
            }
            value <- intent match {
              case Left(v) => resolve(key, v)
              case Right(Resolved(v, _)) => ZIO.succeed(v)
              case Right(Resolving(v)) => v.await
            }
          } yield value
        }

        val schedule = ZSchedule.exponential(Duration(1, TimeUnit.SECONDS)) && ZSchedule.recurs(4)

        def resolve(key: K, promise: Promise[E, V]) = {
          val enrichJob: ZIO[R, E, V] = (for {
            value <- resolver(key)
            time <- ZIO.accessM[R](_.clock.currentTime(TimeUnit.MILLISECONDS))
            _ <- cache.modify(map => ((), map + (key -> Resolved(value, time + expiration.toMillis))))
          } yield value).retry(ZSchedule.exponential(Duration(1, TimeUnit.SECONDS)) && ZSchedule.recurs(4))

          enrichJob.foldM(promise.fail, promise.succeed).fork *> promise.await
        }
      }
    }


  }

}


