package com.shuttleql.services.game

import com.shuttleql.services.game.matchmaking.MatchMaker
import org.json4s.JsonAST.JObject
import org.scalatra.json._
import org.json4s.{DefaultFormats, Formats}
import org.scalatra.{BadRequest, Ok}

class GameServiceServlet extends GameServiceStack with JacksonJsonSupport {

  protected implicit val jsonFormats: Formats = DefaultFormats

  before() {
    contentType = formats("json")
  }

  get("/") {
    Ok(MatchMaker.getMatches)
  }

  put("/status/:status") {
    params("status") match {
      case "start" =>
        MatchMaker.startMatchGeneration
        Ok(JObject(obj = List()))
      case "stop" =>
        MatchMaker.stopMatchGeneration
        Ok(JObject(obj = List()))
      case badStatus =>
        BadRequest("Invalid status type: " + badStatus)
    }
  }

}
