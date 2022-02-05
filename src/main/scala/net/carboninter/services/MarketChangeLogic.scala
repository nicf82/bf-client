package net.carboninter.services

import net.carboninter.models.*
import net.carboninter.syntax.*
import swagger.definitions.{LevelPriceSize, MarketChange, MarketChangeMessage, PriceSize, RunnerChange}
import zio.*

import scala.Console as C

object MarketChangeLogic:

  def handleMarketChangeMessage(mcm: MarketChangeMessage): RIO[Clock & BetfairService, Unit] = for {
    publishTime    <- ZIO.fromOption(mcm.pt).mapError(rte("No publishTime"))
    changeType     =  mcm.ct
    _              =  coutl(C.BOLD, C.MAGENTA)(s"\npt: ${publishTime.toOffsetDateTime}, ct: $changeType")
    _              =  coutl(C.BOLD, C.MAGENTA)("======================================")
    _              <- ZIO.foreach(mcm.mc.getOrElse(Nil).toList)(handleMarketChange(_))
  } yield ()

  def handleMarketChange(mc: MarketChange): RIO[Clock & BetfairService, Unit] = for {
    marketId       <- ZIO.fromOption(mc.id).mapError(rte("No id"))
    marketDef      =  mc.marketDefinition
    venue          =  marketDef.flatMap(_.venue)
    marketTime     =  marketDef.flatMap(_.marketTime)
    bettingType    =  marketDef.flatMap(_.bettingType)
    marketType     =  marketDef.flatMap(_.marketType)
    _              =  coutl(C.BOLD, C.GREEN)(s"$marketId $venue $marketTime $bettingType $marketType")
    _              =  coutl(C.BOLD, C.GREEN)("--------------------------------------")
    _              <- ZIO.foreach(mc.rc.getOrElse(Nil).toList)(handleRunnerChange(marketId, _))
  } yield ()

  def handleRunnerChange(marketId: String, runnerChange: RunnerChange): RIO[Clock & BetfairService, Unit] = for {
    betfairService <- ZIO.service[BetfairService]
    rcId            = runnerChange.id
    runner         <- betfairService.getRunner(marketId, rcId).flatMap(ZIO.fromOption).mapError(rte("Couldn't get runner"))
    _               = coutl(C.BOLD, C.YELLOW)(s"🐴 ${rcId} " + runner.name)

    _               = cout(C.RESET)("atb / atl ")
    _              <- ZIO.foreach(runnerChange.atb.map(_.sortBy(_.price)))(handlePriceSize(C.BLUE))
    _              <- ZIO.foreach(runnerChange.atl.map(_.sortBy(_.price)))(handlePriceSize(C.RED))

    _               = cout(C.RESET)("\nspb / spl ")
    _              <- ZIO.foreach(runnerChange.spb.map(_.sortBy(_.price)))(handlePriceSize(C.BLUE))
    _              <- ZIO.foreach(runnerChange.spl.map(_.sortBy(_.price)))(handlePriceSize(C.RED))

    _               = cout(C.RESET)("\ntrd       ")
    _              <- ZIO.foreach(runnerChange.trd.map(_.sortBy(_.price)))(handlePriceSize(C.BOLD))

    _               = cout(C.RESET)("\nbatb / batl")
    _              <- ZIO.foreach(runnerChange.batb.map(_.sortBy(_.level).reverse))(handleLevelPriceSize(C.BLUE))
    _              <- ZIO.foreach(runnerChange.batl.map(_.sortBy(_.level)))(handleLevelPriceSize(C.RED))

    _               = cout(C.RESET)("\nbdatb/bdatl")
    _              <- ZIO.foreach(runnerChange.bdatb.map(_.sortBy(_.level).reverse))(handleLevelPriceSize(C.BLUE))
    _              <- ZIO.foreach(runnerChange.bdatl.map(_.sortBy(_.level)))(handleLevelPriceSize(C.RED))

    _               = print("\n\n")
  } yield ()

  def handlePriceSize(color: String)(values: Vector[PriceSize]): RIO[Clock & BetfairService, Unit] = ZIO.succeed {
    if(values.isEmpty) cout(color, C.UNDERLINED)("Empty list (removed)")
    else values.filter(_.size >= 2).map(a => cout(color)(" "+a))
  }

  def handleLevelPriceSize(color: String)(values: Vector[LevelPriceSize]): RIO[Clock & BetfairService, Unit] = ZIO.succeed {
    if(values.isEmpty) cout(color, C.UNDERLINED)("Empty list (removed)")
    else values.filter(_.size >= 2).map(a => cout(color)(" "+a))
  }


//  def outputMarketInfo(mcm: MarketChangeMessage, localMarketCatalog: LocalMarketCatalog) = {
//
//    for(mc <- mcm.mc.getOrElse(Nil)) {
//
//      for((rc, i) <- mc.rc.getOrElse(Nil).zipWithIndex) {
//        coutl(C.BOLD, C.YELLOW)(s"\n$i, ${rc.id.get} " + localMarketCatalog.getRunner(rc.id.get).get.name)
//        for(atb <- rc.atb.getOrElse(Nil).sortBy(_(0))) {
//          print(" " + atb(0) + "@" + atb(1))
//        }
//        println()
//      }
//    }
//  }
