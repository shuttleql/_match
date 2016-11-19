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
  val numDoublesCourts = courtTypes.count(_ == MatchType.Doubles)
  val numSinglesCourts = courtTypes.count(_ == MatchType.Singles)

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

  def getQueue: List[Player] = {
    playerQ.toList
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
    if ( matchMakingTaskHandler.isDefined ) {
      playerQ += player
    }
  }

  def checkOutPlayer(playerId: Int): Unit = {
    playerQ = playerQ.filterNot(player => player.id == playerId)
  }

  def generateMatches(): Unit = {
    val doublesSize = MatchType.toSize(MatchType.Doubles)
    val singlesSize = MatchType.toSize(MatchType.Singles)

    // 1. Enqueue current players back into queue
    val previousPlayers = matches.flatMap { m => m.team1 ++ m.team2 }
    playerQ ++= previousPlayers

    // 2. Dequeue players
    val numPlayers = numDoublesCourts * doublesSize + numSinglesCourts * singlesSize
    val poolCount = Math.min(playerQ.length, numPlayers)
    val currentPlayers = for (i <- 1 to poolCount) yield playerQ.dequeue()

    // 3. Shuffle players by level
    val randomPlayerList = currentPlayers
      .groupBy(_.level)
      .map { case (level, players) =>
        (level, Random.shuffle(players))
      }.values.flatten

    // 4. Find random split index
    val splitIndex = if (randomPlayerList.nonEmpty) Random.nextInt(randomPlayerList.size) else 0

    // 5. Create chunks of size 4 and size 2
    val chunks = randomPlayerList.splitAt(splitIndex) match {
      case (first, second) => {
        val allChunks = first.grouped(doublesSize).toList ::: second.toList.reverse.grouped(doublesSize).toList
        val remainder = allChunks
          .filterNot(_.size == doublesSize)
          .flatten
          .grouped(doublesSize)
          .toList
        val (left, right) = (allChunks.filter(_.size == doublesSize) ::: remainder).splitAt(numDoublesCourts)
        left ::: right.flatten.grouped(singlesSize).toList
      }
    }

    // 6. Map the chunks into matches
    val sortedCourtTypes = courtTypes
      .zipWithIndex
      .map { case (matchType, ind) =>
        (ind + 1, MatchType.toSize(matchType))
      }
      .sortBy( - _._2)

    val playerTeams = chunks
      .map { players => players.splitAt(players.size/2) }
      .padTo(sortedCourtTypes.length, (Iterable(), Iterable()))

    matches = (playerTeams zip sortedCourtTypes)
      .map { case ((team1, team2), (courtId, courtSize)) =>
        Match(
          team1 = team1.toList,
          team2 = team2.toList,
          courtName = "Court " + courtId,
          courtId = courtId,
          courtType = MatchType.toType(courtSize)
        )
      }
      .sortBy(_.courtId)

    println("***** MATCHES *****")
    matches.foreach { lol =>
      println(lol.courtId + ":" + lol.team1.size + "/" + lol.team2.size + "-" + lol.courtType)
    }
    println("***** PLAYER QUEUE *****")
    playerQ.foreach(println)
  }

}
