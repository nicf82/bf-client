//package net.carboninter.services
//
//import net.carboninter.models.*
//import net.carboninter.syntax.*
//import swagger.definitions.{LevelPriceSize, MarketChange, MarketChangeMessage, PriceSize, RunnerChange}
//import zio.*
//
//import scala.Console as C
//
////TODO merge with MarketChagePublisher
//trait MarketChangeLogic:
//
//  
//
//
////  def outputMarketInfo(mcm: MarketChangeMessage, localMarketCatalog: LocalMarketCatalog) = {
////
////    for(mc <- mcm.mc.getOrElse(Nil)) {
////
////      for((rc, i) <- mc.rc.getOrElse(Nil).zipWithIndex) {
////        coutl(C.BOLD, C.YELLOW)(s"\n$i, ${rc.id.get} " + localMarketCatalog.getRunner(rc.id.get).get.name)
////        for(atb <- rc.atb.getOrElse(Nil).sortBy(_(0))) {
////          print(" " + atb(0) + "@" + atb(1))
////        }
////        println()
////      }
////    }
////  }
