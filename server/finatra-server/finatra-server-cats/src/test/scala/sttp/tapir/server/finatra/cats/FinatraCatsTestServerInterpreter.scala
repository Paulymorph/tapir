package sttp.tapir.server.finatra.cats

import cats.data.NonEmptyList
import cats.effect.{ContextShift, IO, Resource, Timer}
import sttp.tapir.Endpoint
import sttp.tapir.server.finatra.{FinatraRoute, FinatraServerOptions, FinatraTestServerInterpreter}
import sttp.tapir.server.interceptor.decodefailure.{DecodeFailureHandler, DefaultDecodeFailureHandler}
import sttp.tapir.server.tests.TestServerInterpreter
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.tests.Port

import scala.concurrent.ExecutionContext
import scala.reflect.ClassTag

class FinatraCatsTestServerInterpreter extends TestServerInterpreter[IO, Any, FinatraRoute] {
  implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global
  implicit val contextShift: ContextShift[IO] = IO.contextShift(ec)
  implicit val timer: Timer[IO] = IO.timer(ec)

  override def route(
      e: ServerEndpoint[Any, IO],
      decodeFailureHandler: Option[DecodeFailureHandler] = None
  ): FinatraRoute = {
    implicit val serverOptions: FinatraServerOptions =
      FinatraServerOptions.customInterceptors(decodeFailureHandler = decodeFailureHandler.getOrElse(DefaultDecodeFailureHandler.handler))
    FinatraCatsServerInterpreter.toRoute(e)
  }

  override def routeRecoverErrors[I, E <: Throwable, O](e: Endpoint[I, E, O, Any], fn: I => IO[O])(implicit
      eClassTag: ClassTag[E]
  ): FinatraRoute = FinatraCatsServerInterpreter.toRouteRecoverErrors(e)(fn)

  override def server(routes: NonEmptyList[FinatraRoute]): Resource[IO, Port] = FinatraTestServerInterpreter.server(routes)
}
