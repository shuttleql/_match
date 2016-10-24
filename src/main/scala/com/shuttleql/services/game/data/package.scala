package com.shuttleql.services.game

/**
  * Created by jasonf7 on 2016-10-15.
  */
package object data {

  type Team = List[Player]

  case class Player(
     id: Int,
     name: String
  )

  case class Match(
    team1: Team,
    team2: Team,
    courtName: String,
    courtId: Int
  )

}
