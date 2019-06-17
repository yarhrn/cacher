# cacher
*This is project is in development phase.*

This project aims add an ability to cache response from third-party service in order to not hit rate limit.

Long story short:
1. if value is absent in cache then start resolving and return promise.await
2. if value is resolving then return promise.await
3. if value is in cache and is not expired return value
4. if value is in cache and is expired start resolving new value (goto 1)

Main abstraction:
```scala
trait Cacher[R, E, K, V] {

  def get(key: K): ZIO[R, E, V]

}
```
Also there is tagless version with cats-effect
```scala
trait CacherF[F[_], K, V] {

  def get(key: K): F[V]

}
```