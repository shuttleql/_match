package com.shuttleql.services.game.matchmaking

import java.util.concurrent.{ScheduledFuture, ScheduledThreadPoolExecutor, TimeUnit}

import com.shuttleql.services.game.data.{Match, MatchType, Player}
import com.typesafe.config.ConfigFactory

import scala.util.Random
import scala.collection.JavaConversions._
import scala.collection.mutable

/**
  * Created by jasonf7 on 2016-10-15.
  */
object MatchMaker {

  val clubConfig = ConfigFactory.load().getConfig("clubConf")
  val rotationTime = clubConfig.getDuration("rotationTime")
  val numCourts = clubConfig.getInt("numCourts")
  val courtTypes = clubConfig.getStringList("courtType").map { s => MatchType.withName(s) }.toList
  val numDoublesCourts = courtTypes.filter(_ == MatchType.Doubles).length
  val numSinglesCourts = courtTypes.filter(_ == MatchType.Singles).length

  val matchScheduler = new ScheduledThreadPoolExecutor(1)
  val matchMakingTask = new Runnable {
    override def run(): Unit = {
      generateMatches()
      // TODO: call notification service
    }
  }
  var matchMakingTaskHandler: Option[ScheduledFuture[_]] = None

  var matches: List[Match] = List()
  var playerQ: mutable.Queue[Player] = mutable.Queue()

  def getMatches: List[Match] = {
    matches
  }

  def startMatchGeneration(players: List[Player]): Unit = {
    if ( matchMakingTaskHandler.isDefined ) {
      stopMatchGeneration()
    }

    playerQ ++= players

    matchMakingTaskHandler = Option(
      matchScheduler.scheduleAtFixedRate(
        matchMakingTask,
        3,
        rotationTime.getSeconds,
        TimeUnit.SECONDS
      )
    )
  }

  def stopMatchGeneration(): Unit = {
    matchMakingTaskHandler.map(_.cancel(true))
    matchMakingTaskHandler = None
    matches = List()
    playerQ.clear()
  }

  def checkInPlayer(player: Player): Unit = {
    playerQ += player
  }

  def checkOutPlayer(playerId: Int): Unit = {
    playerQ = playerQ.filterNot(player => player.id == playerId)
  }

  def generateMatches(): Unit = {
    // 1. Enqueue current players back into queue
    val previousPlayers = matches.flatMap { m => m.team1 ++ m.team2 }
    playerQ ++= previousPlayers

    // 2. Dequeue players
    val poolCount = Math.min(playerQ.length, numDoublesCourts * 4 + numSinglesCourts * 2)
    val currentPlayers = for (i <- 1 to poolCount) yield playerQ.dequeue()

    // 3. Shuffle players by level
    val randomPlayerList = currentPlayers
      .groupBy(_.level)
      .map { case (level, players) =>
        (level, Random.shuffle(players))
      }.values.flatten

    // 4. Find random split index
    val splitIndex = Random.nextInt(randomPlayerList.size)

    // 5. Create chunks of size 4 and size 2
    val chunks = randomPlayerList.splitAt(splitIndex) match {
      case (first, second) => {
        val allChunks = first.grouped(4).toList ::: second.toList.reverse.grouped(4).toList
        val remainder = allChunks
          .filterNot(_.size == 4)
          .flatten
          .grouped(4)
          .toList
        val (left, right) = (allChunks.filter(_.size == 4) ::: remainder).splitAt(numDoublesCourts)
        left ::: right.flatten.grouped(2).toList
      }
    }

    // 6. Map the chunks into matches
    matches = chunks
      .map { players => players.splitAt(players.size/2) }
      .zipWithIndex
      .map { case ((team1, team2), ind) =>
        val courtId = ind + 1
        Match(
          team1 = team1.toList,
          team2 = team2.toList,
          courtName = "Court " + courtId,
          courtId = courtId
        )
      }

    println("***** MATCHES *****")
    matches.foreach(println)
    println("***** PLAYER QUEUE *****")
    playerQ.foreach(println)
  }

}
