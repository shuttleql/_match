package com.shuttleql.services.game

import com.shuttleql.services.game.data.{MatchType, Player}
import com.shuttleql.services.game.matchmaking.MatchMaker
import org.json4s.JsonAST.JObject
import org.json4s.ext.EnumNameSerializer
import org.scalatra.json._
import org.json4s.{DefaultFormats, Formats}
import org.scalatra.{BadRequest, Ok}
import com.typesafe.config._
import com.gandalf.HMACAuth

class GameServiceServlet extends GameServiceStack with JacksonJsonSupport {

  protected implicit val jsonFormats: Formats = DefaultFormats + new EnumNameSerializer(MatchType)

  val conf = ConfigFactory.load()

  private def getRequest = enrichRequest(request)
  private def getResponse = enrichResponse(response)

  before() {
    auth
    contentType = formats("json")
  }

  def auth() {
    val token = getRequest.header("Authorization")
    val key = getRequest.header("Authorization-Key")
    val secret = conf.getString("secrets.hmac_secret")

    (token, key) match {
      case (Some(t), Some(k)) =>
        val split = t.split("HMAC ")
        split.length match {
          case 2 =>
            HMACAuth.validateHost(split(1), k, secret) match {
              case true => return
              case false =>
                halt(status=401, reason="Forbidden");
            }
          case _ =>
            halt(status=401, reason="Forbidden");
        }
      case _ =>
        halt(status=401, reason="Forbidden");
    }
  }

  get("/") {
    Ok(Map(
      "matches" -> MatchMaker.getMatches,
      "queue" -> MatchMaker.getQueue
    ))
  }

  post("/checkedinplayers") {
    val player = parsedBody.extract[Player]
    MatchMaker.checkInPlayer(player)
    Ok(JObject(obj = List()))
  }

  delete("/checkedinplayers/:id") {
    val playerId = params("id").toInt
    MatchMaker.checkOutPlayer(playerId)
    Ok(JObject(obj = List()))
  }

  put("/status/:status") {
    params("status") match {
      case "start" =>
        val playerList = parsedBody.extract[List[Player]]
        MatchMaker.startMatchGeneration(players = playerList)
        Ok(JObject(obj = List()))
      case "stop" =>
        MatchMaker.stopMatchGeneration()
        Ok(JObject(obj = List()))
      case badStatus =>
        BadRequest(reason = "Invalid status type: " + badStatus)
    }
  }

}
