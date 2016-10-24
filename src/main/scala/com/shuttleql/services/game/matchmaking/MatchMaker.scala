package com.shuttleql.services.game.matchmaking

import java.util.UUID
import java.util.concurrent.{ScheduledFuture, ScheduledThreadPoolExecutor, TimeUnit}

import com.shuttleql.services.game.data.{Match, Player}

import scala.collection.mutable
import scala.util.Random


/**
  * Created by jasonf7 on 2016-10-15.
  */
object MatchMaker {

  val matchScheduler = new ScheduledThreadPoolExecutor(1)
  val matchMakingTask = new Runnable {
    override def run(): Unit = {
      generateMatches
      // TODO: call notification service
    }
  }
  var matchMakingTaskHandler: Option[ScheduledFuture[_]] = None

  var matches: List[Match] = List()

  def getMatches: List[Match] = {
    matches
  }

  def startMatchGeneration: Unit = {
    if ( matchMakingTaskHandler.isDefined ) {
      stopMatchGeneration
    }

    matchMakingTaskHandler = Option(matchScheduler.scheduleAtFixedRate(
      matchMakingTask,
      0,
      5,
      TimeUnit.SECONDS
    ))
  }

  def stopMatchGeneration: Unit = {
    matchMakingTaskHandler.map(_.cancel(true))
    matchMakingTaskHandler = None
    matches = List()
  }

  /**
    * TODO: Shouldn't return random matches
    */
  def generateMatches: Unit = {
    println("Let's Generate")

    val firstNames = List("David", "Clement", "Jason", "Tony", "Zach", "Daniel", "Andrew", "Dan", "Chong Wei")
    val lastNames = List("Dong", "Hoang", "Fang", "Lu", "Li", "Chen", "Zhou", "Lin", "Lee")

    val players = List.range(1, 29).map { playerId =>
      Player(
        id = playerId,
        name = firstNames(Random.nextInt(firstNames.size)) + " " + lastNames(Random.nextInt(lastNames.size))
      )
    }

    matches = List.range(1, 9).map { cid =>
      if (cid < 7) {
        Match(
          team1 = List(players((cid - 1)*4), players((cid - 1)*4+1)),
          team2 = List(players((cid - 1)*4+2), players((cid - 1)*4+3)),
          courtName = "Court " + cid,
          courtId = cid
        )
      } else {
        Match(
          team1 = List(players((cid - 7)*2+24)),
          team2 = List(players((cid - 7)*2+25)),
          courtName = "Court " + cid,
          courtId = cid
        )
      }
    }
  }

}
