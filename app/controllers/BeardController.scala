package controllers

import play.api._
import play.api.mvc._
import play.api.libs.json._
import play.api.libs.functional.syntax._
import play.libs.Json._
import reactivemongo.api.commands.WriteResult
import reactivemongo.bson._
import service.BeardService
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import service.mongo.MongoResponse

import scala.collection.mutable
import scala.collection.mutable.ListBuffer
import scala.concurrent.Future
import scala.util.{Failure, Success, Random}
import scala.util.parsing.json.JSONObject

class BeardController extends Controller {

  val beardService = new BeardService

  def index = Action { request =>
    Ok(views.html.index("dream.explore.die"))
  }

  def query = Action.async(parse.json) { request =>

    implicit
    val queryReader = (__ \ 'query).read[String]

    implicit
    object ImplicitDocumentReader extends BSONReader[BSONValue, String] {
      def read(v: BSONValue) =
        v match {
          case oid: BSONObjectID => oid.stringify
          case BSONInteger(integerValue) => integerValue.toString
          case BSONLong(longValue) => longValue.toString
          case BSONDouble(doubleValue) => doubleValue.toString
          case BSONString(stringValue) => stringValue.toString
        }
    }

    val queryStringResult : JsResult[String] = request.body.validate(queryReader)
    val queryString = queryStringResult.get
    var queryType = queryString
    var actualQuery = ""
    if(queryString.contains('(')) {
      queryType = queryString.substring(0, queryString.indexOf('('))
      actualQuery = queryString.substring(queryString.indexOf('(') + 1, queryString.lastIndexOf(')'))
    }

    val mongoResponse : MongoResponse = beardService.query(queryType, actualQuery)

    if (queryType.eq("find")) {
      val result = mongoResponse.result.asInstanceOf[Future[List[BSONDocument]]]

      var listBuffer = new ListBuffer[mutable.Map[String, String]]

      result.map(list => {
        list.foreach(document => {
          val map = mutable.Map[String, String]().empty
          document.elements.foreach(element => {
            map+= element._1 -> element._2.as[String] //as uses implicit reader
          })
          listBuffer+= map
        })
        val jsonString = com.codahale.jerkson.Json.generate(Map("result" -> listBuffer.toList))
        Ok(jsonString)
      })
    } else {
      val result = mongoResponse.result.asInstanceOf[Future[WriteResult]]
      result.map(writableResult => {
        Ok(Json.obj("message" -> writableResult.message))
      })
    }
  }

  def intensiveComputation(): JsObject = {
      Thread.sleep(Random.nextInt(5000))
        Json.obj("value" -> "beard")
  }

  def sayAsyncBeard = Action.async { request =>
    val futureInt = Future {
      intensiveComputation()
    }
    futureInt.map(result =>
      Ok(result)
    )
  }

}
