package commercial.controllers

import java.nio.ByteBuffer

import awswrappers.kinesisfirehose._
import com.amazonaws.services.kinesisfirehose.model.{PutRecordRequest, Record}
import com.amazonaws.services.kinesisfirehose.{AmazonKinesisFirehoseAsync, AmazonKinesisFirehoseAsyncClientBuilder}
import common.Logging
import conf.Configuration.aws.{mandatoryCredentials, region}
import conf.Configuration.commercial.prebidAnalyticsStream
import conf.Configuration.environment.isProd
import conf.switches.Switches.prebidAnalytics
import model.Cached.WithoutRevalidationResult
import model.{CacheTime, Cached, TinyResponse}
import play.api.libs.json.JsValue
import play.api.libs.json.Json.{prettyPrint, toBytes}
import play.api.mvc._

import scala.concurrent.ExecutionContext
import scala.util.control.NonFatal

class PrebidAnalyticsController(val controllerComponents: ControllerComponents) extends BaseController with Logging {

  private implicit val ec: ExecutionContext = controllerComponents.executionContext

  private val firehose: AmazonKinesisFirehoseAsync = {
    AmazonKinesisFirehoseAsyncClientBuilder
      .standard()
      .withCredentials(mandatoryCredentials)
      .withRegion(region)
      .build()
  }

  private val newLine = "\n".getBytes

  private def streamAnalytics(json: JsValue) = {
    val record  = new Record().withData(ByteBuffer.wrap(toBytes(json) ++ newLine))
    val request = new PutRecordRequest().withDeliveryStreamName(prebidAnalyticsStream).withRecord(record)
    val result  = firehose.putRecordFuture(request)
    result.failed foreach {
      case NonFatal(e) => log.error(s"Failed to put '$json'", e)
    }
  }

  private def serve404[A](implicit request: Request[A]) =
    Cached(CacheTime.NotFound)(WithoutRevalidationResult(NotFound))

  def insert(): Action[JsValue] = Action(parse.json) { implicit request =>
    if (prebidAnalytics.isSwitchedOn) {
      if (isProd) streamAnalytics(request.body)
      else log.info(prettyPrint(request.body))
      TinyResponse.noContent()
    } else
      serve404
  }

  def getOptions: Action[AnyContent] = Action { implicit request =>
    if (prebidAnalytics.isSwitchedOn)
      TinyResponse.noContent(Some("OPTIONS, PUT"))
    else
      serve404
  }
}
