package com.shuttleql.services.game

import com.shuttleql.services.game.matchmaking.MatchMaker
import org.scalatra.json._
import org.json4s.{DefaultFormats, Formats}
import org.scalatra.Ok

class GameServiceServlet extends GameServiceStack with JacksonJsonSupport {

  protected implicit val jsonFormats: Formats = DefaultFormats

  before() {
    contentType = formats("json")
  }

  get("/matches") {
    Ok(MatchMaker.getMatches)
  }

  post("/matches/start") {
    MatchMaker.startMatchGeneration
    Ok()
  }

  post("/matches/stop") {
    MatchMaker.stopMatchGeneration
    Ok()
  }

}
