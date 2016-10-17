package com.shuttleql.services.game.matchmaking

import java.util.UUID
import java.util.concurrent.{ScheduledFuture, ScheduledThreadPoolExecutor, TimeUnit}

import com.shuttleql.services.game.data.Match

import scala.collection.mutable


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

  var matches: mutable.MutableList[Match] = mutable.MutableList()

  def getMatches: List[Match] = {
    matches.toList
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
    matches = mutable.MutableList()
  }

  /**
    * TODO: Shouldn't return random matches
    */
  def generateMatches: Unit = {
    println("Let's Generate")

    matches = mutable.MutableList()

    for ( n <- 1 to 8) {
      if ( n % 4 != 0 ) {
        matches += Match(
          team1 = List(UUID.randomUUID().toString, UUID.randomUUID().toString),
          team2 = List(UUID.randomUUID().toString, UUID.randomUUID().toString)
        )
      } else {
        matches += Match(
          team1 = List(UUID.randomUUID().toString),
          team2 = List(UUID.randomUUID().toString)
        )
      }
    }
  }

}
