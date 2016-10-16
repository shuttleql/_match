package com.shuttleql.services.game

/**
  * Created by jasonf7 on 2016-10-15.
  */
package object data {

  type UserId = String
  type Team = List[UserId]

  case class Match(
    team1: Team,
    team2: Team
  )

}
