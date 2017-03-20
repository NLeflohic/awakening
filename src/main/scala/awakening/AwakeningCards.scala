// Labyrinth Awakening
//
// An scala implementation of the solo AI for the game 
// Labyrinth: The Awakening, 2010 - ?, designed by Trevor Bender and
// published by GMT Games.
// 
// Copyright (c) 2010-2017 Curt Sellmer
//
// Permission is hereby granted, free of charge, to any person obtaining
// a copy of this software and associated documentation files (the
// "Software"), to deal in the Software without restriction, including
// without limitation the rights to use, copy, modify, merge, publish,
// distribute, sublicense, and/or sell copies of the Software, and to
// permit persons to whom the Software is furnished to do so, subject to
// the following conditions:
//
// The above copyright notice and this permission notice shall be
// included in all copies or substantial portions of the Software.
//
// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
// EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
// MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
// NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE
// LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION
// OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
// WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
package awakening


// Card definitions for the the deck of cards in the
// Awakening expansion.

import scala.util.Random.shuffle
import scala.collection.immutable.ListMap
import LabyrinthAwakening._
import USBot.PlotInCountry

object AwakeningCards {
  
  // Various tests used by the card events
  val advisorsCandidate = (m: MuslimCountry) => !m.isAdversary && m.civilWar && m.totalTroops == 0 && !m.hasMarker("Advisors")
  val humanitarianAidCandidate = (m: MuslimCountry) => m.canTakeAidMarker && m.totalCells > 0
  val peshmergaCandidate = (m: MuslimCountry) => (m.name == Iraq || m.name == Syria) && m.totalCells > 0
  val arabSpringFalloutCandidate = (m: MuslimCountry) => {
    m.awakening == 0 &&
    m.canTakeAwakeningOrReactionMarker && 
    (game.adjacentMuslims(m.name) forall (_.awakening == 0))
  }
  val strikeEagleCandidate = (m: MuslimCountry) => m.name != Pakistan && m.isPoor && !m.isAlly && m.wmdCache > 0
  val tahrirCandidate = (m: MuslimCountry) => m.name != Egypt && m.canTakeAwakeningOrReactionMarker && m.awakening == 0
  
  val unscr1973Candidate = (m: MuslimCountry) => {
    // Can play in country that already has the marker if it also contains at least one cell.
    // Or in any other civil war country that does not have the maker.
    (m.hasMarker("UNSCR 1973") && m.totalCells > 0) || (m.civilWar && !m.hasMarker("UNSCR 1973"))
  }
  val backlashCandidate = (c: Country) =>
    (c.plots exists (p => !p.backlashed)) && !game.isCaliphateMember(c.name)
  val unNationBuildingCandidate = (m: MuslimCountry) =>
    (m.inRegimeChange || m.civilWar) &&
    !game.isCaliphateMember(m.name)
  val massTurnoutCandidate = (m: MuslimCountry) => 
    m.inRegimeChange && m.awakening > 0 && !game.isCaliphateMember(m.name)
  val scafCandidate = (m: MuslimCountry) => 
    m.name != Iran && m.name != Syria && m.awakening > 0 && m.reaction > 0 &&
    (!m.isAlly || !m.isFair || m.totalCells > 0)
  val statusQuoCandidate = (m: MuslimCountry) => 
    m.regimeChange == TanRegimeChange && 
    (m.totalTroopsAndMilitia / 2) > m.totalCells &&
    !game.isCaliphateMember(m.name)
  
  val coupCandidate = (m: MuslimCountry) => 
    m.resources == 1 && m.totalCells >= 2 && !(m.civilWar && m.besiegedRegime)
    
  val islamicMaghrebCountry = Set(AlgeriaTunisia, Libya, Mali, Morocco, Nigeria)
  val islamicMaghrebCandidate = (m: MuslimCountry) => islamicMaghrebCountry(m.name) && m.isPoor
  val theftOfStateCandidate = (m: MuslimCountry) => m.isPoor && m.awakening > 0
  val changeOfStateCandidate = (m: MuslimCountry) => {
    (m.isShiaMix || m.name == Jordan || m.name == Morocco) && !m.isUntested &&
    !(m.isGood || m.isIslamistRule || m.civilWar || m.inRegimeChange)
  }
  val botChangeOfStateCandidate = (m: MuslimCountry) => {
    (m.isShiaMix || m.name == Jordan || m.name == Morocco) && m.isFair && m.isAlly &&
    !(m.isGood || m.isIslamistRule || m.civilWar || m.inRegimeChange)
  }
  val ghostSoldiersCandidate = (m: MuslimCountry) => m.militia > 0 && (m.civilWar || m.inRegimeChange)
  val martyrdomCandidate = (c: Country) => c.totalCells > 0 && 
    !(game.isMuslim(c.name) && game.getMuslim(c.name).isIslamistRule)
  val regionalAlQaedaCandidate = (m: MuslimCountry) => m.name != Iran && m.isUntested
  val talibanResurgentCandidate = (m: MuslimCountry) => (m.civilWar || m.inRegimeChange) && m.totalCells >= 3
  val usAtrocitiesCandidate = (m: MuslimCountry) => m.totalTroops > 0 && (m.civilWar || m.inRegimeChange)

  def parisAttacksPossible: Boolean = {
    val list = UnitedStates :: Canada :: UnitedKingdom :: Benelux :: France :: Schengen
    (game.cellsAvailable > 0 || game.availablePlots.nonEmpty) &&
    (list exists (name => game.getNonMuslim(name).isHard))
  }
  // Countries with cells that are not within two countries with troops/advisor
  def specialForcesCandidates: List[String] = {
    // First find all muslim countries with troops or "Advisors"
    val withForces = (game.muslims filter (m => m.totalTroops > 0 || m.hasMarker("Advisors"))) map (_.name)
    // Next get all countries that contain any cells and filter out the ones are are not 
    // within two of a country with forces.
    countryNames(game.countries filter { country =>
      country.totalCells > 0 && (withForces exists (forces => distance(forces, country.name) <= 2))
    })
  }
  
  // Countries with an awakening maker or adjacent to a country with an awakening marker.
  def faceBookCandidates: List[String] = countryNames(game.muslims filter { m =>
    m.canTakeAwakeningOrReactionMarker &&
    (m.awakening > 0 || (game.adjacentMuslims(m.name) exists (_.awakening > 0)))
  })
  
  val GulfUnionCountries = List(GulfStates, SaudiArabia, Yemen, Jordan, Morocco).sorted
  
  def gulfUnionCandidates: Set[String] = {
    GulfUnionCountries.foldLeft(Set.empty[String]) { (candidates, name) =>
      val gulf = game getMuslim name
      val adj = (getAdjacent(name) filter (x => game.isMuslim(x) && x != Iran)) map game.getMuslim
      candidates ++ ((gulf :: adj) filter (_.canTakeMilitia) map (_.name))
    }
  }
  
  val criticalMiddleUSCandidate = (m: MuslimCountry) => m.isFair && m.resources > 1 &&
                                     (!m.isAlly || m.canTakeAwakeningOrReactionMarker)
  val criticalMiddleJihadistCandidate = (m: MuslimCountry) => m.isPoor && m.resources < 3
  
  def crossBorderSupportPlayable(role: Role) = {
    val cellsOK   = game.cellsAvailable > 0
    val militiaOK = game.militiaAvailable > 0 && (game.getCountries(African) exists (_.canTakeMilitia))
    (role == game.humanRole && (cellsOK || militiaOK)) ||
    (role == Jihadist && cellsOK) ||
    (role == US       && militiaOK)
  }
  
  // Convenience method for adding a card to the deck.
  private def entry(card: Card) = (card.number -> card)
  
  val deck: Map[Int, Card] = Map(
    // ------------------------------------------------------------------------
    entry(new Card(121, "Advisors", US, 1,
      NoRemove, CountryMarker, NoLapsing, NoAutoTrigger, DoesNotAlertPlot,
      (_: Role) => (game.muslims count (_.hasMarker("Advisors"))) < 3 && (game hasMuslim advisorsCandidate)
      ,
      (role: Role) => {
        val candidates = countryNames(game.muslims filter advisorsCandidate)
        val target = if (role == game.humanRole)
          askCountry(s"Advisors in which country: ", candidates)
        else 
          USBot.deployToPriority(candidates).get
        println()
        addEventTarget(target)
        testCountry(target)
        addEventMarkersToCountry(target, "Advisors")
      }
    )),
    // ------------------------------------------------------------------------
    entry(new Card(122, "Backlash", US, 1,
      NoRemove, NoMarker, NoLapsing, NoAutoTrigger, DoesNotAlertPlot,
      (role: Role) => (game.funding > 1 || role != game.botRole) && 
                      (game hasCountry backlashCandidate)
      ,
      (role: Role) => {
        val candidates = countryNames(game.countries filter backlashCandidate)
        if (role == game.humanRole) {
          val target = askCountry(s"Backlash in which country: ", candidates)
          // Pick a random plot in the country
          addEventTarget(target)
          val country = game.getCountry(target)
          val plot :: remaining = shuffle(country.plots)
          val newPlots = plot.copy(backlashed = true) :: remaining
          country match {
            case m: MuslimCountry    => game = game.updateCountry(m.copy(plots = newPlots))
            case n: NonMuslimCountry => game = game.updateCountry(n.copy(plots = newPlots))
          }
          println()
          log(s"Backlash applied to a plot in $target")
        }
        else {
          val plots = for {
            name <- candidates
            country = game.getCountry(name)
            plot <- country.plots
            if !plot.backlashed
          } yield PlotInCountry(plot, country)

          // Pick the highest priority plot among the countries
          val PlotInCountry(plotOnMap, country) = USBot.priorityPlot(plots)
          val (matching, other) = country.plots partition (_ == plotOnMap)
          val newPlots = matching.head.copy(backlashed = true) :: matching.tail ::: other
          addEventTarget(country.name)
          country match {
            case m: MuslimCountry    => game = game.updateCountry(m.copy(plots = newPlots))
            case n: NonMuslimCountry => game = game.updateCountry(n.copy(plots = newPlots))
          }
          println()
          log(s"Backlash applied to a $plotOnMap in ${country.name}")
        }
      }
    )),
    // ------------------------------------------------------------------------
    entry(new Card(123, "Humanitarian Aid", US, 1,
      NoRemove, NoMarker, NoLapsing, NoAutoTrigger, DoesNotAlertPlot,
      (role: Role) => game hasMuslim humanitarianAidCandidate
      ,
      (role: Role) => {
        val candidates = countryNames(game.muslims filter humanitarianAidCandidate)
        val target = if (role == game.humanRole)
          askCountry(s"Humanitarian Aid in which country: ", candidates)
        else
          USBot.markerAlignGovTarget(candidates).get

        println()
        addEventTarget(target)
        addAidMarker(target)
      }
    )),
    // ------------------------------------------------------------------------
    entry(new Card(124, "Pearl Roundabout", US, 1,
      NoRemove, NoMarker, NoLapsing, NoAutoTrigger, DoesNotAlertPlot,
      (role: Role) => globalEventNotInPlay("Bloody Thursday") && lapsingEventNotInPlay("Arab Winter")
      ,
      (role: Role) => {
        println()
        addEventTarget(GulfStates)
        testCountry(GulfStates)
        addAwakeningMarker(GulfStates)
      }
    )),
    // ------------------------------------------------------------------------
    entry(new Card(125, "Peshmerga", US, 1,
      NoRemove, NoMarker, NoLapsing, NoAutoTrigger, DoesNotAlertPlot,
      (role: Role) => (game hasMuslim peshmergaCandidate) && game.militiaAvailable > 0
      ,
      (role: Role) => {
        val candidates = countryNames(game.muslims filter peshmergaCandidate)
        val target = if (role == game.humanRole)
          askCountry(s"Select country: ", candidates)
        else 
          USBot.deployToPriority(candidates).get
        
        addEventTarget(target)        
        println()
        addMilitiaToCountry(target, 2 min game.militiaAvailable)
      }
    )),
    // ------------------------------------------------------------------------
    entry(new Card(126, "Reaper", US, 1,
      NoRemove, NoMarker, NoLapsing,  NoAutoTrigger, DoesNotAlertPlot, AlwaysPlayable,
      (role: Role) => {
        if (role == game.humanRole) {
          if (game.muslims exists (_.totalCells > 0)) {
            val choices = ListMap(
              "remove"  -> "Remove up to 2 cells in one Muslim country",
              "discard" -> "Randomly discard 1 card from the Jihadist hand")
            println("Choose one:")
            askMenu(choices).head match {
              case "discard" => log("Discard the top card in the Jihadist hand")
              case _ =>
              val candidates = game.muslims filter (_.totalCells > 0)
              val target = askCountry("Remove 2 cells in which country? ", countryNames(candidates))
              val (actives, sleepers) = askCells(target, 2)
              addEventTarget(target)
              println()
              removeCellsFromCountry(target, actives, sleepers, addCadre = true)
            }
          }
          else
            log("Discard the top card in the Jihadist hand")
        }
        else {
          val nonIRWith5Cells = countryNames(game.muslims filter (m => !m.isIslamistRule && m.totalCells >= 5))
          val withCells       = countryNames(game.muslims filter (_.totalCells > 0))
          if (nonIRWith5Cells.isEmpty && askYorN(s"Do you ($Jihadist) have any cards in hand (y/n)? ")) {
            println()
            log(s"You ($Jihadist) must discard one random card")
          }
          else {
            val target = if (nonIRWith5Cells.nonEmpty)
              USBot.disruptPriority(nonIRWith5Cells).get
            else
              USBot.disruptPriority(withCells).get
            addEventTarget(target)
            val (actives, sleepers) = USBot.chooseCellsToRemove(target, 2)
            println()
            removeCellsFromCountry(target, actives, sleepers, addCadre = true)
          }
        }
      }
    )),
    // ------------------------------------------------------------------------
    entry(new Card(127, "Reaper", US, 1,
      NoRemove, NoMarker, NoLapsing, NoAutoTrigger, DoesNotAlertPlot,
      (role: Role) => deck(126).eventConditions(role),
      (role: Role) => deck(126).executeEvent(role)
    )),
    // ------------------------------------------------------------------------
    entry(new Card(128, "Reaper", US, 1,
      NoRemove, NoMarker, NoLapsing, NoAutoTrigger, DoesNotAlertPlot,
      (role: Role) => deck(126).eventConditions(role),
      (role: Role) => deck(126).executeEvent(role)
    )),
    // ------------------------------------------------------------------------
    entry(new Card(129, "Special Forces", US, 1,
      NoRemove, NoMarker, NoLapsing, NoAutoTrigger, DoesNotAlertPlot,
      (_ : Role) => specialForcesCandidates.nonEmpty
      ,
      (role: Role) => {
        val (target, (actives, sleepers)) = if (role == game.humanRole) {
          val target = askCountry("Remove cell in which country: ", specialForcesCandidates)
          (target, askCells(target, 1))
        }
        else {
          val target = USBot.disruptPriority(specialForcesCandidates).get
          (target, USBot.chooseCellsToRemove(target, 1))
        }
        println()
        addEventTarget(target)
        removeCellsFromCountry(target, actives, sleepers, addCadre = true)
      }
    )),
    // ------------------------------------------------------------------------
    entry(new Card(130, "Special Forces", US, 1,
      NoRemove, NoMarker, NoLapsing, NoAutoTrigger, DoesNotAlertPlot,
      (role: Role) => deck(129).eventConditions(role),
      (role: Role) => deck(129).executeEvent(role)
    )),
    // ------------------------------------------------------------------------
    entry(new Card(131, "Arab Spring Fallout", US, 2,
      NoRemove, NoMarker, NoLapsing, NoAutoTrigger, DoesNotAlertPlot,
      (role: Role) => lapsingEventNotInPlay("Arab Winter") && (game hasMuslim arabSpringFalloutCandidate)
      ,
      (role: Role) => {
        val candidates = countryNames(game.muslims filter arabSpringFalloutCandidate)
        val targets = if (candidates.size <= 2)
          candidates
        else if (role == game.humanRole) {
          val first  = askCountry("Select first country: ", candidates)
          val second = askCountry("Select second country: ", candidates filterNot (_ == first))
          first::second::Nil
        }
        else
          USBot.multipleTargets(2, candidates, USBot.markerAlignGovTarget)
        
        println()
        targets foreach { target =>
          addEventTarget(target)
          testCountry(target)
          addAwakeningMarker(target)
        }
      }
    )),
    // ------------------------------------------------------------------------
    entry(new Card(132, "Battle of Sirte", US, 2,
      Remove, NoMarker, NoLapsing, NoAutoTrigger, DoesNotAlertPlot,
      (role: Role) => game hasMuslim (_.civilWar)
      ,
      (role: Role) => {
        val candidates = countryNames(game.muslims filter (_.civilWar))
        val (target, (actives, sleepers)) = if (role == game.humanRole) {
          val target = askCountry("Select civil war country: ", candidates)
          (target, askCells(target, 1))
        }
        else {
          // Bot chooses the candidate with the where (totalCells - TandM) is highest.
          val best = USBot.highestCellsMinusTandM(candidates)
          val target = shuffle(candidates).head
          (target, USBot.chooseCellsToRemove(target, 1))
        }

        println()
        addEventTarget(target)
        removeCellsFromCountry(target, actives, sleepers, addCadre = true)
        addMilitiaToCountry(target, game.militiaAvailable min 2)
      }
    )),
    // ------------------------------------------------------------------------
    entry(new Card(133, "Benghazi Falls", US, 2,
      Remove, NoMarker, NoLapsing, NoAutoTrigger, DoesNotAlertPlot,
      (role: Role) => {
       val libya = game.getMuslim(Libya)
       val italy = game.getNonMuslim(Italy)
       // Conditions must be met AND the event must have some effect
       ((libya :: game.adjacentMuslims(Libya)) exists (_.awakening > 0)) &&
       (!libya.civilWar || game.militiaAvailable > 0 || italy.posture != Hard)
      }
      ,
      (role: Role) => {
        println()
        testCountry(Libya)
        addEventTarget(Libya)
        startCivilWar(Libya)
        addMilitiaToCountry(Libya, game.militiaAvailable min 2)
        setCountryPosture(Italy, Hard)
      }
    )),
    // ------------------------------------------------------------------------
    entry(new Card(134, "Civil Resistance", US, 2,
      NoRemove, NoMarker, NoLapsing, NoAutoTrigger, DoesNotAlertPlot,
      (role: Role) => lapsingEventNotInPlay("Arab Winter")
      ,
      (role: Role) => {
        val sunnis = countryNames(game.muslims filter (m=> m.isSunni && m.canTakeAwakeningOrReactionMarker))
        if (sunnis.nonEmpty) {  // This should never happen, but let's be defensive
          val sunniTarget = if (role == game.humanRole)
            askCountry("Select a Sunni country: ", sunnis)
          else 
            USBot.markerAlignGovTarget(sunnis).get

          println()
          addEventTarget(sunniTarget)
          testCountry(sunniTarget)
          addAwakeningMarker(sunniTarget)
        }
        
        if (randomShiaMixList exists (_.canTakeAwakeningOrReactionMarker)) {
          def doRandomShiaMix: Unit = {
            val shiaMix = randomShiaMixCountry
            if (shiaMix.canTakeAwakeningOrReactionMarker) {
              println()
              addEventTarget(shiaMix.name)
              testCountry(shiaMix.name)
              addAwakeningMarker(shiaMix.name)
            }
            else
              doRandomShiaMix
          }
          doRandomShiaMix
        }
      }
    )),
    // ------------------------------------------------------------------------
    entry(new Card(135, "Delta / SEALS", US, 2,
      NoRemove, NoMarker, NoLapsing, NoAutoTrigger, DoesNotAlertPlot,
      (role: Role) => (role == game.humanRole || (game.availablePlots contains PlotWMD) ||
                         askYorN(s"Do you ($Jihadist) have any cards in hand (y/n)? "))
      ,
      (role: Role) => {
        if (role == game.humanRole) {
          val choices = ListMap(
            "reveal"  -> "Reveal all WMD plots and remove one",
            "discard" -> "Randomly discard 1 card from the Jihadist hand")
          println("Choose one:")
          askMenu(choices).head match {
            case "discard" => log("Discard the top card in the Jihadist Bot's hand")
            case _ =>
              val wmdOnMap = for (c <- game.countries; PlotOnMap(PlotWMD, _) <- c.plots)
                yield c.name
              val wmdAvailable = game.availablePlots.sorted takeWhile (_ == PlotWMD)
              if (wmdOnMap.isEmpty && wmdAvailable.isEmpty)
                log("There are no WMD plots on the map or in the available plots box")
              else {
                log(s"WMD plots in the available plots box: ${wmdAvailable.size}")
                val onMapDisplay = if (wmdOnMap.isEmpty) "none" else wmdOnMap.mkString(", ")
                log(s"WMD plots on the map: " + onMapDisplay)
                val target = if (wmdOnMap.isEmpty) "available"
                else if (wmdAvailable.isEmpty)
                  askCountry("Select country with WMD: ", wmdOnMap)
                else
                  askCountry("Select 'available' or country with WMD: ", "available" :: wmdOnMap)
              
                if (target == "available") {
                  log(s"Permanently remove one $PlotWMD from the available plots box")
                  game = game.copy(availablePlots = game.availablePlots.sorted.tail).adjustPrestige(1)
                }
                else {
                  log(s"Permanently remove one $PlotWMD from the $target")
                  addEventTarget(target)
                  game.getCountry(target) match {
                    case m: MuslimCountry =>
                      game = game.updateCountry(m.copy(plots = m.plots.sorted.tail)).adjustPrestige(1)
                    case n: NonMuslimCountry =>
                      game = game.updateCountry(n.copy(plots = n.plots.sorted.tail)).adjustPrestige(1)
                  }
                }
                log(s"Increase prestige by +1 to ${game.prestige} for removing a WMD plot")
              }
          }
        }
        else {
          // See Event Instructions table
          // If there are any WMD plots in the available box, the bot
          // will remove one. Otherwise the US player must discard a random card.
          if (game.availablePlots contains PlotWMD) {
            // The sort order for plots put WMD's first.
            game = game.copy(availablePlots = game.availablePlots.sorted.tail).adjustPrestige(1)
            log(s"Permanently remove one $PlotWMD from the available plots box")
            log(s"Increase prestige by +1 to ${game.prestige} for removing an WMD plot")
          }
          else
            log(s"You ($Jihadist) must discard one random card")
        }
      }
    )),
    // ------------------------------------------------------------------------
    entry(new Card(136, "Factional Infighting", US, 2,
      NoRemove, NoMarker, NoLapsing, NoAutoTrigger, DoesNotAlertPlot, AlwaysPlayable,
      (role: Role) => {
        val candidates = countryNames(game.muslims filter (m => !m.isIslamistRule && m.totalCells > 0))
        if (candidates.nonEmpty) {
          val target = if (role == game.humanRole)
            askCountry("Select a country: ", candidates)
          else 
            USBot.disruptPriority(USBot.highestCellsMinusTandM(candidates)).get

          val m = game.getMuslim(target)
          val (actives, sleepers) = if (m.totalCells <= 2)
            (m.activeCells, m.sleeperCells)
          else if (m.sleeperCells == 0)
            (2, 0)
          else {
            // We have 3 or more cells and at least one is a sleeper
            val sleepers = (m.sleeperCells - 1) min 2  // resereve 1 sleeper to flip to active
            (2 - sleepers, sleepers)
          }
          println()
          addEventTarget(target)
          removeCellsFromCountry(target, actives, sleepers, addCadre = true)
          if (game.getMuslim(target).sleeperCells > 0)
            flipSleeperCells(target, 1)
        }
        // Also, decrease funding by 1 even if no cells affected.
        decreaseFunding(1)
      }
    )),
    // ------------------------------------------------------------------------
    entry(new Card(137, "FMS", US, 2,
      NoRemove, NoMarker, NoLapsing, NoAutoTrigger, DoesNotAlertPlot,
      // Note: No militia allowed in Good countries!
      (role: Role) => game.militiaAvailable > 0 && (game hasMuslim (m => m.isAlly && !m.isGood))
      ,
      (role: Role) => {
        val candidates = countryNames(game.muslims filter (m => m.isAlly && !m.isGood))
        val target = if (role == game.humanRole)
          askCountry("Select country: ", candidates)
        else 
          USBot.deployToPriority(candidates).get

        println()
        addEventTarget(target)
        addMilitiaToCountry(target, game.militiaAvailable min 3)
      }
    )),
    // ------------------------------------------------------------------------
    entry(new Card(138, "Intel Community", US, 2,
      NoRemove, NoMarker, NoLapsing, NoAutoTrigger, DoesNotAlertPlot,
      (role: Role) => role == game.humanRole  // The bot treats this as unplayable
      ,
      (role: Role) => {
        // See Event Instructions table
        log("US player may inspect the Jihadist hand.")
        val cadres = countryNames(game.countries filter (_.hasCadre))
        if (cadres.isEmpty)
          log("No cadres on the map to remove")
        else {
          val target =askCountry("Select country with cadre: ", cadres)
          addEventTarget(target)
          removeCadre(target)
        }
        
        // US player conducts a 1 Op operations.
        println()
        log("US player conducts an operation with 1 Op")
        humanExecuteOperation(1)
        println()
        log("US player may now play an extra card in this action phase")
      }
    )),
    // ------------------------------------------------------------------------
    entry(new Card(139, "Int'l Banking Regime", US, 2,
      NoRemove, NoMarker, NoLapsing, NoAutoTrigger, DoesNotAlertPlot, AlwaysPlayable,
      (role: Role) => {
        val die = getDieRoll(role)
        log(s"Die roll: $die")
        println()
        decreaseFunding((die + 1) / 2)  // Half rounded up
      }
    )),
    // ------------------------------------------------------------------------
    entry(new Card(140, "Maersk Alabama", US, 2,
      Remove, GlobalMarker, NoLapsing, NoAutoTrigger, DoesNotAlertPlot, AlwaysPlayable,
      (role: Role) => {
        if (game.reserves.us < 2) {
          game = game.copy(reserves = game.reserves.copy(us = game.reserves.us + 1))
          log(s"Add 1 Ops value to US reserves")
        }
        else
          log(s"US reserves are already at max of 2 Ops")
        println()
        increasePrestige(1)
        addGlobalEventMarker("Maersk Alabama")
      }
    )),
    // ------------------------------------------------------------------------
    entry(new Card(141, "Malala Yousafzai", US, 2,
      Remove, NoMarker, NoLapsing, NoAutoTrigger, DoesNotAlertPlot,
      (role: Role) => globalEventNotInPlay("3 Cups of Tea") &&  // If not blocked and can have at least one effect
        (game.funding > 1 || game.prestige < 12 || game.getMuslim(Pakistan).canTakeAwakeningOrReactionMarker)
      ,
      (role: Role) => {
        println()
        increasePrestige(1)
        decreaseFunding(1)
        addEventTarget(Pakistan)
        testCountry(Pakistan)
        if (lapsingEventNotInPlay("Arab Winter") && (game getMuslim Pakistan).canTakeAwakeningOrReactionMarker)
          addAwakeningMarker(Pakistan)
      }
    )),
    // ------------------------------------------------------------------------
    entry(new Card(142, "Militia", US, 2,
      NoRemove, NoMarker, NoLapsing, NoAutoTrigger, DoesNotAlertPlot,
      (role: Role) => game hasMuslim (m => (m.civilWar || m.inRegimeChange) && game.militiaAvailable > 0)
      ,
      (role: Role) => {
        val candidates = countryNames(game.muslims filter (m => m.civilWar || m.inRegimeChange))
        val target = if (role == game.humanRole)
          askCountry("Place militia in which country: ", candidates)
        else 
          USBot.deployToPriority(candidates).get

        println()
        addEventTarget(target)
        addMilitiaToCountry(target, game.getMuslim(target).resources min game.militiaAvailable)
      }
    )),
    // ------------------------------------------------------------------------
    entry(new Card(143, "Obama Doctrine", US, 2,
      NoRemove, NoMarker, NoLapsing, NoAutoTrigger, DoesNotAlertPlot,
      (role: Role) => game.usPosture == Soft
      ,
      (role: Role) => {
        if (role == game.humanRole) {
          def item(test: Boolean, x: (String, String)) = if (test) Some(x) else None
          val canAwakening = (game hasMuslim (_.canTakeAwakeningOrReactionMarker)) && lapsingEventNotInPlay("Arab Winter")
          val canAid       = game hasMuslim (_.canTakeAidMarker)
          val items = List(
            item(canAwakening,       "awakening" -> "Place 1 Awakening marker"),
            item(canAid,             "aid"       -> "Place 1 Aid marker"),
            item(game.prestige < 12, "prestige"  -> "+1 Prestige"),
            item(game.funding > 1,   "funding"   -> "-1 Funding"),
            item(true,               "posture"   -> "Select posture of 1 Schengen country"),
            item(true,               "draw"      -> "Select Reaper, Operation New Dawn, or Advisors from discard pile.")
          ).flatten 
          println("Do any 2 of the following:")
          askMenu(ListMap(items:_*), 2, repeatsOK = false) foreach { action =>
            println()
            action match {
              case "awakening" =>
                val target = askCountry("Place awakening marker in which country: ",
                             countryNames(game.muslims filter (_.canTakeAwakeningOrReactionMarker)))
                addEventTarget(target)
                testCountry(target)
                addAwakeningMarker(target)
              case "aid" =>
                val target = askCountry("Place aid marker in which country: ",
                             countryNames(game.muslims filter (_.canTakeAidMarker)))
                addEventTarget(target)
                testCountry(target)
                addAidMarker(target)
              case "prestige" => increasePrestige(1)
              case "funding"  => decreaseFunding(1)
              case "posture" =>
                val target  = askCountry("Select posture of which Schengen country: ", Schengen)
                val posture = askOneOf("New posture (Soft or Hard): ", Seq(Soft, Hard)).get
                addEventTarget(target)
                setCountryPosture(target, posture)
              case _ =>
                log("Select Reaper, Operation New Dawn, or Advisors from discard pile")
            }
          }
        }
        else {
          // See Event Instructions table
          val canAwakening = lapsingEventNotInPlay("Arab Winter") &&
                 (game hasMuslim (_.canTakeAwakeningOrReactionMarker))
          var actions = List(
            if (game.prestige < 12) Some("prestige") else None,
            if (game.funding  >  1) Some("funding") else None,
            if (canAwakening      ) Some("awakening") else None,
            if (game hasMuslim (_.canTakeAidMarker)) Some("aid") else None
          ).flatten take 2
          actions foreach {
            case "prestige" => increasePrestige(1)
            case "funding"  => decreaseFunding(1)
            case "awakening" => 
              val candidates = countryNames(game.muslims filter (_.canTakeAwakeningOrReactionMarker))
              val target = USBot.markerAlignGovTarget(candidates).get
              addEventTarget(target)
              testCountry(target)
              addAwakeningMarker(target)
            case "aid" =>
              val candidates = countryNames(game.muslims filter (_.canTakeAidMarker))
              val target = USBot.markerAlignGovTarget(candidates).get
              addEventTarget(target)
              testCountry(target)
              addAidMarker(target)
          }  
        }
      }
    )),
    // ------------------------------------------------------------------------
    entry(new Card(144, "Operation New Dawn", US, 2,
      NoRemove, NoMarker, NoLapsing, NoAutoTrigger, DoesNotAlertPlot,
      (role: Role) => game.troopsOnMap > 0 && game.militiaAvailable > 0
      ,
      (role: Role) => {
        val candidates = countryNames(game.muslims filter (_.troops > 0))
        val target = if (role == game.humanRole)
          askCountry("Replace troops in which country: ", candidates)
        else {
          val withTroops = game.muslims filter (_.troops > 0)
          val candidates = if (withTroops exists (_.inRegimeChange))
            withTroops filter (_.inRegimeChange)
          else if (withTroops exists (_.hasPlots))
            withTroops filter (_.hasPlots)
          else
            withTroops
          shuffle(countryNames(withTroops)).head
        }
        val numTroops  = 2 min game.getMuslim(target).troops
        val numMilitia = numTroops min game.militiaAvailable
        println()
        addEventTarget(target)
        moveTroops(target, "track", numTroops)
        addMilitiaToCountry(target, numMilitia)      
      }
    )),
    // ------------------------------------------------------------------------
    entry(new Card(145, "Russian Aid", US, 2,
      NoRemove, NoMarker, NoLapsing, NoAutoTrigger, DoesNotAlertPlot,
      (role: Role) => game hasMuslim (_.civilWar)
      ,
      (role: Role) => {
        val candidates = countryNames(game.muslims filter (_.civilWar))
        val target = if (role == game.humanRole)
          askCountry("Place militia and aid in which country: ", candidates)
        else
          USBot.deployToPriority(candidates).get

        println()
        addEventTarget(target)
        addMilitiaToCountry(target, 1 min game.militiaAvailable)
        addAidMarker(target)
      }
    )),
    // ------------------------------------------------------------------------
    entry(new Card(146, "Sharia", US, 2,
      NoRemove, NoMarker, NoLapsing, NoAutoTrigger, DoesNotAlertPlot,
      (role: Role) => game hasMuslim (m => m.besiegedRegime || m.canTakeAwakeningOrReactionMarker)
      ,
      (role: Role) => {
        // Get candidates in this priority order:
        // 1. Muslims with besieged regime markers that can take an awakening marker
        // 2. Muslims with besiged regime markers (cannot take awakening because of Civil War)
        // 3. Muslims that can take an awakening marker.
        val possibles = List(
          game.muslims filter (m => m.besiegedRegime && m.canTakeAwakeningOrReactionMarker),
          game.muslims filter (_.besiegedRegime),
          game.muslims filter (_.canTakeAwakeningOrReactionMarker)
        )
        val candidates = countryNames((possibles dropWhile (_.isEmpty)).head)
        val target = if (role == game.humanRole)
          askCountry("Select country: ", candidates)
        else
          USBot.markerAlignGovTarget(candidates).get

        println()
        addEventTarget(target)
        testCountry(target)
        removeBesiegedRegimeMarker(target)
        if (lapsingEventNotInPlay("Arab Winter") && game.getMuslim(target).canTakeAwakeningOrReactionMarker)
          addAwakeningMarker(target)
        else if (game.getMuslim(target).isGood)
          log(s"Cannot add an awakening marker to $target because it has Good governance")
        else
          log(s"Cannot add an awakening marker to $target because it is in Civil War")
      }
    )),
    // ------------------------------------------------------------------------
    entry(new Card(147, "Strike Eagle", US, 2,
      NoRemove, NoMarker, NoLapsing, NoAutoTrigger, DoesNotAlertPlot,
      (role: Role) => game hasMuslim strikeEagleCandidate
      ,
      (role: Role) => {
        val candidates = countryNames(game.muslims filter strikeEagleCandidate)
        val target = if (role == game.humanRole)
          askCountry("Select country: ", candidates)
        else
          USBot.disruptPriority(candidates).get

        println()
        addEventTarget(target)
        removeCachedWMD(target, 1)
        if (game.cellsAvailable > 0)
          addSleeperCellsToCountry(Israel, 1)
        increaseFunding(1)
      }
    )),
    // ------------------------------------------------------------------------
    entry(new Card(148, "Tahrir Square", US, 2,
      NoRemove, NoMarker, NoLapsing, NoAutoTrigger, DoesNotAlertPlot,
      (role: Role) => lapsingEventNotInPlay("Arab Winter") &&
           ((game getMuslim Egypt).canTakeAwakeningOrReactionMarker || (game hasMuslim tahrirCandidate))
      ,
      (role: Role) => {
        val candidates = countryNames(game.muslims filter tahrirCandidate)
        
        if (game.getMuslim(Egypt).canTakeAwakeningOrReactionMarker) {
          addEventTarget(Egypt)
          testCountry(Egypt)
          addAwakeningMarker(Egypt, 2)
          addReactionMarker(Egypt)
        }
        
        if (candidates.nonEmpty) {
          val target = if (role == game.humanRole)
            askCountry("Place 1 awakening marker in which country: ", candidates)
          else
            USBot.markerAlignGovTarget(candidates).get

          addEventTarget(target)
          testCountry(target)
          addAwakeningMarker(target)
        }
      }
    )),
    // ------------------------------------------------------------------------
    entry(new Card(149, "UN Nation Building", US, 2,
      NoRemove, NoMarker, NoLapsing, NoAutoTrigger, DoesNotAlertPlot,
      (role: Role) => game hasMuslim unNationBuildingCandidate
      ,
      (role: Role) => {
        val candidates = countryNames(game.muslims filter unNationBuildingCandidate)
        val (target, die) = if (role == game.humanRole)
          (askCountry("Select country: ", candidates), humanDieRoll("Enter War of Ideas die roll: "))
        else 
          (USBot.markerAlignGovTarget(candidates).get, dieRoll)
        
        addEventTarget(target)
        addAidMarker(target)
        performWarOfIdeas(target, die, ignoreGwotPenalty = true)
      }
    )),
    // ------------------------------------------------------------------------
    entry(new Card(150, "UNSCR 1973", US, 2,
      NoRemove, CountryMarker, NoLapsing, NoAutoTrigger, DoesNotAlertPlot,
      (role: Role) => game hasMuslim unscr1973Candidate
      ,
      (role: Role) => {
        val candidates = countryNames(game.muslims filter unscr1973Candidate)
        val target = if (role == game.humanRole)
          askCountry("Place UNSCR 1973 in which country: ", candidates)
        else {
           val best = USBot.followOpPFlowchart(game.muslims filter unscr1973Candidate, USBot.HighestResourcePriority::Nil)
           USBot.disruptPriority(countryNames(best)).get
        }
        
        addEventTarget(target)
        val m = game.getMuslim(target)
        // If the target already contains the marker, then
        // we only remove a cell.
        val sameCountry = m.hasMarker("UNSCR 1973")

        if (!sameCountry) {
          // If marker is already on the map, remove it first.
          game.muslims find (_.hasMarker("UNSCR 1973")) foreach { c =>
            removeEventMarkersFromCountry(c.name, "UNSCR 1973")
          }
        }

        if (m.totalCells > 0) {
          val (actives, sleepers) = if (role == game.humanRole)
            askCells(target, 1, sleeperFocus = true)
          else 
            USBot.chooseCellsToRemove(target, 1)
          removeCellsFromCountry(target, actives, sleepers, addCadre = true)
        }

        if (!sameCountry)
          addEventMarkersToCountry(target, "UNSCR 1973")
      }
    )),
    // ------------------------------------------------------------------------
    entry(new Card(151, "UNSCR 2118", US, 2,
      Remove, NoMarker, NoLapsing, NoAutoTrigger, DoesNotAlertPlot,
      (role: Role) => {
        val syria = game.getMuslim(Syria)
        !syria.isUntested && syria.wmdCache > 0
      }
      ,
      (role: Role) => {
        addEventTarget(Syria)
        val syria = game.getMuslim(Syria)
        removeCachedWMD(Syria, if (syria.isAlly) syria.wmdCache else 1)
      }
    )),
    // ------------------------------------------------------------------------
    entry(new Card(152, "Congress Acts", US, 3,
      NoRemove, NoMarker, NoLapsing, NoAutoTrigger, DoesNotAlertPlot,
      (role: Role) => if (role == game.humanRole) {
        globalEventInPlay("Sequestration") || (
           game.troopsAvailable >= 6 && (game hasMuslim (m => m.civilWar)))
      }
      else {
        game.numIslamistRule > 2 && game.usPosture == Soft && 
          (game hasMuslim (m => m.civilWar && m.totalCells > m.totalTroopsAndMilitia))
      }
      ,
      (role: Role) => {
        removeGlobalEventMarker("Sequestration")
        returnSequestrationTroopsToAvailable()
        
        if (role == game.humanRole) {
          val candidates = countryNames(game.muslims filter (m => m.civilWar))
          if (game.troopsAvailable >= 6 && candidates.nonEmpty) {
            val target = askCountry("Select country: ", candidates)
            val numTroops = askInt("Deploy how many troops from the track", 6, game.troopsAvailable, Some(6))
            addEventTarget(target)
            performRegimeChange("track", target, numTroops)
          }
        }
        else {
          val candidates = countryNames(game.muslims filter (m => m.civilWar && m.totalCells > m.totalTroopsAndMilitia))
          val target = shuffle(USBot.highestCellsMinusTandM(candidates)).head
          addEventTarget(target)
          performRegimeChange("track", target, 6)  // Bot always uses exatly 6 troops for regime change
        }
      }
    )),
    // ------------------------------------------------------------------------
    entry(new Card(153, "Facebook", US, 3,
      NoRemove, NoMarker, NoLapsing, NoAutoTrigger, DoesNotAlertPlot,
      (role: Role) => globalEventInPlay("Smartphones") &&
                      lapsingEventNotInPlay("Arab Winter") &&
                      faceBookCandidates.nonEmpty
      ,
      (role: Role) => {
        val candidates = faceBookCandidates
        val targets = if (candidates.size < 4)
          candidates
        else if (role == game.humanRole) {
          // Choose three targets
          def nextTarget(num: Int, possibles: List[String]): List[String] = {
            if (num > 3) Nil
            else {
              val target = askCountry(s"${ordinal(num)} country: ", possibles)
              target :: nextTarget(num + 1, possibles filterNot(_ == target))
            }
          }
          nextTarget(1, candidates)
        }
        else
          USBot.multipleTargets(3, candidates, USBot.markerAlignGovTarget)

        addEventTarget(targets:_*)
        targets foreach (target => addAwakeningMarker(target))
      }
    )),
    // ------------------------------------------------------------------------
    entry(new Card(154, "Facebook", US, 3,
      NoRemove, NoMarker, NoLapsing, NoAutoTrigger, DoesNotAlertPlot,
      (role: Role) => deck(153).eventConditions(role),
      (role: Role) => deck(153).executeEvent(role)
    )),
    // ------------------------------------------------------------------------
    entry(new Card(155, "Fracking", US, 3,
      NoRemove, GlobalMarker, NoLapsing, NoAutoTrigger, DoesNotAlertPlot, AlwaysPlayable,
      (role: Role) => {
        addGlobalEventMarker("Fracking")
        rollPrestige()
        log("US player draws a card")
      }
    )),
    // ------------------------------------------------------------------------
    entry(new Card(156, "Gulf Union", US, 3,
      NoRemove, NoMarker, NoLapsing, NoAutoTrigger, DoesNotAlertPlot,
      (role: Role) => if (role == game.humanRole) {
        game.militiaAvailable > 0 || 
             (GulfUnionCountries exists (name => game.getMuslim(name).militia > 0))
      }
      else {
        game.militiaAvailable > 0 &&
        ((gulfUnionCandidates map game.getMuslim) exists (m => m.totalCells > m.totalTroopsAndMilitia))
      }
      ,
      (role: Role) => {
        val maxMilitia = 4 min game.militiaAvailable
        if (role == game.humanRole) {
          // Card allow placing militia or respositioning militia in the Gulf Union countries.
          val existingMilitia = (GulfUnionCountries exists (name => game.getMuslim(name).militia > 0))
          val actions = List(
            if (game.militiaAvailable > 0) Some("place")      else None,
            if (existingMilitia)           Some("reposition") else None
          ).flatten
          val choices = ListMap(
            "place"      -> "Place up to 4 militia in one Gulf Union (or adjacent) country",
            "reposition" -> "Repositon militia in Gulf Union countries")
          val placeCandidates = gulfUnionCandidates.toList.sorted
          val action = if (actions.size == 1) actions.head 
          else {
            println("Choose one:")
            askMenu(choices).head
          }
          action match {
            case "place" =>
              val target = askCountry("Place militia in which country: ", placeCandidates)
              val num    = askInt(s"Place how many militia in $target", 1, maxMilitia, Some(maxMilitia))
              addEventTarget(target)
              testCountry(target)
              addMilitiaToCountry(target, num)
              
            case _ => // Reposition militia with the Gulf Union countries
              val totalMilitia = GulfUnionCountries.foldLeft(0) { (sum, name) =>
                sum + game.getMuslim(name).militia
              }
              println(s"There are a total of $totalMilitia militia in the Gulf Union countries.")
              println("Assume that we start by taking all of those militia off of the map.")
              println("You will be prompted with the name of each Gulf Union country one by one.")
              println("Specify the number of militia that you would like in each country:")
              case class GulfUnion(name: String, newMilitia: Int) {
                val muslim = game.getMuslim(name)
                def hasLess = newMilitia < muslim.militia
                def noChange = newMilitia == muslim.militia
              }
              def nextCountry(members: List[String], remaining: Int): List[GulfUnion] = {
                members match {
                  case Nil                     => Nil
                  case x::Nil                  => GulfUnion(x, remaining) :: Nil
                  case x::xs if remaining == 0 => GulfUnion(x, 0) :: nextCountry(xs, 0)
                  case x::xs                   =>
                    val num = askInt(s"How many militia in $x", 0, remaining)
                    GulfUnion(x, num) :: nextCountry(xs, remaining - num)
                }
              }
              val placements = nextCountry(GulfUnionCountries, maxMilitia) filterNot (_.noChange)
              if (placements.isEmpty)
                log("No change to the position of the existing militia in the Gulf Union")
              else {
                for (p <- placements; if p.hasLess) {
                  addEventTarget(p.name)
                  testCountry(p.name)
                  val m = game.getMuslim(p.name) // Get fresh copy after testCountry()
                  game = game.updateCountry(m.copy(militia = p.newMilitia))
                  log(s"Remove ${p.muslim.militia - p.newMilitia} militia from ${p.name}")
                }
                for (p <- placements; if !p.hasLess) {
                  addEventTarget(p.name)
                  testCountry(p.name)
                  val m = game.getMuslim(p.name) // Get fresh copy after testCountry()
                  game = game.updateCountry(m.copy(militia = p.newMilitia))
                  log(s"Add ${p.newMilitia - p.muslim.militia} militia to ${p.name}")
                }
              }
          } 
        }
        else {
          // See Event Instructions table
          val candidates = ((gulfUnionCandidates map game.getMuslim) filter (m => m.totalCells > m.totalTroopsAndMilitia))
          val target = USBot.deployToPriority(USBot.highestCellsMinusTandM(countryNames(candidates.toList))).get
          addEventTarget(target)
          testCountry(target)
          addMilitiaToCountry(target, maxMilitia)
        }
      }
    )),
    // ------------------------------------------------------------------------
    entry(new Card(157, "Limited Deployment", US, 3,
      NoRemove, NoMarker, NoLapsing, NoAutoTrigger, DoesNotAlertPlot,
      (role: Role) => game.usPosture == Hard && (game hasMuslim (_.civilWar))
      ,
      (role: Role) => {
        val candidates = countryNames(game.muslims filter (_.civilWar))
        val (target, adjacent) = if (role == game.humanRole) {
          val t = askCountry("Select country: ", candidates)
          val adjacents = getAdjacentMuslims(t) filter (n => game.getMuslim(n).canTakeAwakeningOrReactionMarker)
          if (adjacents.nonEmpty)
            (t, Some(askCountry("Place awakening marker in which adjacent country: ", adjacents)))
          else
            (t, None)
        }
        else {
          val t = USBot.deployToPriority(USBot.highestCellsMinusTandM(candidates)).get
          val a = (getAdjacentMuslims(t) filter (n => game.getMuslim(n).canTakeAwakeningOrReactionMarker))
          val bestAdjacent = USBot.markerAlignGovTarget(a)
          (t, bestAdjacent)
        }
        
        addEventTarget(target)
        moveTroops("track", target, 2 min game.troopsAvailable)
        addAidMarker(target)
        if (lapsingEventNotInPlay("Arab Winter"))
          adjacent foreach { m => addAwakeningMarker(m) }
      }
    )),
    // ------------------------------------------------------------------------
    entry(new Card(158, "Mass Turnout", US, 3,
      NoRemove, NoMarker, NoLapsing, NoAutoTrigger, DoesNotAlertPlot,
      (role: Role) => game hasMuslim massTurnoutCandidate
      ,
      (role: Role) => {
        val candidates = countryNames(game.muslims filter massTurnoutCandidate)
        val target = if (role == game.humanRole)
          askCountry("Select regime change country: ", candidates)
        else
          USBot.markerAlignGovTarget(candidates).get
        
        addEventTarget(target)
        improveGovernance(target)
      }
    )),
    // ------------------------------------------------------------------------
    entry(new Card(159, "NATO", US, 3,
      NoRemove, CountryMarker, NoLapsing, NoAutoTrigger, DoesNotAlertPlot,
      (role: Role) => game.gwotPenalty == 0 && (game hasMuslim (m => m.inRegimeChange || m.civilWar))
      ,
      (role: Role) => {
        val candidates = countryNames(game.muslims filter (m => m.inRegimeChange || m.civilWar))
        val target = if (role == game.humanRole)
          askCountry("Select country for NATO: ", candidates)
        else
          USBot.deployToPriority(candidates).get
        
        addEventTarget(target)
        addAidMarker(target)
        (game.muslims find (_.hasMarker("NATO")) map (_.name)) match {
          case Some(`target`) =>
            log(s"NATO marker remains in $target")
          case Some(current) =>
            removeEventMarkersFromCountry(current, "NATO")
            addEventMarkersToCountry(target, "NATO")
          case None =>
            addEventMarkersToCountry(target, "NATO")
        }
      }
    )),
    // ------------------------------------------------------------------------
    entry(new Card(160, "Operation Neptune Spear", US, 3,
      NoRemove, NoMarker, NoLapsing, NoAutoTrigger, DoesNotAlertPlot,
      (role: Role) => role == game.humanRole || askYorN("Is one of the indicated cards in the discard pile (y/n)? ")
      ,
      (role: Role) => {
        val cards = List("Ayman al-Zawahiri", "Abu Bakr al-Baghdadi", "Abu Sayyaf (ISIL)",
                         "Jihadi John", "Osama bin Ladin")
        log("US player takes one of the following cards from the discard pile:")
        log(orList(cards))
        // See Event Instructions table
        if (role == game.botRole)
          log("The Bot selects the card nearest the bottom of the discard pile")
      }
    )),
    // ------------------------------------------------------------------------
    entry(new Card(161, "PRISM", US, 3,
      NoRemove, NoMarker, NoLapsing, NoAutoTrigger, 
      (_, _) => game hasCountry (_.hasPlots)  // Alerts all plots on the map
      ,
      (role: Role) => role == game.humanRole         || 
                      (game hasCountry (_.hasPlots)) ||
                      game.sleeperCellsOnMap >= 5
      ,
      (role: Role) => {
        val actions = List(
          if (game.sleeperCellsOnMap > 0) Some("activate") else None,
          if (game.alertTargets.nonEmpty) Some("alert") else None
        ).flatten
        val choices = ListMap(
          "activate" -> "Activate half (rounded up) of all sleeper cells on the map",
          "alert"    -> "Alert all plots on the map"
        )
          
        if (role == game.humanRole) {
          if (actions.nonEmpty) {
            val action = if (actions.size == 1)
              actions.head
            else {
              println("Choose one:")
              askMenu(choices).head
            }

            if (action == "activate") {
              val numToFlip = (game.sleeperCellsOnMap + 1) / 2  // half rounded up
              // Ask which cells to activate
              val withSleepers = for (c <- game.countries; if c.sleeperCells > 0)
                yield MapItem(c.name, c.sleeperCells)
              println(s"Activate a total of ${amountOf(numToFlip, "sleeper cell")}")
              val toFlip = askMapItems(withSleepers.sortBy(_.country), numToFlip, "sleeper cell")
              
              // It is possible that the same country was chosen multiple times
              // Consolidate them so we do 1 flip per country.
              def flipNextBatch(remaining: List[MapItem]): Unit = {
                if (remaining.nonEmpty) {
                  val name = remaining.head.country
                  val (batch, next) = remaining.partition(_.country == name)
                  val total = batch.foldLeft(0) { (sum, x) => sum + x.num }
                  addEventTarget(name)
                  flipSleeperCells(name, total)
                  flipNextBatch(next)
                }
              }
              flipNextBatch(toFlip)
            }
            else
              for (c <- game.countries; p <- c.plots)  {// Alert all plots on the map
                addEventTarget(c.name)
                performAlert(c.name, humanPickPlotToAlert(c.name))
              }
          }
          // Even of neither action above was possible.
          decreasePrestige(1)
        }
        else {
          // See Event Instructions table
          if (game hasCountry (_.hasPlots))
            for (c <- game.countries; p <- c.plots)  {// Alert all plots on the map
              addEventTarget(c.name)
              performAlert(c.name, humanPickPlotToAlert(c.name))
            }
          else {
            val candidates = countryNames(game.countries filter (_.sleeperCells > 0))
            def flipNext(numLeft: Int, targets: List[String]): Unit = {
              if (numLeft > 0 && targets.nonEmpty) {
                var name = USBot.disruptPriority(targets).get
                val c = game getCountry name
                addEventTarget(name)
                val num = numLeft min c.sleeperCells
                flipSleeperCells(name, num)
                flipNext(numLeft - num, targets filterNot (_ == name))
              }
            }
            
            flipNext((game.sleeperCellsOnMap + 1) / 2, candidates)
          }
        }
      }
    )),
    // ------------------------------------------------------------------------
    entry(new Card(162, "SCAF", US, 3,
      NoRemove, NoMarker, NoLapsing, NoAutoTrigger, DoesNotAlertPlot,
      (role: Role) => (role == game.humanRole && (game hasMuslim scafCandidate)) ||
                      (role == game.botRole && (game hasMuslim (m => !m.isAlly && scafCandidate(m))))
      ,
      (role: Role) => {
        val candidates = countryNames(game.muslims filter scafCandidate)
        val target = if (role == game.humanRole)
          askCountry("Select country: ", candidates)
        else 
          USBot.scafTarget(candidates).get

        addEventTarget(target)
        val m = game.getMuslim(target)
        shiftAlignment(target, Ally)
        if (m.isPoor)
          improveGovernance(target, 1)
        removeCellsFromCountry(target, m.activeCells, m.sleeperCells, addCadre = true)
        if (lapsingEventNotInPlay("Arab Winter"))
          addReactionMarker(target, m.totalCells)
      }
    )),
    // ------------------------------------------------------------------------
    entry(new Card(163, "Status Quo", US, 3,
      NoRemove, NoMarker, NoLapsing, NoAutoTrigger, DoesNotAlertPlot,
      (role: Role) => (role == game.humanRole && (game hasMuslim statusQuoCandidate)) ||
                      (role == game.botRole && (game hasMuslim (m => !m.isAlly && statusQuoCandidate(m))))
      ,  
      (role: Role) => {
        val candidates = countryNames(game.muslims filter statusQuoCandidate)
        val target = if (role == game.humanRole)
          askCountry("Select country: ", candidates)
        else 
          USBot.statusQuoTarget(candidates).get  // See Event Instructions table
        
        addEventTarget(target)
        endRegimeChange(target)
        shiftAlignment(target, Ally)
      }
    )),
    // ------------------------------------------------------------------------
    entry(new Card(164, "Bloody Thursday", Jihadist, 1,
      NoRemove, GlobalMarker, NoLapsing, NoAutoTrigger, DoesNotAlertPlot,
      (role: Role) => globalEventNotInPlay("Bloody Thursday") ||
                      (game hasMuslim (_.awakening > 0))
      ,
      (role: Role) => {
        addGlobalEventMarker("Bloody Thursday")
        val candidates = countryNames(game.muslims filter (_.awakening > 0))
        if (candidates.nonEmpty) {
          val target = if (role == game.humanRole)
            askCountry("Select country with awakening marker: ", candidates)
          else
            JihadistBot.markerAlignGovTarget(candidates).get

          addEventTarget(target)
          removeAwakeningMarker(target)
        }
      }
    )),
    // ------------------------------------------------------------------------
    entry(new Card(165, "Coup", Jihadist, 1,
      NoRemove, NoMarker, NoLapsing, NoAutoTrigger, DoesNotAlertPlot,
      (role: Role) => (role == game.humanRole && (game hasMuslim coupCandidate)) ||
                      (role == game.botRole && (game hasMuslim (m => !m.isIslamistRule && coupCandidate(m))))
      ,
      (role: Role) => {
        val target = if (role == game.humanRole) {
          val candidates = countryNames(game.muslims filter coupCandidate)
          askCountry("Select country: ", candidates)
        }
        else {
          val candidates = countryNames(game.muslims filter (m => !m.isIslamistRule && coupCandidate(m)))
          JihadistBot.goodPriority(candidates).get
        }
        
        addEventTarget(target)
        startCivilWar(target)
        addBesiegedRegimeMarker(target)
      }
    )),
    // ------------------------------------------------------------------------
    entry(new Card(166, "Ferguson", Jihadist, 1,
      NoRemove, NoMarker, Lapsing, NoAutoTrigger, DoesNotAlertPlot, AlwaysPlayable,
      (role: Role) => {
        log("Jihadist player my block 1 US associated event played later this turn.")
        if (role == game.botRole)
          log("The Jihadist Bot will cancel the NEXT US associated event played by the US")
      }
    )),
    // ------------------------------------------------------------------------
    entry(new Card(167, "Houthi Rebels", Jihadist, 1,
      Remove, NoMarker, NoLapsing, NoAutoTrigger, DoesNotAlertPlot,
      (role: Role) => {
        game.getMuslim(Yemen).isPoor &&
        (game.isNonMuslim(Iran) || !game.getMuslim(Iran).isAlly) &&
        !(game.getMuslim(Yemen).civilWar && game.cellsAvailable == 0)
      }
      ,
      (role: Role) => {
        addEventTarget(Yemen)
        addSleeperCellsToCountry(Yemen, 2 min game.cellsAvailable)
        startCivilWar(Yemen)
      }
    )),
    // ------------------------------------------------------------------------
    entry(new Card(168, "IEDs", Jihadist, 1,
      NoRemove, NoMarker, NoLapsing, NoAutoTrigger, DoesNotAlertPlot,
      (role: Role) => game hasMuslim (m =>
        (m.inRegimeChange || m.civilWar) && m.totalCells > 0 && m.totalTroops > 0
      )
      ,  
      (role: Role) => {
        log("US player randomly discards one card")
      }
    )),
    // ------------------------------------------------------------------------
    entry(new Card(169, "Islamic Maghreb", Jihadist, 1,
      NoRemove, NoMarker, Lapsing, NoAutoTrigger, DoesNotAlertPlot,
      (role: Role) => if (role == game.botRole)
        (game hasMuslim islamicMaghrebCandidate) && (game.funding < 8 || game.cellsAvailable > 0)
      else  
        (game hasMuslim islamicMaghrebCandidate) && (game.funding < 9 || game.cellsAvailable > 0)
      ,  
      (role: Role) => {
        val (target, action) = if (role == game.humanRole) {
          val candidates = countryNames(game.muslims filter islamicMaghrebCandidate)
          val t = askCountry("Select country: ", candidates)
          val two = (game.getMuslim(t).civilWar || game.isCaliphateMember(t))
          val choices = ListMap(
            "cells" -> (if (two) "Place cells" else "Place a cell"),
            "funding" -> "Increase funding")
          (t, askMenu(choices).head)
        }
        else {
          val maghrebs = game.muslims filter islamicMaghrebCandidate
          val better = maghrebs filter (m => m.civilWar || game.isCaliphateMember(m.name))
          val candidates = countryNames(if (better.nonEmpty) better else maghrebs)
          val t = JihadistBot.travelToTarget(candidates).get
          val action = if (game.funding < 8) "funding" else "cells"
          (t, action)
        }
        
        addEventTarget(target)
        val num = if (game.getMuslim(target).civilWar || game.isCaliphateMember(target)) 2 else 1
        if (action == "funding")
          increaseFunding(num)
        else
          addSleeperCellsToCountry(target, num min game.cellsAvailable)
        rollCountryPosture(Serbia)
        log("Travel to/within Schengen countries requires a roll for the rest this turn")
      }
    )),
    // ------------------------------------------------------------------------
    entry(new Card(170, "Theft of State", Jihadist, 1,
      NoRemove, NoMarker, NoLapsing, NoAutoTrigger, DoesNotAlertPlot,
      (role: Role) => game hasMuslim theftOfStateCandidate
      ,
      (role: Role) => {
        val candidates = countryNames(game.muslims filter theftOfStateCandidate)
        val target = if (role == game.humanRole)
          askCountry("Select country: ", candidates)
        else
          JihadistBot.markerAlignGovTarget(candidates).get

        addEventTarget(target)
        removeAwakeningMarker(target)
        if (lapsingEventNotInPlay("Arab Winter"))
          addReactionMarker(target)
      }
    )),
    // ------------------------------------------------------------------------
    entry(new Card(171, "Abu Ghraib Jail Break", Jihadist, 2,
      Remove, NoMarker, NoLapsing, NoAutoTrigger, DoesNotAlertPlot, AlwaysPlayable,
      (role: Role) => {
        addEventTarget(Iraq)
        testCountry(Iraq)
        addActiveCellsToCountry(Iraq, 1 min game.cellsAvailable)
        if (lapsingEventNotInPlay("Arab Winter"))
          addReactionMarker(Iraq)
        decreasePrestige(1)
      }
    )),
    // ------------------------------------------------------------------------
    entry(new Card(172, "Al-Shabaab", Jihadist, 2,
      NoRemove, NoMarker, NoLapsing, NoAutoTrigger, DoesNotAlertPlot, AlwaysPlayable,
      (role: Role) => {
        if (role == game.humanRole) {
          val candidates = Somalia :: getAdjacent(Somalia).sorted
          val reactionCandidates = candidates filter game.isMuslim filter { name =>
            game.getMuslim(name).canTakeAwakeningOrReactionMarker
          }
          val besiegeCandidates = candidates filter game.isMuslim filter { name =>
            !(game.getMuslim(name).canTakeAwakeningOrReactionMarker ||
              game.getMuslim(name).besiegedRegime)
          }
          val canReaction = reactionCandidates.nonEmpty && lapsingEventNotInPlay("Arab Winter")
          val canCell     = game.cellsAvailable > 0
          val canPlot1    = game.availablePlots contains Plot1
          val canPlot2    = game.availablePlots contains Plot2
          val canBesiege  = besiegeCandidates.nonEmpty
          def item(test: Boolean, x: (String, String)) = if (test) Some(x) else None
          val items = List(
            item(canReaction,"reaction" -> "Place 1 Reaction marker"),
            item(canCell,    "cell"     -> "Place 1 cell"),
            item(canPlot1,   "plot1"    -> "Place a level 1 plot"),
            item(canPlot2,   "plot2"    -> "Place a level 2 plot"),
            item(canBesiege, "besiege"  -> "Place a besieged regime marker"),
            item(true,       "draw"     -> "Select Pirates, Boko Haram, or Islamic Maghreb from discard pile")
          ).flatten 
          println("Do any 2 of the following:")
          askMenu(ListMap(items:_*), 2, repeatsOK = false) foreach { action =>
            println()
            action match {
              case "reaction" =>
                val target = askCountry("Place reaction marker in which country: ", reactionCandidates)
                addEventTarget(target)
                testCountry(target)
                addReactionMarker(target)
              case "cell" =>
                val target = askCountry("Place a cell in which country: ", candidates)
                addEventTarget(target)
                testCountry(target)
                addSleeperCellsToCountry(target, 1)
              case "plot1" =>
                val target = askCountry("Place a level 1 plot in which country: ", candidates)
                addEventTarget(target)
                testCountry(target)
                addAvailablePlotToCountry(target, Plot1)
              case "plot2" =>
                val target = askCountry("Place a level 2 plot in which country: ", candidates)
                addEventTarget(target)
                testCountry(target)
                addAvailablePlotToCountry(target, Plot2)
              case "besiege"  =>
                val target = askCountry("Place besieged regime marker in which country: ", besiegeCandidates)
                addEventTarget(target)
                testCountry(target)
                addBesiegedRegimeMarker(target)
              case _ =>
                log("Select Pirates, Boko Haram, or Islamic Maghreb from discard pile")
            }
          }
        }
        else {
          // See Event Instructions table
          val candidates = Somalia::Sudan::Yemen::Nil
          val muslims    = candidates map game.getMuslim filter (!_.isIslamistRule)
          val besiegeTarget  = countryNames(muslims filter (!_.besiegedRegime)).headOption
          val reactionTarget = if (lapsingEventNotInPlay("Arab Winter"))
            None
          else
            countryNames(muslims filter (_.canTakeAwakeningOrReactionMarker)).headOption
          val cellTarget     = if (game.cellsAvailable > 0)
            muslims.headOption map (_.name) orElse Some(KenyaTanzania)
          else
            None
          val plotTarget = if (game.availablePlots exists (p => p == Plot1 || p == Plot2))
            muslims.headOption map (_.name) orElse Some(KenyaTanzania)
          else
            None
          val actions = List(
            besiegeTarget  map (_ => "besiege"),
            cellTarget     map (_ => "cell"),
            reactionTarget map (_ => "reaction"),
            plotTarget     map (_ => "plot"),
            Some("draw")
          ).flatten take 2
          actions foreach { action =>
            println()
            action match {
              case "besiege"  =>
                addEventTarget(besiegeTarget.get)
                testCountry(besiegeTarget.get)
                addBesiegedRegimeMarker(besiegeTarget.get)
              case "cell" =>
                addEventTarget(cellTarget.get)
                testCountry(cellTarget.get)
                addSleeperCellsToCountry(cellTarget.get, 1)
              case "reaction" =>
                addEventTarget(reactionTarget.get)
                testCountry(reactionTarget.get)
                addReactionMarker(reactionTarget.get)
              case "plot" =>
                val plot = (game.availablePlots.sorted dropWhile (p => p != Plot1 && p != Plot2)).head
                addEventTarget(plotTarget.get)
                testCountry(plotTarget.get)
                addAvailablePlotToCountry(plotTarget.get, plot)
              case _ =>
                log("Select Pirates, Boko Haram, or Islamic Maghreb from discard pile")
                log("and place it on top of the the Jihadist's hand of cards")
            }
          }
        }
      }
    )),
    // ------------------------------------------------------------------------
    entry(new Card(173, "Arab Winter", Jihadist, 2,
      NoRemove, NoMarker, Lapsing, NoAutoTrigger, DoesNotAlertPlot, AlwaysPlayable,
      (role: Role) => {
        val candidates = countryNames(game.muslims filter (_.awakening > 0))
        if (candidates.isEmpty)
          log(s"There are no reaction markers on the map")
        else {
          val targets = if (candidates.size == 1)
            candidates
          else if (role == game.humanRole) {
            val target1 = askCountry("Select first country: ", candidates)
            val target2 = askCountry("Select second country: ", candidates filterNot (_ == target1))
            (target1::target2::Nil)
          }
          else
            JihadistBot.multipleTargets(2, candidates, JihadistBot.markerAlignGovTarget)

          addEventTarget(targets:_*)
          targets foreach (removeAwakeningMarker(_))
          log("No more Awakening/Reaction markers may be placed this turn.")
        }
      }
    )),
    // ------------------------------------------------------------------------
    entry(new Card(174, "Boston Marathon", Jihadist, 2,
      Remove, NoMarker, NoLapsing, NoAutoTrigger, DoesNotAlertPlot,
      (role: Role) => (role == game.humanRole || game.gwotPenalty == 0)
      ,
      (role: Role) => {
        // See Event Instructions table
        rollUSPosture()
        increaseFunding(1)
        addEventTarget(Caucasus)
        rollCountryPosture(Caucasus)
      }
    )),
    // ------------------------------------------------------------------------
    entry(new Card(175, "Censorship", Jihadist, 2,
      NoRemove, GlobalMarker, NoLapsing, NoAutoTrigger, DoesNotAlertPlot, AlwaysPlayable,
      (role: Role) => {
        val candidates = countryNames(game.muslims filter (_.awakening > 0))
        if (candidates.isEmpty)
          log(s"There are no reaction markers on the map")
        else {
          val target = if (role == game.humanRole) 
            askCountry("Select country: ", candidates)
          else
            JihadistBot.markerAlignGovTarget(candidates).get

          addEventTarget(target)
          val m = game.getMuslim(target)
          removeAwakeningMarker(target, 2 min m.awakening)
        }
        
        removeGlobalEventMarker("Smartphones")
        addGlobalEventMarker("Censorship")
      }
    )),
    // ------------------------------------------------------------------------
    entry(new Card(176, "Change of State", Jihadist, 2,
      NoRemove, NoMarker, NoLapsing, NoAutoTrigger, DoesNotAlertPlot,
      (role: Role) => if (role == game.botRole)
        game hasMuslim botChangeOfStateCandidate
      else
        game hasMuslim changeOfStateCandidate
      ,
      (role: Role) => {
        val candidates = if (role == game.botRole)
          countryNames(game.muslims filter botChangeOfStateCandidate)
        else
          countryNames(game.muslims filter changeOfStateCandidate)
        
        val target = if (role == game.humanRole) 
          askCountry("Select country: ", candidates)
        else
          JihadistBot.changeOfStateTarget(candidates).get

        addEventTarget(target)
        // Strip the country of all markers and make it Untested
        setCountryToUntested(target)
      }
    )),
    // ------------------------------------------------------------------------
    entry(new Card(177, "Gaza Rockets", Jihadist, 2,
      NoRemove, NoMarker, NoLapsing, NoAutoTrigger, DoesNotAlertPlot,
      (role: Role) => (game.availablePlots contains Plot1) &&
                      (role == game.humanRole || game.funding < 9) // Bot unplayable if funding == 9
      ,
      (role: Role) => {
        // See Event Instructions table
        val num = 2 min (game.availablePlots count (_ == Plot1))
        addEventTarget(Israel)
        for (i <- 1 to num)
          addAvailablePlotToCountry(Israel, Plot1)
        decreaseFunding(1)
      }
    )),
    // ------------------------------------------------------------------------
    entry(new Card(178, "Ghost Soldiers", Jihadist, 2,
      NoRemove, NoMarker, NoLapsing, NoAutoTrigger, DoesNotAlertPlot,
      (role: Role) => game hasMuslim ghostSoldiersCandidate
      ,
      (role: Role) => {
        val candidates = countryNames(game.muslims filter ghostSoldiersCandidate)
        val target = if (role == game.humanRole) 
          askCountry("Select country: ", candidates)
        else 
          JihadistBot.troopsMilitiaTarget(candidates).get

        addEventTarget(target)
        val m = game.getMuslim(target)
        removeMilitiaFromCountry(target, (m.militia + 1) / 2) // Half rounded up
      }
    )),
    // ------------------------------------------------------------------------
    entry(new Card(179, "Korean Crisis", Jihadist, 2,
      NoRemove, NoMarker, Lapsing, NoAutoTrigger, DoesNotAlertPlot,
      (role: Role) => (game.troopsAvailable + game.troopsOnMap) > 0
      ,
      (role: Role) => {
        import JihadistBot.troopsToTakeOffMap
        // Take troops from available if possible, otherwise we must 
        // ask the user where to take them from.
        val numToRemove  = 2 min (game.troopsAvailable + game.troopsOnMap)
        val numFromTrack = numToRemove min game.troopsAvailable
        val numFromMap   = numToRemove - numFromTrack
        
        val countries = if (numFromMap == 0)
          Nil
        else if (role == game.humanRole) {
          val targets = game.muslims filter (_.troops > 0) map (m => MapItem(m.name, m.troops))
          println(s"Select ${amountOf(numFromMap, "troop")} from the map to remove")
          askMapItems(targets.sortBy(_.country), numFromMap, "troop")
        }
        else
          troopsToTakeOffMap(numFromMap, countryNames(game.muslims filter (_.troops > 0)))
        
        for (MapItem(name, num) <- MapItem("track", numFromTrack) :: countries) {
          if (name != "track")
            addEventTarget(name)
          takeTroopsOffMap(name, num)
        }
        addEventTarget(China)
        setCountryPosture(China, oppositePosture(game.usPosture))
      }
    )),
    // ------------------------------------------------------------------------
    entry(new Card(180, "Mosul Central Bank", Jihadist, 2,
      Remove, NoMarker, NoLapsing, NoAutoTrigger, DoesNotAlertPlot,
      (role: Role) => game hasMuslim (m => m.civilWar && m.totalCells > 0)
      ,
      (role: Role) => increaseFunding(2)
    )),
    // ------------------------------------------------------------------------
    entry(new Card(181, "NPT Safeguards Ignored", Jihadist, 2,
      NoRemove, NoMarker, NoLapsing, NoAutoTrigger, DoesNotAlertPlot,  
      (role: Role) => globalEventNotInPlay("Trade Embargo (US)") &&
                      game.getCountry(Iran).totalCells > 0 &&
                      game.getCountry(Iran).wmdCache > 0
      ,
      (role: Role) => {
        addEventTarget(Iran)
        val die = getDieRoll(role)
        val success = die < 4
        log(s"Die roll: $die")
        if (success) {
          log("Success")
          moveWMDCachedToAvailable(Iran)
          removeCardFromGame(181)
        }
        else {
          log("Failure")
          val c = game.getCountry(Iran)
          if (c.activeCells > 0)
            removeActiveCellsFromCountry(Iran, 1, addCadre = true)
          else
            removeSleeperCellsFromCountry(Iran, 1, addCadre = true)
        }
      }
    )),
    // ------------------------------------------------------------------------
    entry(new Card(182, "Paris Attacks", Jihadist, 2,
      NoRemove, NoMarker, NoLapsing, NoAutoTrigger, DoesNotAlertPlot,
      (role: Role) => parisAttacksPossible
      ,
      (role: Role) => {
        // Note: There is a slight chance that the Bot could execute this event,
        // and get a black die roll for only plots or only cells and there are 
        // plot/cells available.
        val countries = List(UnitedStates, Canada, UnitedKingdom, Benelux, France)
        val validSchengen = for (s <- Schengen; if game.getNonMuslim(s).isHard) yield s
        println()
        val tanDie     = getDieRoll(role, "Enter tan die: ")
        val blackDie   = getDieRoll(role, "Enter black die: ")
        val (plots, activeCells) = blackDie match {
          case 1 => (Plot1::Plot2::Nil, 0)
          case 2 => (Plot3::Nil, 2)
          case 3 => (Plot2::Nil, 1)
          case 4 => (Plot2::Nil, 0)
          case 5 => (Plot1::Nil, 1)
          case _ => (Plot1::Nil, 0)
        }
        def isHard(name: String) = game.getNonMuslim(name).isHard
        def getTarget(list: List[String]): Option[String] = {
          list.drop(tanDie - 1) match {
            case Nil if validSchengen.nonEmpty => Some("Schengen")
            case Nil                           => None
            case x::xs if isHard(x)            => Some(x)
            case x::xs                         => getTarget(xs)
          }
        }
        def placePlots(name: String, plotList: List[Plot]): Unit = plotList match {
          case Nil =>
          case p::ps =>
            if (game.availablePlots contains p)
              addAvailablePlotToCountry(name, p)
            placePlots(name, ps)
        }
        
        log(s"Tan die: $tanDie,  Black die: $blackDie")
        val target = getTarget(countries drop (tanDie - 1)) map {
          case "Schengen" if role == game.humanRole =>
            askCountry("Choose a Hard Schengen country: ", validSchengen)
          case "Schengen" => JihadistBot.plotTarget(validSchengen).get
          case name => name
        }
        
        target match {
          case None =>
            log("No Hard country was selected for the Paris Attacks event")
          case Some(name) =>
            addEventTarget(name)
            log(s"$name is the target of the Paris Attacks event")
            placePlots(name, plots)
            addActiveCellsToCountry(name, activeCells min game.cellsAvailable)
        }
      }
    )),
    // ------------------------------------------------------------------------
    entry(new Card(183, "Pirates", Jihadist, 2,
      Remove, GlobalMarker, NoLapsing, NoAutoTrigger, DoesNotAlertPlot,
      (role: Role) => globalEventNotInPlay("Maersk Alabama") && piratesConditionsInEffect
      ,
      (role: Role) => addGlobalEventMarker("Pirates")
    )),
    // ------------------------------------------------------------------------
    entry(new Card(184, "Sequestration", Jihadist, 2,
      Remove, GlobalMarker, NoLapsing, NoAutoTrigger, DoesNotAlertPlot,
      (role: Role) => game.usPosture == Soft
      ,
      (role: Role) => {
        import JihadistBot.troopsToTakeOffMap
        // Take troops from available if possible, otherwise we must 
        // ask the user where to take them from.
        val numToRemove  = 3 min (game.troopsAvailable + game.troopsOnMap)
        val numFromTrack = numToRemove min game.troopsAvailable
        val numFromMap   = numToRemove - numFromTrack
        val countries = if (numFromMap == 0)
          Nil
        else if (role == game.humanRole) {
          val targets = game.muslims filter (_.troops > 0) map (m => MapItem(m.name, m.troops))
          println(s"Select ${amountOf(numFromMap, "troop")} from the map to remove")
          askMapItems(targets.sortBy(_.country), numFromMap, "troop")
        }
        else
          troopsToTakeOffMap(numFromMap, countryNames(game.muslims filter (_.troops > 0)))
        
        log("US player draws one card from the discard pile")
        log("Must draw \"Obama Doctrine\" if it is available")
        // See Event Instructions table
        if (game.botRole == US) {
          log("US Bot will draw \"Obama Doctrine\", or \"Congress Acts\", or")
          log("randomly among the US associated cards with the highest Ops")
        }
        
        for (MapItem(name, num) <- MapItem("track", numFromTrack) :: countries) {
          if (name != "track")
            addEventTarget(name)
          takeTroopsOffMap(name, num)
        }
        game = game.copy(eventParams = game.eventParams.copy(sequestrationTroops = true))
        addGlobalEventMarker("Sequestration")
      }
    )),
    // ------------------------------------------------------------------------
    entry(new Card(185, "al-Maliki", Jihadist, 3,
      Remove, NoMarker, NoLapsing, NoAutoTrigger, DoesNotAlertPlot,
      (role: Role) => game hasMuslim (_.totalTroops > 0)
      ,
      (role: Role) => {
        val candidates = countryNames(game.muslims filter (_.totalTroops > 0))
        val target = if (role == game.humanRole)
          askCountry("Select country: ", candidates)
        else {
          // See Event Instructions table
          val candidates = {
            val eligible = game.muslims filter (_.totalTroops > 0)
            countryNames(if (eligible exists (_.isAlly))
              eligible filter (_.isAlly)
            else if (eligible exists (_.isNeutral))
              eligible filter (_.isNeutral)
            else
              eligible)
          }
          JihadistBot.troopsMilitiaTarget(candidates).get
        }

        addEventTarget(target)
        val m = game.getMuslim(target)
        moveTroops(target, "track", m.troops)
        removeAllTroopsMarkers(target)
        shiftAlignment(target, Neutral)
        addAidMarker(target)
        endRegimeChange(target)
      }
    )),
    // ------------------------------------------------------------------------
    entry(new Card(186, "Boko Haram", Jihadist, 3,
      NoRemove, NoMarker, NoLapsing, NoAutoTrigger, DoesNotAlertPlot,
      (role: Role) => (game.availablePlots exists (p => p == Plot2 || p == Plot3)) ||
                      game.cellsAvailable > 0
      ,
      (role: Role) => {
        addEventTarget(Nigeria)
        testCountry(Nigeria)
        if (role == game.humanRole) {
          val havePlots = game.availablePlots exists (p => p == Plot2 || p == Plot3)
          val haveCells = game.cellsAvailable > 0
          val choices = List(
            if (havePlots) Some("plot"  -> "Place a level 2 or level 3 Plot in Nigeria") else None,
            if (haveCells) Some("cells" -> "Place up to 3 cells in Nigeria") else None
          ).flatten
          if (choices.size > 1)
            println("Choose one of:")
          askMenu(ListMap(choices:_*)).head match {
            case "plot" =>
              val options = Seq(Plot2, Plot3) filter game.availablePlots.contains map {
                case Plot2 => "2"
                case Plot3 => "3"
              }
              val plotNum = if (options.size == 1)
                options.head
              else
                askOneOf("What level plot? (2 or 3) ", options).get
              plotNum match {
                case "2" => addAvailablePlotToCountry(Nigeria, Plot2)
                case "3" => addAvailablePlotToCountry(Nigeria, Plot3)
              }
              
            case "cells" =>
              val maxCells = 3 min game.cellsAvailable
              val num = askInt("Place how many cells", 1, maxCells, Some(maxCells))
              addSleeperCellsToCountry(Nigeria, num)
              if (num == 3 && canDeclareCaliphate(Nigeria) && askDeclareCaliphate(Nigeria))
                declareCaliphate(Nigeria)
          }
          log()
          log("Jihadist player may return Boko Haram to hand by")
          log("discarding a non-US associated 3 Ops card")
        }
        else {
          // See Event Instructions table
          // We know that either cells are available or a Plot2/3 is available from
          // the eventConditions().
          val plot = (game.availablePlots.sorted filter (p => p == Plot2 || p == Plot3)).headOption
          val placeCells = if (plot.isEmpty) true
          else if (game.cellsAvailable == 0) false
          else if (game isMuslim Nigeria) true  // Muslim priority to cells
          else false                            // non-Muslim priority to plot
          if (placeCells)  {
            val numCells = 3 min game.cellsAvailable
            addSleeperCellsToCountry(Nigeria, numCells)
            if (numCells == 3 && JihadistBot.willDeclareCaliphate(Nigeria))
              declareCaliphate(Nigeria)
          }
          else
            addAvailablePlotToCountry(Nigeria, plot.get)
        }
      }
    )),
    // ------------------------------------------------------------------------
    entry(new Card(187, "Foreign Fighters", Jihadist, 3,
      NoRemove, NoMarker, NoLapsing, NoAutoTrigger, DoesNotAlertPlot,
      (role: Role) => game hasMuslim (m => m.inRegimeChange || m.civilWar)
      ,
      (role: Role) => {
        val candidates = countryNames(game.muslims filter (m => m.inRegimeChange || m.civilWar))
        val target = if (role == game.humanRole)
          askCountry("Select country: ", candidates)
        else
          JihadistBot.recruitTarget(candidates).get

        addEventTarget(target)
        val m = game.getMuslim(target)
        val numCells = 5 min game.cellsAvailable
        addSleeperCellsToCountry(target, numCells)
        if (m.aidMarkers > 0)
          removeAidMarker(target, 1)
        else if (!m.besiegedRegime)
          addBesiegedRegimeMarker(target)
        
        if (numCells >= 3 &&
            canDeclareCaliphate(target) &&
            ((role == game.humanRole && askDeclareCaliphate(target)) ||
             (role == game.botRole && JihadistBot.willDeclareCaliphate(target))))
          declareCaliphate(target)
      }
    )),
    // ------------------------------------------------------------------------
    entry(new Card(188, "ISIL", Jihadist, 3,
      Remove, NoMarker, NoLapsing, NoAutoTrigger, DoesNotAlertPlot, AlwaysPlayable,
      (role: Role) => {
        def canCivilWar(m: MuslimCountry) =
          !m.civilWar       && 
          !m.isIslamistRule &&
          !m.isGood         &&
          m.totalTroopsAndMilitia <= m.totalCells
          
        for (name <- Seq(Syria, Iraq); if canCivilWar(game.getMuslim(name))) {
          addEventTarget(name)
          startCivilWar(name)
        }
        decreasePrestige(1)
      }
    )),
    // ------------------------------------------------------------------------
    entry(new Card(189, "Jihadist Videos", Jihadist, 3,
      NoRemove, NoMarker, NoLapsing, NoAutoTrigger, DoesNotAlertPlot, AlwaysPlayable,
      (role: Role) => {
        var targets = if (role == game.humanRole)
          askCountries(3, countryNames(game.countries filter (_.totalCells == 0)))
        else {
          // See Event Instructions table
          val eligible = countryNames(game.countries filter (m => m.totalCells == 0 && !m.isIslamistRule))
          
          if (eligible contains UnitedStates)
            UnitedStates :: JihadistBot.multipleTargets(2, eligible filterNot (_ == UnitedStates), 
                                                JihadistBot.recruitTarget)
          else 
            JihadistBot.multipleTargets(3, eligible, JihadistBot.recruitTarget)
        }
        
        val numCells = if (role == game.botRole && game.jihadistIdeology(Potent)) {
          log(s"$Jihadist Bot with Potent Ideology places two cells for each recruit success")
          2
        }
        else
          1
        // Process all of the targets
        for (target <- targets) {
          addEventTarget(target)
          testCountry(target)
          
          val c = game.getCountry(target)
          if (game.cellsAvailable > 0) {
            val cells = numCells min game.cellsAvailable
            if (c.autoRecruit) {
              log(s"Recruit in $target succeeds automatically")
              addSleeperCellsToCountry(target, cells)
            }
            else {
              val die = getDieRoll(role)
              log(s"Die roll: $die")
              if (die <= c.governance) {
                log(s"Recruit in $target succeeds with a die roll of $die")
                addSleeperCellsToCountry(target, cells)
              }
              else {
                log(s"Recruit in $target fails with a die roll of $die")
                addCadre(target)
              }
            }
          }
          else
            addCadre(target)
        }
      }
    )),
    // ------------------------------------------------------------------------
    entry(new Card(190, "Martyrdom Operation", Jihadist, 3,
      NoRemove, NoMarker, NoLapsing, NoAutoTrigger, DoesNotAlertPlot,
      (role: Role) => game.availablePlots.nonEmpty && (game hasCountry martyrdomCandidate)
      ,
      (role: Role) => {
        val candidates = countryNames(game.countries filter martyrdomCandidate)
        val (target, active, plots) = if (role == game.humanRole) {
          val target = askCountry("Select country: ", candidates)
          val (actives, _) = askCells(target, 1)
          (target, actives > 0, askAvailablePlots(2, ops = 3))
        }
        else {
          // See Event Instructions table
          val target = JihadistBot.plotTarget(candidates).get
          addEventTarget(target)
          val active = (game getCountry target).activeCells > 0
          (target, active, shuffle(game.availablePlots) take 2)
        }
        
        addEventTarget(target)
        if (active)
          removeActiveCellsFromCountry(target, 1, addCadre = false)
        else
          removeSleeperCellsFromCountry(target, 1, addCadre = false)
        for (plot <- plots)
          addAvailablePlotToCountry(target, plot)
      }
    )),
    // ------------------------------------------------------------------------
    entry(new Card(191, "Muslim Brotherhood", Jihadist, 3,
      Remove, NoMarker, NoLapsing, NoAutoTrigger, DoesNotAlertPlot,
      (role: Role) => lapsingEventNotInPlay("Arab Winter")
      ,
      (role: Role) => {
        val candidates = countryNames(game.muslims filter (_.canTakeAwakeningOrReactionMarker))
        
        val other2 = if (role == game.humanRole)
          askCountries(2, candidates)
        else 
          JihadistBot.multipleTargets(2, candidates, JihadistBot.markerAlignGovTarget)
        
        val targets = if (game.getMuslim(Egypt).canTakeAwakeningOrReactionMarker)
          Egypt :: other2
        else {
          log("Egypt cannot currently take a reaction marker.")
          other2
        }
        for (target <- targets) {
          addEventTarget(target)
          testCountry(target)
          addReactionMarker(target)
        }
      }
    )),
    // ------------------------------------------------------------------------
    entry(new Card(192, "Quagmire", Jihadist, 3,
      NoRemove, NoMarker, NoLapsing, NoAutoTrigger, DoesNotAlertPlot,
      (role: Role) => (game.prestigeLevel == Medium || game.prestigeLevel == Low) &&
                      (game hasMuslim (m => m.inRegimeChange && m.totalCells > 0))
      ,
      (role: Role) => {
        val opponent = if (role == Jihadist) US else Jihadist
        log("US randomly discards 2 cards")
        log("Playable Jihadist events on the discards are triggered")
        
        def nextDiscard(num: Int): List[Int] = {
          if (num > 2)
            Nil
          else {
            val prompt = s"Number of the ${ordinal(num)} discard (or blank if none) "
            askCardNumber(prompt) match {
              case None         => Nil
              case Some(cardNo) =>  cardNo :: nextDiscard(num + 1)
            }
          }
        }
        
        for (n <- nextDiscard(1); card = deck(n))
          if (card.eventWillTrigger(Jihadist))
            performCardEvent(card, Jihadist, triggered = true)
        
        setUSPosture(Soft)
      }
    )),
    // ------------------------------------------------------------------------
    entry(new Card(193, "Regional al-Qaeda", Jihadist, 3,
      NoRemove, NoMarker, NoLapsing, NoAutoTrigger, DoesNotAlertPlot,
      (role: Role) => game.cellsAvailable > 0 && (game.muslims count regionalAlQaedaCandidate) >= 2
      ,
      (role: Role) => {
        val candidates = countryNames(game.muslims filter regionalAlQaedaCandidate)
        val maxPer = if (game hasMuslim (_.isIslamistRule)) 2 else 1
        case class Target(name: String, cells: Int)
        val targets = if (role == game.humanRole) {
          if (game.cellsAvailable == 1) {
            val name = askCountry("Select country: ", candidates)
            Target(name, 1)::Nil
          }
          else if (maxPer == 2 && game.cellsAvailable < 4) {
            println(s"There are only ${game.cellsAvailable} available cells")
            val name1 = askCountry("Select 1st country: ", candidates)
            val num1  = askInt(s"Place how many cells in $name1", 1, 2)
            val remain = game.cellsAvailable - num1
            if (remain == 0)
              Target(name1, num1)::Nil
            else {
              val name2 = askCountry("Select 2nd country: ", candidates filterNot (_ == name1))
              Target(name1, num1):: Target(name2, remain)::Nil
            }
          }
          else {
            val name1 = askCountry("Select 1st country: ", candidates)
            val name2 = askCountry("Select 2nd country: ", candidates filterNot (_ == name1))
            Target(name1, maxPer):: Target(name2, maxPer)::Nil
          }
        }
        else {
          def nextTarget(available: Int, targets: List[String]): List[Target] = targets match {
            case Nil => Nil
            case t::ts => 
              val n = maxPer min available
              Target(t, n) :: nextTarget(available - n, ts)
          }
          var names = JihadistBot.multipleTargets(2, candidates, JihadistBot.recruitTarget)
          nextTarget(game.cellsAvailable, names)  
        }
        
        for (Target(name, num) <- targets) {
          addEventTarget(name)
          testCountry(name)
          addSleeperCellsToCountry(name, num)
        }
      }
    )),
    // ------------------------------------------------------------------------
    entry(new Card(194, "Snowden", Jihadist, 3,
      Remove, NoMarker, NoLapsing, NoAutoTrigger, DoesNotAlertPlot,
      (role: Role) => game.prestige > 1                                    ||
                      game.usPosture != game.getNonMuslim(Russia).posture  ||
                      game.usPosture != game.getNonMuslim(Germany).posture
      ,
      (role: Role) => {
        decreasePrestige(2)
        addEventTarget(Russia)
        addEventTarget(Germany)
        setCountryPosture(Russia, oppositePosture(game.usPosture))
        setCountryPosture(Germany, oppositePosture(game.usPosture))
      }
    )),
    // ------------------------------------------------------------------------
    entry(new Card(195, "Taliban Resurgent", Jihadist, 3,
      NoRemove, NoMarker, NoLapsing, NoAutoTrigger, DoesNotAlertPlot,
      (role: Role) => if (role == game.humanRole)
          game hasMuslim talibanResurgentCandidate
      else {
        val candidates = countryNames(game.muslims filter talibanResurgentCandidate)
        JihadistBot.talibanResurgentTarget(candidates).nonEmpty
      }
      ,
      (role: Role) => {
        // See Event Instructions table
        val candidates = countryNames(game.muslims filter talibanResurgentCandidate)
        val (target, plots, (actives, sleepers)) = if (role == game.humanRole) {
          val name = askCountry("Select country: ", candidates)
          val plots = askPlots(game.availablePlots filterNot (_ == PlotWMD), 2)
          println("Choose cells to remove:")
          (name, plots, askCells(name, 3, sleeperFocus = false))
        }
        else {
          val name = JihadistBot.talibanResurgentTarget(candidates).get
          val plots = (game.availablePlots.sorted filterNot (_ == PlotWMD)) take 2
          (name, plots, JihadistBot.chooseCellsToRemove(name, 3))
        }
        addEventTarget(target)
        removeCellsFromCountry(target, actives, sleepers, addCadre = false)
        plots foreach { p => addAvailablePlotToCountry(target, p) }
        performJihads(JihadTarget(target, 2, 0)::Nil, ignoreFailures = true)
      }
    )),
    // ------------------------------------------------------------------------
    entry(new Card(196, "Training Camps", Jihadist, 3,
      NoRemove, CountryMarker, NoLapsing, NoAutoTrigger, DoesNotAlertPlot,
      (role: Role) => {
        if (role == game.humanRole)
          game hasMuslim (m => !(m.isUntested || m.isGood))
        else // See Event Instructions table
          game hasMuslim (m => !(m.isUntested || m.isGood) && !m.autoRecruit)
      }
      ,
      (role: Role) => {
        val target = if (role == game.humanRole) {
          val candidates = countryNames(game.muslims filter (m => !(m.isUntested || m.isGood)))
          askCountry("Place Training Camps in which country: ", candidates)
        }
        else {
          // See Event Instructions table
          val candidates = 
            countryNames(game.muslims filter (m => !(m.isUntested || m.isGood && !m.autoRecruit)))
          JihadistBot.recruitTarget(candidates).get
        }
        println()
        val priorCapacity = game.trainingCampCapacity
        game.trainingCamp foreach { name => 
          removeEventMarkersFromCountry(name, "Training Camps")
        }
        addEventTarget(target)
        addEventMarkersToCountry(target, "Training Camps")
        updateTrainingCampCapacity(priorCapacity)
        val cellsToAdd = game.cellsAvailable min 2
        addSleeperCellsToCountry(target, cellsToAdd)
      }
    )),
    // ------------------------------------------------------------------------
    entry(new Card(197, "Unconfirmed", Jihadist, 3,
      Remove, NoMarker, NoLapsing, NoAutoTrigger, DoesNotAlertPlot, AlwaysPlayable,
      (role: Role) => {
        // See Event Instructions table
        if (role == game.humanRole)
          log("Draw one of the indicated cards from the discard pile.")
        else
          log(s"The $Jihadist Bot draws the candidate card nearest the top of the dicard pile")
        decreasePrestige(1)
      }
    )),
    // ------------------------------------------------------------------------
    entry(new Card(198, "US Atrocities", Jihadist, 3,
      NoRemove, NoMarker, NoLapsing, NoAutoTrigger, DoesNotAlertPlot,
      (role: Role) => game hasMuslim usAtrocitiesCandidate
      ,
      (role: Role) => {
        val alignCandidates = countryNames(game.muslims filter usAtrocitiesCandidate)
        val postureCandidates = countryNames(game.nonMuslims filter (n => n.isUntested && !n.isSchengen))
        var (alignTarget, postureTarget) = if (role == game.humanRole) {
          val at = askCountry(s"Select country for alignment shift: ", alignCandidates)
          val pt = if (postureCandidates.isEmpty)
            None
          else {
            val name = askCountry(s"Select posture of which country: ", postureCandidates)
            val pos = askOneOf(s"Select posture for $name: ", Hard::Soft::Nil).get
            Some(name, pos)
          }
          (at, pt)
        }
        else {
          val at = JihadistBot.markerAlignGovTarget(alignCandidates).get
          val pt = if (postureCandidates.isEmpty)
            None
          else
            Some(shuffle(postureCandidates).head, oppositePosture(game.usPosture))
          (at, pt)
        }
        
        val newAlign = if ((game getMuslim alignTarget).alignment == Ally)
          Neutral
        else
          Adversary
        addEventTarget(alignTarget)
        shiftAlignment(alignTarget, newAlign)
        postureTarget foreach {
          case (name, posture) => 
            addEventTarget(name)
            setCountryPosture(name, posture)
        }
        decreasePrestige(1)
      }
    )),
    // ------------------------------------------------------------------------
    entry(new Card(199, "US Consulate Attacked", Jihadist, 3,
      NoRemove, NoMarker, Lapsing, NoAutoTrigger, DoesNotAlertPlot, AlwaysPlayable,
      (role: Role) => {
        decreasePrestige(2)
        if (role == game.humanRole)
          log(s"Discard the top card of the $US hand")
        else
          log(s"You ($US) must discard one random card")
        log("If US Elections is played later this turn, US Posture switches automatically")
      }
    )),
    // ------------------------------------------------------------------------
    entry(new Card(200, "Critical Middle", Unassociated, 1,
      NoRemove, NoMarker, NoLapsing, NoAutoTrigger, DoesNotAlertPlot,
      (role: Role) => role match {
        case US       => game hasMuslim criticalMiddleUSCandidate
        case Jihadist => game hasMuslim criticalMiddleJihadistCandidate
      }
      ,
      (role: Role) => {
        // See Event Instructions table
        val usCandidates = countryNames(game.muslims filter criticalMiddleUSCandidate)
        val jiCandidates = countryNames(game.muslims filter criticalMiddleJihadistCandidate)
        val (target, action, from) = (role, role == game.humanRole) match {
          case (US, true) =>
            val target = askCountry("Select country: ", usCandidates)
            val m = game getMuslim target
            val choices = List(
              if (m.canTakeAwakeningOrReactionMarker) Some("awakening" -> "Place an awakening marker") else None,
              if (!m.isAlly) Some("shiftAlly" -> "Shift alignment towards Ally") else None
            ).flatten
            val action = if (choices.isEmpty) None
            else askMenu(ListMap(choices:_*)).headOption
            (target, action, Nil)
              
          case (Jihadist, true) =>
            val target = askCountry("Select country: ", jiCandidates)
            val m = game getMuslim target
            val choices = List(
              if (game.cellsAvailable > 0) Some("cells" -> "Place cells") else None,
              if (!m.isAdversary) Some("shiftAdversary" -> "Shift alignment towards Adversary") else None
            ).flatten
            val action = if (choices.isEmpty) None
            else askMenu(ListMap(choices:_*)).headOption
            val from = if (action == Some("cells")) {
              val xs = (game.countries filter (c => c.name != target && c.totalCells > 0) 
                                      map (c => MapItem(c.name, c.totalCells)))
              val items = MapItem("track", game.cellsAvailable) :: xs
              println(s"Select 2 cells to place in $target:")
              askMapItems(items, 2, "cell")
            }
            else
              Nil
              (target, action, from)
            
          case (US, false) =>
            USBot.criticalMiddleShiftPossibilities(usCandidates) match {
              case Nil => (USBot.markerAlignGovTarget(usCandidates).get, Some("awakening"), Nil)
              case xs  => (USBot.markerAlignGovTarget(xs).get, Some("shiftAlly"), Nil)
            }
          case (Jihadist, false) =>
            JihadistBot.criticalMiddleShiftPossibilities(jiCandidates) match {
              case Nil =>
                val target = JihadistBot.travelToTarget(jiCandidates).get
                def nextFrom(countries: List[String], remaining: Int): List[MapItem] =
                  if (remaining == 0)
                    Nil
                  else if (game.cellsAvailable > 0) {
                    val n = remaining min game.cellsAvailable
                    MapItem("track", n)::nextFrom(countries, remaining - n)
                  }
                  else if (countries.isEmpty)
                    Nil
                  else {
                    val name = JihadistBot.travelFromTarget(target, countries).get
                    val n = remaining min (game getCountry name).totalCells
                    MapItem(name, n)::nextFrom(countries filterNot (_ == name), remaining - n)
                  }

                val fromCandidates = countryNames(game.countries filter (c => c.name != target && c.totalCells > 0))
                (target, Some("cells"), nextFrom(fromCandidates, 2))
                
              case xs  => (JihadistBot.markerAlignGovTarget(xs).get, Some("shiftAdversary"), Nil)
            }
        }
        
        addEventTarget(target)
        val m = game getMuslim target
        action match {
          case None                   => log(s"$target is already and Ally and cannot take an awakening marker")
          case Some("awakening")      => addAwakeningMarker(target)
          case Some("shiftAlly")      => shiftAlignment(target, if (m.isAdversary) Neutral else Ally)
          case Some("shiftAdversary") => shiftAlignment(target, if (m.isAlly) Neutral else Adversary)
          case _ => // place cells
            from foreach {
              case MapItem("track", n) => addSleeperCellsToCountry(target, n)
              case MapItem(name, n)    =>
                val actives  = n min (game getCountry name).activeCells
                val sleepers = n - actives
                moveCellsBetweenCountries(name, target, actives, true)
                moveCellsBetweenCountries(name, target, sleepers, false)
            }
        }
        log()
        log("IMPORTANT!")
        log("Place the \"Critical Middle\" card in the approximate middle of the draw pile")
      }
    )),
    // ------------------------------------------------------------------------
    entry(new Card(201, "Cross Border Support", Unassociated, 1,
      NoRemove, NoMarker, NoLapsing, NoAutoTrigger, DoesNotAlertPlot,
      (role: Role) => crossBorderSupportPlayable(role)
      ,
      (role: Role) => {
        // See Event Instructions table
        val cellsOK   = game.cellsAvailable > 0
        val militiaOK = game.militiaAvailable > 0 && (game.getCountries(African) exists (_.canTakeMilitia))
        val (target, action) = if (role == game.humanRole) {
          val choices = List(
            if (militiaOK) Some("militia" -> "Place militia") else None,
            if (cellsOK)   Some("cells" -> "Place cells")     else None
          ).flatten
          println("What would you like to do:")
          val action = askMenu(ListMap(choices:_*)).head
          val candidates = if (action == "militia")
            countryNames(game.getCountries(African) filter (_.canTakeMilitia))
          else
            countryNames(game.getCountries(African))
          val name = askCountry("Select African country: ", candidates)
          (name, action)
        }
        else if (role == US) {
          val candidates = USBot.highestCellsMinusTandM(countryNames(game.getCountries(African) filter (_.canTakeMilitia)))
          val target = shuffle(candidates).head
          (target, "militia")
        }
        else {
          val target = JihadistBot.recruitTravelToPriority(African).get
          (target, "cells")
        }
        
        addEventTarget(target)
        val bump = (target == Mali || target == Nigeria)
        if (action == "militia") {
          val num = (if (bump) 2 else 1) min game.militiaAvailable
          addMilitiaToCountry(target, num)
        }
        else {
          // Can possibly declare Caliphate (only Mali or Muslim Nigeria)
          val num = (if (bump) 3 else 2) min game.cellsAvailable
          addSleeperCellsToCountry(target, num)
          if (num == 3 &&
              canDeclareCaliphate(target) &&
              ((role == game.humanRole && askDeclareCaliphate(target)) ||
               (role == game.botRole && JihadistBot.willDeclareCaliphate(target))))
            declareCaliphate(target)
        }
      }
    )),
    // ------------------------------------------------------------------------
    entry(new Card(202, "Cyber Warfare", Unassociated, 1,
      NoRemove, NoMarker, NoLapsing, NoAutoTrigger, DoesNotAlertPlot, AlwaysPlayable,
      (role: Role) => {
        // See Event Instructions table
      }
    )),
    // ------------------------------------------------------------------------
    entry(new Card(203, "Day of Rage", Unassociated, 1,
      Remove, NoMarker, NoLapsing, NoAutoTrigger, DoesNotAlertPlot, AlwaysPlayable,
      (role: Role) => ()
    )),
    // ------------------------------------------------------------------------
    entry(new Card(204, "Ebola Scare", Unassociated, 1,
      Remove, NoMarker, USLapsing, NoAutoTrigger, DoesNotAlertPlot, AlwaysPlayable,
      (role: Role) => {
        // See Event Instructions table
      }
    )),
    // ------------------------------------------------------------------------
    entry(new Card(205, "Erdoğan Effect", Unassociated, 1,
      NoRemove, NoMarker, NoLapsing, NoAutoTrigger, DoesNotAlertPlot, AlwaysPlayable,
      (role: Role) => {
        // See Event Instructions table
      }
    )),
    // ------------------------------------------------------------------------
    entry(new Card(206, "Friday of Anger", Unassociated, 1,
      NoRemove, NoMarker, NoLapsing, NoAutoTrigger, DoesNotAlertPlot, AlwaysPlayable,
      (role: Role) => ()
    )),
    // ------------------------------------------------------------------------
    entry(new Card(207, "JV / Copycat", Unassociated, 1,
      NoRemove, NoMarker, NoLapsing, NoAutoTrigger, DoesNotAlertPlot, AlwaysPlayable,
      (role: Role) => {
        // See Event Instructions table
      }
    )),
    // ------------------------------------------------------------------------
    entry(new Card(208, "Kinder – Gentler", Unassociated, 1,
      NoRemove, NoMarker, NoLapsing, NoAutoTrigger, DoesNotAlertPlot, AlwaysPlayable,
      (role: Role) => {
        // See Event Instructions table
      }
    )),
    // ------------------------------------------------------------------------
    entry(new Card(209, "Quds Force", Unassociated, 1,
      NoRemove, NoMarker, NoLapsing, NoAutoTrigger, DoesNotAlertPlot, AlwaysPlayable,
      (role: Role) => {
        // See Event Instructions table
      }
    )),
    // ------------------------------------------------------------------------
    entry(new Card(210, "Sectarian Violence", Unassociated, 1,
      NoRemove, NoMarker, NoLapsing, NoAutoTrigger, DoesNotAlertPlot, AlwaysPlayable,
      (role: Role) => ()
    )),
    // ------------------------------------------------------------------------
    entry(new Card(211, "Smartphones", Unassociated, 1,
      NoRemove, GlobalMarker, NoLapsing, NoAutoTrigger, DoesNotAlertPlot, AlwaysPlayable,
      (role: Role) => {
        // TODO: Need to flesh this out.
        removeGlobalEventMarker("Censorship")
        addGlobalEventMarker("Smartphones")
      }
      
    )),
    // ------------------------------------------------------------------------
    entry(new Card(212, "Smartphones", Unassociated, 1,
      NoRemove, GlobalMarker, NoLapsing, NoAutoTrigger, DoesNotAlertPlot, AlwaysPlayable,
      (role: Role) => ()
    )),
    // ------------------------------------------------------------------------
    entry(new Card(213, "Smartphones", Unassociated, 1,
      NoRemove, GlobalMarker, NoLapsing, NoAutoTrigger, DoesNotAlertPlot, AlwaysPlayable,
      (role: Role) => ()
    )),
    // ------------------------------------------------------------------------
    entry(new Card(214, "3 Cups of Tea", Unassociated, 2,
      NoRemove, GlobalMarker, NoLapsing, NoAutoTrigger, DoesNotAlertPlot, AlwaysPlayable,
      (role: Role) => ()
    )),
    // ------------------------------------------------------------------------
    entry(new Card(215, "Abu Bakr al-Baghdadi", Unassociated, 2,
      USRemove, NoMarker, NoLapsing, NoAutoTrigger, DoesNotAlertPlot, AlwaysPlayable,
      (role: Role) => () 
      // Can possibly declare Caliphate, only in Syria or Iraq
      // Only by Jihadist play
      // See Event Instructions table
    )),
    // ------------------------------------------------------------------------
    entry(new Card(216, "Abu Sayyaf (ISIL)", Unassociated, 2,
      USRemove, NoMarker, NoLapsing, NoAutoTrigger, DoesNotAlertPlot, AlwaysPlayable,
      (role: Role) => ()
    )),
    // ------------------------------------------------------------------------
    entry(new Card(217, "Agitators", Unassociated, 2,
      NoRemove, NoMarker, NoLapsing, NoAutoTrigger, DoesNotAlertPlot, AlwaysPlayable,
      (role: Role) => {
        // See Event Instructions table
      }
    )),
    // ------------------------------------------------------------------------
    entry(new Card(218, "Al-Nusra Front", Unassociated, 2,
      NoRemove, NoMarker, NoLapsing, NoAutoTrigger, DoesNotAlertPlot, AlwaysPlayable,
      (role: Role) => {
        // See Event Instructions table
      }
    )),
    // ------------------------------------------------------------------------
    entry(new Card(219, "Ayman al-Zawahiri", Unassociated, 2,
      USRemove, NoMarker, NoLapsing, NoAutoTrigger, DoesNotAlertPlot, AlwaysPlayable,
      (role: Role) => ()
    )),
    // ------------------------------------------------------------------------
    entry(new Card(220, "Daraa", Unassociated, 2,
      Remove, NoMarker, NoLapsing, NoAutoTrigger, DoesNotAlertPlot, AlwaysPlayable,
      (role: Role) => ()
    )),
    // ------------------------------------------------------------------------
    entry(new Card(221, "FlyPaper", Unassociated, 2,
      NoRemove, NoMarker, NoLapsing, NoAutoTrigger, DoesNotAlertPlot, AlwaysPlayable,
      (role: Role) => {
        // Can possibly declare Caliphate, by either player
        // See Event Instructions table
      }  
    )),
    // ------------------------------------------------------------------------
    entry(new Card(222, "Hagel", Unassociated, 2,
      Remove, NoMarker, NoLapsing, NoAutoTrigger, DoesNotAlertPlot, AlwaysPlayable,
      (role: Role) => {
        // See Event Instructions table
      }
    )),
    // ------------------------------------------------------------------------
    entry(new Card(223, "Iranian Elections", Unassociated, 2,
      Remove, NoMarker, NoLapsing, NoAutoTrigger, DoesNotAlertPlot, AlwaysPlayable,
      (role: Role) => {
        // See Event Instructions table
      }
    )),
    // ------------------------------------------------------------------------
    entry(new Card(224, "Je Suis Charlie", Unassociated, 2,
      NoRemove, NoMarker, NoLapsing, NoAutoTrigger, DoesNotAlertPlot, AlwaysPlayable,
      (role: Role) => {
        // See Event Instructions table
      }
    )),
    // ------------------------------------------------------------------------
    entry(new Card(225, "Jihadi John", Unassociated, 2,
      USRemove, NoMarker, NoLapsing, NoAutoTrigger, DoesNotAlertPlot, AlwaysPlayable,
      (role: Role) => ()
    )),
    // ------------------------------------------------------------------------
    entry(new Card(226, "Operation Serval", Unassociated, 2,
      NoRemove, GlobalMarker, NoLapsing, NoAutoTrigger, DoesNotAlertPlot, AlwaysPlayable,
      (role: Role) => {
        // See Event Instructions table
      }
    )),
    // ------------------------------------------------------------------------
    entry(new Card(227, "Popular Support", Unassociated, 2,
      NoRemove, NoMarker, NoLapsing, NoAutoTrigger, DoesNotAlertPlot, AlwaysPlayable,
      (role: Role) => ()
    )),
    // ------------------------------------------------------------------------
    entry(new Card(228, "Popular Support", Unassociated, 2,
      NoRemove, NoMarker, NoLapsing, NoAutoTrigger, DoesNotAlertPlot, AlwaysPlayable,
      (role: Role) => ()
    )),
    // ------------------------------------------------------------------------
    entry(new Card(229, "Prisoner Exchange", Unassociated, 2,
      NoRemove, NoMarker, NoLapsing, NoAutoTrigger, DoesNotAlertPlot, AlwaysPlayable,
      (role: Role) => {
        // See Event Instructions table
      }
    )),
    // ------------------------------------------------------------------------
    entry(new Card(230, "Sellout", Unassociated, 2,
      NoRemove, NoMarker, NoLapsing, NoAutoTrigger, DoesNotAlertPlot, AlwaysPlayable,
      (role: Role) => {
        // Not playable in a caliphate member
        // See Event Instructions table
      }
    )),
    // ------------------------------------------------------------------------
    entry(new Card(231, "Siege of Kobanigrad", Unassociated, 2,
      NoRemove, NoMarker, NoLapsing, NoAutoTrigger, DoesNotAlertPlot, AlwaysPlayable,
      (role: Role) => ()
    )),
    // ------------------------------------------------------------------------
    entry(new Card(232, "Trade Embargo", Unassociated, 2,
      USRemove, GlobalMarker, NoLapsing, NoAutoTrigger, DoesNotAlertPlot, AlwaysPlayable,
      (role: Role) => {
        // Mark as either "Trade Embargo (US)" or "Trade Embargo (Jihadist)"
        // If Iran becomes Neutral or Ally, remove any Trade Embargo marker. [11.3.3.1]
      }
    )),
    // ------------------------------------------------------------------------
    entry(new Card(233, "UN Ceasefire", Unassociated, 2,
      NoRemove, NoMarker, NoLapsing, NoAutoTrigger, DoesNotAlertPlot, AlwaysPlayable,
      (role: Role) => {
        // Not playable in a caliphate member
        // See Event Instructions table
      }
    )),
    // ------------------------------------------------------------------------
    entry(new Card(234, "Free Syrian Army", Unassociated, 3,
      Remove, NoMarker, NoLapsing, NoAutoTrigger, DoesNotAlertPlot, AlwaysPlayable,
      (role: Role) => {
        // See Event Instructions table
      }
    )),
    // ------------------------------------------------------------------------
    entry(new Card(235, "Qadhafi", Unassociated, 3,
      NoRemove, NoMarker, NoLapsing, NoAutoTrigger, DoesNotAlertPlot, AlwaysPlayable,
      (role: Role) => {
        // See Event Instructions table
      }
    )),
    // ------------------------------------------------------------------------
    entry(new Card(236, "Oil Price Spike", Unassociated, 3,
      NoRemove, NoMarker, Lapsing, NoAutoTrigger, DoesNotAlertPlot, AlwaysPlayable,
      (role: Role) => {
        // Removes "Fracking" marker
        // See Event Instructions table
      }
    )),
    // ------------------------------------------------------------------------
    entry(new Card(237, "Osama bin Ladin", Unassociated, 3,
      USRemove, NoMarker, NoLapsing, NoAutoTrigger, DoesNotAlertPlot, AlwaysPlayable,
      (role: Role) => ()
    )),
    // ------------------------------------------------------------------------
    entry(new Card(238, "Revolution", Unassociated, 3,
      NoRemove, NoMarker, NoLapsing, NoAutoTrigger, DoesNotAlertPlot, AlwaysPlayable,
      (role: Role) => {
        // See Event Instructions table
      }
    )),
    // ------------------------------------------------------------------------
    entry(new Card(239, "Truce", Unassociated, 3,
      NoRemove, NoMarker, Lapsing, NoAutoTrigger, DoesNotAlertPlot, AlwaysPlayable,
      (role: Role) => {
        // Not playable in a caliphate member
        // See Event Instructions table
      }
    )),
    // ------------------------------------------------------------------------
    entry(new Card(240, "US Election", Unassociated, 3,
      NoRemove, NoMarker, NoLapsing, AutoTrigger, DoesNotAlertPlot,
      (role: Role) => false  // No directly playable, but will always auto trigger
      ,
      (role: Role) => {
        // See Event Instructions table
        // if lapsingEventInPlay("US Consulate Attacked") the posture switches, without a roll.
      }
    ))
  )
}