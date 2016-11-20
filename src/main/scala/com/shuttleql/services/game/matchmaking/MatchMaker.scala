package com.shuttleql.services.game.matchmaking

import java.util.concurrent.{ScheduledFuture, ScheduledThreadPoolExecutor, TimeUnit}

import com.shuttleql.services.game.data.{Match, MatchType, Player}
import com.typesafe.config.ConfigFactory

import scala.util.Random
import scala.collection.JavaConversions._
import scala.collection.mutable

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
    }
  }
  var matchMakingTaskHandler: Option[ScheduledFuture[_]] = None

  var matches: List[Match] = List()
  var playerQ: mutable.Queue[Player] = mutable.Queue()
  var lastRotationTime = System.currentTimeMillis() / 1000

  def getMatches: List[Match] = {
    matches
  }

  def getQueue: List[Player] = {
    playerQ.toList
  }

  def getRotationTimeLeft: Long = {
    val currentTime = System.currentTimeMillis() / 1000
    Math.max(0, rotationTime.getSeconds - (currentTime - lastRotationTime))
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

  def swap(userId: Int, userId2: Int): Boolean = {
    val match1Index = matches.indexWhere(court => court.team1.exists(x => x.id == userId) || court.team2.exists(x => x.id == userId))
    val match2Index = matches.indexWhere(court => court.team1.exists(x => x.id == userId2) || court.team2.exists(x => x.id == userId2))

    // Both players are in the queue
    if (match1Index == -1 && match2Index == -1) {
      return false
    }

    // 1. Both players are in a match
    if (match1Index != -1 && match2Index != -1) {
      val splitIndex = Math.min(match1Index, match2Index) 
      val splitIndex2 = Math.max(match1Index, match2Index) 

      val split1 = if (splitIndex != 0) matches.slice(0, splitIndex) else List()
      val split2 = if (splitIndex + 1 != splitIndex2) matches.slice(splitIndex + 1, splitIndex2) else List()
      val split3 = if (splitIndex2 + 1 != matches.length) matches.slice(splitIndex2 + 1, matches.length) else List()

      val match1 = matches(match1Index)
      val match2 = matches(match2Index)

      val player1: Player = (match1.team1 ++ match1.team2).find(x => x.id == userId).get
      val player2: Player = (match2.team1 ++ match2.team2).find(x => x.id == userId2).get

      val newMatch1 = Match(
        team1 = match1.team1.map { player =>
          if (player == player1) player2 else player
        },
        team2 = match1.team2.map { player =>
          if (player == player1) player2 else player
        },
        courtName = match1.courtName,
        courtId = match1.courtId,
        courtType = match1.courtType
      )

      val newMatch2 = Match(
        team1 = match2.team1.map { player =>
          if (player == player2) player1 else player
        },
        team2 = match2.team2.map { player =>
          if (player == player2) player1 else player
        },
        courtName = match2.courtName,
        courtId = match2.courtId,
        courtType = match2.courtType
      )

      val firstMatch = if (match2Index < match1Index) newMatch2 else newMatch1
      val secondMatch = if (match2Index > match1Index) newMatch2 else newMatch1

      matches = (split1 :+ firstMatch) ++ (split2 :+ secondMatch) ++ split3
    } else if (match1Index != -1 && match2Index == -1) {
      // 2. Player 1 is in a match, player 2 is in the queue
      swapPlayerInMatchAndQueue(userId, userId2, match1Index)
    } else if (match1Index == -1 && match2Index != -1) {
      // 3. Player 2 is in a match, player 1 is in the queue
      swapPlayerInMatchAndQueue(userId2, userId, match2Index)
    }

    return true
  }

  def swapPlayerInMatchAndQueue(userId: Int, userId2: Int, matchIndex: Int) {
    val split1 = if (matchIndex != 0) matches.slice(0, matchIndex) else List()
    val split2 = if (matchIndex + 1 != matches.length) matches.slice(matchIndex + 1, matches.length) else List()

    val court = matches(matchIndex)
    val player1: Player = (court.team1 ++ court.team2).find(x => x.id == userId).get
    val player2: Player = playerQ.find(x => x.id == userId2).get

    val newMatch = Match(
      team1 = court.team1.map { player =>
        if (player == player1) player2 else player
      },
      team2 = court.team2.map { player =>
        if (player == player1) player2 else player
      },
      courtName = court.courtName,
      courtId = court.courtId,
      courtType = court.courtType
    )

    matches = (split1 :+ newMatch) ++ split2
    var newPlayerQ: mutable.Queue[Player] = mutable.Queue()
    newPlayerQ.enqueue(player1)
    newPlayerQ ++= playerQ.filter(x => x.id != userId2)
    playerQ = newPlayerQ
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

    lastRotationTime = System.currentTimeMillis() / 1000
  }

}
