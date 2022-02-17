package net.carboninter.models

import swagger.definitions.{LevelPriceSize, MarketChange, MarketChangeMessage, MarketDefinition, PriceSize, RunnerChange}

object MarketChangeMergeTools {

  private def transformIfOriginalAndDelta[A](transformation: (A, A) => A)(original: Option[A], delta: Option[A]): Option[A] = (original, delta) match {
    case (original, None) => original
    case (None, delta)    => delta
    case (Some(original), Some(delta)) => Some(transformation(original, delta))
  }

  private def transformIfDelta[A](transformation: (A, A) => A)(original: A, delta: Option[A]): A = (original, delta) match {
    case (original, None) => original
    case (original, Some(delta)) => transformation(original, delta)
  }

  private def mergeListItemsIfDelta[A](merger: (A, Option[A]) => A)(finder: A => Vector[A] => Option[A])(originals: Option[Vector[A]], deltas: Option[Vector[A]]) = {
    transformIfOriginalAndDelta[Vector[A]] { (originals, deltas) =>
      originals.map { original =>
        merger(original, finder(original)(deltas))
      }
    }(originals, deltas)
  }

  def mergePriceSizes(original: Vector[PriceSize], delta: Vector[PriceSize]): Vector[PriceSize] = {
    val (changes, additions) = delta.partition(d => original.map(_.price).contains(d.price))
    original.map(p => changes.find(_.price==p.price).getOrElse(p)).filterNot(_.size==0.0) ++ additions
  }
  val mergePriceSizesIfOriginalAndDelta = transformIfOriginalAndDelta(mergePriceSizes)

  def mergeLevelPriceSizes(original: Vector[LevelPriceSize], delta: Vector[LevelPriceSize]): Vector[LevelPriceSize] = {
    val (changes, additions) = delta.partition(d => original.map(_.level).contains(d.level))
    original.map(p => changes.find(_.level==p.level).getOrElse(p)).filterNot(_.size==0.0) ++ additions
  }
  val mergeLevelPriceSizesIfOriginalAndDelta = transformIfOriginalAndDelta(mergeLevelPriceSizes)

  def replace[A](original: A, delta: A) = delta

  def replaceIfOriginalAndDelta[A] = transformIfOriginalAndDelta[A](replace)

  def mergeRC(original: RunnerChange, delta: RunnerChange): RunnerChange = {
    val newSpf = replaceIfOriginalAndDelta(original.spf, delta.spf)
    original.copy(
      tv    = replaceIfOriginalAndDelta(original.tv, delta.tv),
      batb  = mergeLevelPriceSizesIfOriginalAndDelta(original.batb, delta.batb),
      spb   = mergePriceSizesIfOriginalAndDelta(original.spb, delta.spb),
      bdatl = mergeLevelPriceSizesIfOriginalAndDelta(original.bdatl, delta.bdatl),
      trd   = mergePriceSizesIfOriginalAndDelta(original.trd, delta.trd),
      spf   = replaceIfOriginalAndDelta(original.spf, delta.spf),
      ltp   = replaceIfOriginalAndDelta(original.ltp, delta.ltp),
      atb   = mergePriceSizesIfOriginalAndDelta(original.atb, delta.atb),
      spl   = mergePriceSizesIfOriginalAndDelta(original.spl, delta.spl),
      spn   = replaceIfOriginalAndDelta(original.spn, delta.spn),
      atl   = mergePriceSizesIfOriginalAndDelta(original.atl, delta.atl),
      batl  = mergeLevelPriceSizesIfOriginalAndDelta(original.batl, delta.batl),
      hc    = replaceIfOriginalAndDelta(original.hc, delta.hc),
      bdatb = mergeLevelPriceSizesIfOriginalAndDelta(original.bdatb, delta.bdatb)
    )
  }
  val mergeRCIfDelta = transformIfDelta(mergeRC)

  def mergeMarketDefinition(original: MarketDefinition, delta: MarketDefinition) = {
    original.copy(
      venue                 = replaceIfOriginalAndDelta(original.venue, delta.venue),
      raceType              = replaceIfOriginalAndDelta(original.raceType, delta.raceType),
      settledTime           = replaceIfOriginalAndDelta(original.settledTime, delta.settledTime),
      timezone              = replaceIfOriginalAndDelta(original.timezone, delta.timezone),
      eachWayDivisor        = replaceIfOriginalAndDelta(original.eachWayDivisor, delta.eachWayDivisor),
      regulators            = replaceIfOriginalAndDelta(original.regulators, delta.regulators),
      marketType            = replaceIfOriginalAndDelta(original.marketType, delta.marketType),
      marketBaseRate        = replaceIfOriginalAndDelta(original.marketBaseRate, delta.marketBaseRate),
      numberOfWinners       = replaceIfOriginalAndDelta(original.numberOfWinners, delta.numberOfWinners),
      countryCode           = replaceIfOriginalAndDelta(original.countryCode, delta.countryCode),
      lineMaxUnit           = replaceIfOriginalAndDelta(original.lineMaxUnit, delta.lineMaxUnit),
      inPlay                = replaceIfOriginalAndDelta(original.inPlay, delta.inPlay),
      betDelay              = replaceIfOriginalAndDelta(original.betDelay, delta.betDelay),
      bspMarket             = replaceIfOriginalAndDelta(original.bspMarket, delta.bspMarket),
      bettingType           = replaceIfOriginalAndDelta(original.bettingType, delta.bettingType),
      numberOfActiveRunners = replaceIfOriginalAndDelta(original.numberOfActiveRunners, delta.numberOfActiveRunners),
      lineMinUnit           = replaceIfOriginalAndDelta(original.lineMinUnit, delta.lineMinUnit),
      eventId               = replaceIfOriginalAndDelta(original.eventId, delta.eventId),
      crossMatching         = replaceIfOriginalAndDelta(original.crossMatching, delta.crossMatching),
      runnersVoidable       = replaceIfOriginalAndDelta(original.runnersVoidable, delta.runnersVoidable),
      turnInPlayEnabled     = replaceIfOriginalAndDelta(original.turnInPlayEnabled, delta.turnInPlayEnabled),
      priceLadderDefinition = replaceIfOriginalAndDelta(original.priceLadderDefinition, delta.priceLadderDefinition),
      keyLineDefinition     = replaceIfOriginalAndDelta(original.keyLineDefinition, delta.keyLineDefinition),
      suspendTime           = replaceIfOriginalAndDelta(original.suspendTime, delta.suspendTime),
      discountAllowed       = replaceIfOriginalAndDelta(original.discountAllowed, delta.discountAllowed),
      persistenceEnabled    = replaceIfOriginalAndDelta(original.persistenceEnabled, delta.persistenceEnabled),
      runners               = replaceIfOriginalAndDelta(original.runners, delta.runners),  //Think it's ok to just replace entire list
      version               = replaceIfOriginalAndDelta(original.version, delta.version),
      eventTypeId           = replaceIfOriginalAndDelta(original.eventTypeId, delta.eventTypeId),
      complete              = replaceIfOriginalAndDelta(original.complete, delta.complete),
      openDate              = replaceIfOriginalAndDelta(original.openDate, delta.openDate),
      marketTime            = replaceIfOriginalAndDelta(original.marketTime, delta.marketTime),
      bspReconciled         = replaceIfOriginalAndDelta(original.bspReconciled, delta.bspReconciled),
      lineInterval          = replaceIfOriginalAndDelta(original.lineInterval, delta.lineInterval),
      status                = replaceIfOriginalAndDelta(original.status, delta.status),
    )
  }
  val mergeMarketDefinitionIfOriginalAndDelta = transformIfOriginalAndDelta(mergeMarketDefinition)

  def mergeMC(original: MarketChange, delta: MarketChange): MarketChange = {
    if(original.img.getOrElse(false)) delta  //img means replace rather than merge 
    else
      original.copy(
        rc               = mergeListItemsIfDelta(mergeRCIfDelta)(original => deltas => deltas.find(_.id==original.id))(original.rc, delta.rc),
        img              = replaceIfOriginalAndDelta(original.img, delta.img),
        tv               = replaceIfOriginalAndDelta(original.tv, delta.tv),
        con              = replaceIfOriginalAndDelta(original.con, delta.con),
        marketDefinition = mergeMarketDefinitionIfOriginalAndDelta(original.marketDefinition, delta.marketDefinition)
      )
  }
  val mergeMCIfDelta = transformIfDelta(mergeMC)

  def mergeMCM(original: MarketChangeMessage, delta: MarketChangeMessage): MarketChangeMessage = {
    original.copy(
      ct          = replaceIfOriginalAndDelta(original.ct, delta.ct),
      clk         = replaceIfOriginalAndDelta(original.clk, delta.clk),
      heartbeatMs = replaceIfOriginalAndDelta(original.heartbeatMs, delta.heartbeatMs),
      pt          = replace(original.pt, delta.pt),
      mc          = mergeListItemsIfDelta(mergeMCIfDelta)(original => deltas => deltas.find(_.id==original.id))(original.mc, delta.mc)
    )
  }
  //    val mergeMCMIfDelta = transformIfDelta(mergeMCM)
}