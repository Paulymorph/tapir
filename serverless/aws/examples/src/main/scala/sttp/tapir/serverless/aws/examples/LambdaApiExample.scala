package sttp.tapir.serverless.aws.examples

import cats.effect.IO
import cats.syntax.all._
import com.amazonaws.services.lambda.runtime.{Context, RequestStreamHandler}
import io.circe.Printer
import io.circe.generic.auto._
import io.circe.parser.decode
import io.circe.syntax._
import sttp.model.StatusCode
import sttp.tapir._
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.serverless.aws.examples.LambdaApiExample.{helloEndpoint, route}
import sttp.tapir.serverless.aws.lambda._
import sttp.tapir.serverless.aws.sam.{AwsSamInterpreter, AwsSamOptions, CodeSource}

import java.io.{BufferedWriter, InputStream, OutputStream, OutputStreamWriter}
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Paths}

/** Example assumes that you have `sam local` installed on your OS. Installation is described here:
  * https://docs.aws.amazon.com/serverless-application-model/latest/developerguide/serverless-sam-cli-install.html
  *
  * Select `awsExamples` project from sbt shell and run `assembly` task to build a fat jar with lambda handler.
  * Then `runSamExample` to generate `template.yaml` and start up `sam local`.
  */
class LambdaApiExample extends RequestStreamHandler {

  override def handleRequest(input: InputStream, output: OutputStream, context: Context): Unit = {

    /** Read input as string */
    val json = new String(input.readAllBytes(), UTF_8)

    /** Decode input to `AwsRequest` which is send by API Gateway */
    (decode[AwsRequest](json) match {
      /** Process request using interpreted route */
      case Right(awsRequest) => route(awsRequest)
      case Left(_)           => IO.pure(AwsResponse(Nil, isBase64Encoded = false, StatusCode.BadRequest.code, Map.empty, ""))
    }).map { awsRes =>
      /** Write response to output */
      val writer = new BufferedWriter(new OutputStreamWriter(output, UTF_8))
      writer.write(Printer.noSpaces.print(awsRes.asJson))
      writer.flush()
    }.unsafeRunSync()
  }
}

object LambdaApiExample {

  val helloEndpoint: ServerEndpoint[Unit, Unit, String, Any, IO] = endpoint.get
    .in("api" / "hello")
    .out(stringBody)
    .serverLogic { _ => IO.pure("Hello!".asRight[Unit]) }

  implicit val options: AwsServerOptions[IO] = AwsServerOptions.customInterceptors(encodeResponseBody = false)

  val route: Route[IO] = AwsServerInterpreter.toRoute(helloEndpoint)
}

/** Before running the actual example we need to interpret our api as SAM template */
object LambdaApiExampleSamTemplate extends App {

  val jarPath = Paths.get("serverless/aws/examples/target/jvm-2.13/tapir-aws-examples.jar").toAbsolutePath.toString

  implicit val samOptions: AwsSamOptions = AwsSamOptions(
    "PersonsApi",
    source =
      /** Specifying a fat jar build from example sources */
      CodeSource(
        runtime = "java11",
        codeUri = jarPath,
        handler = "sttp.tapir.serverless.aws.examples.LambdaApiExample::handleRequest"
      )
  )

  val templateYaml = AwsSamInterpreter.toSamTemplate(helloEndpoint).toYaml

  /** Write template to file, it's required to run the example using sam local */
  Files.write(Paths.get("template.yaml"), templateYaml.getBytes(UTF_8))
}