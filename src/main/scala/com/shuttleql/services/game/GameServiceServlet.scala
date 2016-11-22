package com.shuttleql.services.game

import com.shuttleql.services.game.data.{MatchType, Player, MatchOverrideParams}
import com.shuttleql.services.game.matchmaking.MatchMaker
import org.json4s.ext.EnumNameSerializer
import org.scalatra.json._
import org.json4s.{DefaultFormats, Formats}
import org.scalatra._
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

  def auth =  {
    val token = getRequest.header("Authorization")
    val key = getRequest.header("Authorization-Key")
    val secret = conf.getString("secrets.hmac_secret")

    (token, key) match {
      case (Some(t), Some(k)) =>
        val split = t.split("HMAC ")
        split.length match {
          case 2 =>
            HMACAuth.validateHost(split(1), k, secret) match {
              case true => true
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
      "queue" -> MatchMaker.getQueue,
      "timeLeft" -> MatchMaker.getRotationTimeLeft
    ))
  }

  post("/checkedinplayers") {
    val player = parsedBody.extract[Player]
    MatchMaker.checkInPlayer(player)
    NoContent(reason = "Success")
  }

  delete("/checkedinplayers/:id") {
    val playerId = params("id").toInt
    MatchMaker.checkOutPlayer(playerId)
    NoContent(reason = "Success")
  }

  put("/status/:status") {
    params("status") match {
      case "start" =>
        val playerList = parsedBody.extract[List[Player]]
        MatchMaker.startMatchGeneration(players = playerList)
        NoContent(reason = "Success")
      case "stop" =>
        MatchMaker.stopMatchGeneration()
        NoContent(reason = "Success")
      case badStatus =>
        BadRequest(reason = "Invalid status type: " + badStatus)
    }
  }

  put("/override") {
    val matchOverride = parsedBody.extract[MatchOverrideParams]
    val userId = matchOverride.userId
    val userId2 = matchOverride.userId2

    MatchMaker.swap(userId, userId2) match {
      case true =>
        NoContent(reason = "Success")
      case false =>
        InternalServerError(reason = "Error with match override.")
    }
  }
}
