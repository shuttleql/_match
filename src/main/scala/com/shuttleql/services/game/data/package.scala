package com.shuttleql.services.game

import com.shuttleql.services.game.data.MatchType.MatchType

/**
  * Created by jasonf7 on 2016-10-15.
  */
package object data {

  type Team = List[Player]
  type CourtInfo = List[MatchType]

  object MatchType extends Enumeration {
    type MatchType = Value
    val Singles = Value("Singles")
    val Doubles = Value("Doubles")
  }

  case class Player(
     id: Int,
     name: String,
     level: Int,
     preference: MatchType
  )

  case class Match(
    team1: Team,
    team2: Team,
    courtName: String,
    courtId: Int
  )

}
