
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

import scala.util.Random.{shuffle, nextInt}
import scala.annotation.tailrec
import scala.util.Properties.{lineSeparator, isWin}
import scala.collection.immutable.ListMap
import scala.collection.mutable.ListBuffer

object LabyrinthAwakening {
  
  def dieRoll = nextInt(6) + 1
  def humanDieRoll(prompt: String = "Enter die roll: ", allowAbort: Boolean = true) =
    if (game.params.humanAutoRoll)
      dieRoll
    else
      (askOneOf(prompt, 1 to 6, allowAbort = allowAbort) map (_.toInt)).get
  
  // If the given role is human the prompt if necessary otherwise produce the roll automatically
  def getDieRoll(role: Role, prompt: String = "Enter die roll: ", allowAbort: Boolean = true): Int = {
    if (role == game.humanRole)
      humanDieRoll(prompt, allowAbort)
    else
      dieRoll
  }
  
  val INTEGER = """(\d+)""".r
  
  sealed trait CardAssociation
  case object Unassociated extends CardAssociation { override def toString() = "Unassociated" }
  
  sealed trait Role extends CardAssociation
  case object US extends Role       { override def toString() = "US" }
  case object Jihadist extends Role { override def toString() = "Jihadist" }
  object Role {
    def apply(name: String): Role = name.toLowerCase match {
      case "us"       => US
      case "jihadist" => Jihadist
      case _ => throw new IllegalArgumentException(s"Invalid role name: $name")
    }
  }
  
  case class BotDifficulty(name: String, description: String) {
    override def toString() = f"$name%-10s - $description"
  }
  
  // US
  val OffGuard  = BotDifficulty("Off Guard", "Standard rules")
  val Competent = BotDifficulty("Competent", "Alert affects two plots in the same country")
  val Adept     = BotDifficulty("Adept"    , "Disrupt at two or more troops awards +2 prestige")
  val Vigilant  = BotDifficulty("Vigilant" , "No auto recruit in Regime Change or Civil War countries")
  val Ruthless  = BotDifficulty("Ruthless" , "US associated events trigger on first plot")
  val NoMercy   = BotDifficulty("NoMercy"  , "Ignore DRM penalty on War of Ideas")

  // Jihadist
  val Muddled    = BotDifficulty("Muddled"   , "Standard rules")
  val Coherent   = BotDifficulty("Coherent"  , "Ignore DRM penalty during Minor Jihad")
  val Attractive = BotDifficulty("Attractive", "Each successful plot places two available plot markers")
  val Potent     = BotDifficulty("Potent"    , "Each successful recruit places two available cells")
  val Infectious = BotDifficulty("Infectious", "US must play all cards")
  val Virulent   = BotDifficulty("Virulent"  , "Ignore all DRM penalties")
  
  object BotDifficulty {
    def apply(name: String): BotDifficulty = name match {
      case OffGuard.name   => OffGuard
      case Competent.name  => Competent
      case Adept.name      => Adept
      case Vigilant.name   => Vigilant
      case Ruthless.name   => Ruthless
      case NoMercy.name    => NoMercy
      case Muddled.name    => Muddled
      case Coherent.name   => Coherent
      case Attractive.name => Attractive
      case Potent.name     => Potent
      case Infectious.name => Infectious
      case Virulent.name   => Virulent
      case _ => throw new IllegalArgumentException(s"Invalid BotDifficulty name: $name")
    }
  }
  
  trait Plot {
    val number: Int        // Dice rolled against governance in muslim countries
    val opsToPlace: Int    // Min number of ops needed to place this plot.
    val name: String 
    override def toString() = name
  }
  
  // Order so that the most dangerous come first: WMD, 3, 2, 1
  implicit val PlotOrdering = new Ordering[Plot] {
    def compare(x: Plot, y: Plot) = (x, y) match {
      case (PlotWMD, PlotWMD) =>  0
      case (PlotWMD, _)       => -1
      case (_, PlotWMD)       =>  1
      case (x, y)             =>  y.number - x.number
    }
  }
  case object PlotWMD extends Plot {
    val number     = 3
    val opsToPlace = 1
    val name = "Plot WMD"
  }
  class NumberedPlot(val number: Int) extends Plot {
    val opsToPlace = number
    val name: String = s"Plot $number"
  }
  case object Plot1 extends NumberedPlot(1)
  case object Plot2 extends NumberedPlot(2)
  case object Plot3 extends NumberedPlot(3)

  case class PlotOnMap(plot: Plot, backlashed: Boolean = false) {
    override def toString() = if (backlashed) s"${plot.name} (backlashed)" else plot.name
  }
  implicit val PlotOnMapOrdering = Ordering.by { x: PlotOnMap => x.plot }

  val NoRegimeChange    = "None"
  val GreenRegimeChange = "Green"
  val TanRegimeChange   = "Tan"

  val Canada            = "Canada"
  val UnitedStates      = "United States"
  val UnitedKingdom     = "United Kingdom"
  val Serbia            = "Serbia"
  val Israel            = "Israel"
  val India             = "India"
  val Scandinavia       = "Scandinavia"
  val EasternEurope     = "Eastern Europe"
  val Benelux           = "Benelux"
  val Germany           = "Germany"
  val France            = "France"
  val Italy             = "Italy"
  val Spain             = "Spain"
  val Russia            = "Russia"
  val Caucasus          = "Caucasus"
  val China             = "China"
  val KenyaTanzania     = "Kenya/Tanzania"
  val Thailand          = "Thailand"
  val Philippines       = "Philippines"
  val Morocco           = "Morocco"
  val AlgeriaTunisia    = "Algeria/Tunisia"
  val Libya             = "Libya"
  val Egypt             = "Egypt"
  val Sudan             = "Sudan"
  val Somalia           = "Somalia"
  val Jordan            = "Jordan"
  val Syria             = "Syria"
  val CentralAsia       = "Central Asia"
  val IndonesiaMalaysia = "Indonesia/Malaysia"
  val Turkey            = "Turkey"
  val Lebanon           = "Lebanon"
  val Yemen             = "Yemen"
  val Iraq              = "Iraq"
  val SaudiArabia       = "Saudi Arabia"
  val GulfStates        = "Gulf States"
  val Pakistan          = "Pakistan"
  val Afghanistan       = "Afghanistan"
  val Iran              = "Iran"
  val Mali              = "Mali"
  val Nigeria           = "Nigeria"
  
  val CountryAbbreviations = Map("US" -> UnitedStates, "UK" -> UnitedKingdom)
  // Troop commitment
  val LowIntensity = "Low Intensity"
  val War          = "War"
  val Overstretch  = "Overstretch"
  val USCardDraw = Map(LowIntensity -> 9, War -> 8, Overstretch -> 7)

  // Prestige Level
  val Low      = "Low"
  val Medium   = "Medium"
  val High     = "High"  
  val VeryHigh = "Very High"  
  // Funding level
  val Tight    = "Tight"
  val Moderate = "Moderate"
  val Ample    = "Ample"
  val JihadistCardDraw = Map(Tight -> 7, Moderate -> 8, Ample -> 9)
  
  // Country Types
  val NonMuslim = "Non-Muslim"
  val Sunni     = "Sunni"
  val ShiaMix   = "Shia-Mix"
  
  val PostureUntested = "Untested"
  val Soft            = "Soft"
  val Hard            = "Hard"
  val Even            = "Even"  // for display only
  
  def oppositePosture(p: String): String = {
    if (p != Hard && p != Soft)
      throw new IllegalArgumentException(s"oppositePosture($p)")
    if (p == Hard) Soft else Hard
  }
  val GovernanceUntested = 0
  val Good               = 1
  val Fair               = 2
  val Poor               = 3
  val IslamistRule       = 4
  
  val govToString = Map(
    GovernanceUntested -> "Untested",
    Good               -> "Good",
    Fair               -> "Fair",
    Poor               -> "Poor",
    IslamistRule       -> "Islamist Rule")
    
  val govFromString = Map(
   "Untested"      -> GovernanceUntested,
   "Good"          -> Good,
   "Fair"          -> Fair,
   "Poor"          -> Poor,
   "Islamist Rule" -> IslamistRule)
  
  val Ally      = "Ally"
  val Neutral   = "Neutral"
  val Adversary = "Adversary"
  
  val Schengen      = Scandinavia :: Benelux :: Germany :: EasternEurope :: 
                      France :: Italy :: Spain :: Nil
  val SchengenLinks = Canada :: UnitedStates :: UnitedKingdom :: Morocco ::
                      AlgeriaTunisia :: Libya :: Serbia :: Turkey :: Lebanon ::
                      Russia :: Nil
    
  // List of names to adjacent countries
  case class CountryData(adjacent: List[String])
                    
  // A country key and a list of adjacent country keys.
  val countryData: Map[String, CountryData] = Map(
   Canada            -> CountryData(UnitedStates :: UnitedKingdom :: Schengen),
   UnitedStates      -> CountryData(Canada :: UnitedKingdom :: Philippines :: Schengen),
   UnitedKingdom     -> CountryData(Canada :: UnitedStates :: Schengen),
   Serbia            -> CountryData(Russia :: Turkey :: Schengen),
   Israel            -> CountryData(Egypt :: Jordan :: Lebanon :: Nil),
   India             -> CountryData(Pakistan :: IndonesiaMalaysia :: Nil),
   Scandinavia       -> CountryData((Schengen ::: SchengenLinks) filterNot (_ == Scandinavia)),
   EasternEurope     -> CountryData((Schengen ::: SchengenLinks) filterNot (_ == EasternEurope)),
   Benelux           -> CountryData((Schengen ::: SchengenLinks) filterNot (_ == Benelux)),
   Germany           -> CountryData((Schengen ::: SchengenLinks) filterNot (_ == Germany)),
   France            -> CountryData((Schengen ::: SchengenLinks) filterNot (_ == France)),
   Italy             -> CountryData((Schengen ::: SchengenLinks) filterNot (_ == Italy)),
   Spain             -> CountryData((Schengen ::: SchengenLinks) filterNot (_ == Spain)),
   Russia            -> CountryData(Serbia :: Turkey :: Caucasus :: CentralAsia :: Schengen),
   Caucasus          -> CountryData(Turkey :: Russia :: CentralAsia :: Iran :: Nil),
   China             -> CountryData(CentralAsia:: Thailand :: Nil),
   KenyaTanzania     -> CountryData(Sudan :: Somalia :: Nigeria :: Nil),
   Thailand          -> CountryData(China :: IndonesiaMalaysia :: Philippines :: Nil),
   Philippines       -> CountryData(UnitedStates :: IndonesiaMalaysia :: Thailand :: Nil),
   Morocco           -> CountryData(AlgeriaTunisia :: Mali :: Schengen),
   AlgeriaTunisia    -> CountryData(Morocco :: Libya :: Mali :: Schengen),
   Libya             -> CountryData(AlgeriaTunisia :: Egypt :: Sudan :: Schengen),
   Egypt             -> CountryData(Israel :: Libya :: Sudan :: Nil),
   Sudan             -> CountryData(Libya :: Egypt :: Somalia :: KenyaTanzania :: Nigeria :: Nil),
   Somalia           -> CountryData(Yemen :: Sudan :: KenyaTanzania :: Nil),
   Jordan            -> CountryData(Israel :: Syria :: Iraq :: SaudiArabia :: Nil),
   Syria             -> CountryData(Iraq :: Lebanon :: Jordan :: Turkey :: Nil),
   CentralAsia       -> CountryData(Russia :: Caucasus :: Iran :: Afghanistan :: China :: Nil),
   IndonesiaMalaysia -> CountryData(Pakistan :: IndonesiaMalaysia :: Thailand :: Philippines :: Nil),
   Turkey            -> CountryData(Serbia :: Russia :: Caucasus :: Syria :: Iran :: Schengen),
   Lebanon           -> CountryData(Syria :: Israel :: Schengen),
   Yemen             -> CountryData(SaudiArabia :: Somalia :: Nil),
   Iraq              -> CountryData(GulfStates :: SaudiArabia :: Iran :: Turkey :: Syria :: Jordan :: Nil),
   SaudiArabia       -> CountryData(Jordan :: Iraq :: GulfStates :: Yemen :: Nil),
   GulfStates        -> CountryData(SaudiArabia :: Iraq :: Iran :: Pakistan :: Nil),
   Pakistan          -> CountryData(GulfStates :: Iran :: Afghanistan :: IndonesiaMalaysia :: India :: Nil),
   Afghanistan       -> CountryData(Pakistan :: Iran :: CentralAsia :: Nil),
   Iran              -> CountryData(Afghanistan :: Pakistan :: GulfStates :: Iraq :: Turkey :: Caucasus :: CentralAsia :: Nil),
   Mali              -> CountryData(Morocco :: AlgeriaTunisia :: Nigeria :: Nil),
   Nigeria           -> CountryData(Mali :: Sudan :: KenyaTanzania :: Nil)
  )
  
  def getAdjacent(name: String): List[String] = countryData(name).adjacent
  def getAdjacentMuslims(name: String) = getAdjacent(name) filter game.isMuslim
  def getAdjacentNonMuslims(name: String) = getAdjacent(name) filter game.isNonMuslim
  def areAdjacent(name1: String, name2: String) = getAdjacent(name1) contains name2

  // Shortest distance between countries
  def distance(source: String, target: String): Int = {
    def measure(current: String, visited: Set[String]): Option[Int] = {
      if (current == target)
        Some(0)
      else {
        (getAdjacent(current) filterNot visited) match {
          case Nil       => None
          case adjacents => 
            val paths = adjacents.map(a => measure(a, visited ++ adjacents)).flatten.sorted
            paths match {
              case Nil => None
              case x :: _ => Some(1 + x)
            }
        }
      }
    }
    measure(source, Set.empty).get
  }
  
  def plotsDisplay(plots: List[Plot], humanRole: Role): String = (plots.size, humanRole) match {
    case (0, _)        => "none"
    case (n, US)       => amountOf(n, "hidden plot")
    case (_, Jihadist) => plots.sorted map (_.name) mkString ", "
  }
  
  def mapPlotsDisplay(plots: List[PlotOnMap], humanRole: Role): String = {
    val lashed = plots count (_.backlashed)
    (plots.size, humanRole) match {
      case (0, _)                => "none"
      case (n, US) if lashed > 0 => s"${amountOf(n, "hidden plot")}, $lashed backlashed"
      case (n, US)               => amountOf(n, "hidden plot")
      case (_, Jihadist)         => plots.sorted map (_.toString) mkString ", "
    }
  }
  
  // Used to describe event markers that represent troops.
  // prestigeLoss: if true, the marker's presence during a plot will cause loss of prestige
  case class TroopsMarker(name: String, num: Int, canDeploy: Boolean, prestigeLoss: Boolean)
  // When removing troop markers, the Bot will choose the first one
  // based on the following sort order.
  // - markers represent smaller numbers of troops come first
  // - markers that suffer prestige loss come first??
  implicit val TroopsMarkerOrdering = new Ordering[TroopsMarker] {
    def compare(x: TroopsMarker, y: TroopsMarker) = 
      if (x.num == y.num)
        y.prestigeLoss compare x.prestigeLoss // reversed
      else
        x.num compare y.num
  }
  
  type CardEvent       = Role => Unit
  type EventConditions = Role => Boolean
  val AlwaysPlayable: EventConditions =  _ => true
  
  sealed trait CardMarker
  case object NoMarker      extends CardMarker
  case object GlobalMarker  extends CardMarker
  case object CountryMarker extends CardMarker
  
  sealed trait CardRemoval
  case object NoRemove       extends CardRemoval
  case object Remove         extends CardRemoval
  case object USRemove       extends CardRemoval
  case object JihadistRemove extends CardRemoval
  
  sealed trait CardLapsing
  case object NoLapsing       extends CardLapsing
  case object Lapsing         extends CardLapsing
  case object USLapsing       extends CardLapsing
  case object JihadistLapsing extends CardLapsing
  
  val AutoTrigger   = true
  val NoAutoTrigger = false
  
  class Card(
    val number: Int,
    val name: String,
    val association: CardAssociation,
    val ops: Int,
    val remove: CardRemoval,
    val marker: CardMarker,   // Only used by the adjust routines
    val lapsing: CardLapsing,
    val autoTrigger: Boolean,
    val eventConditions: EventConditions,
    val executeEvent: CardEvent) {
      
    def numAndName = s"#$number $name"
    override def toString() = s"${numAndName} (${opsString(ops)})"
    
    def eventIsPlayable(role: Role): Boolean =
      (association == Unassociated || association == role) && eventConditions(role)
      
    def eventWillTrigger(opponentRole: Role): Boolean =
      association == opponentRole && eventConditions(opponentRole)
  }
  
  // Sort by card number
  implicit val CardOrdering = new Ordering[Card] {
    def compare(x: Card, y: Card) = x.number compare y.number
  }
  
  // Used to keep track of cards played during the current turn.
  case class PlayedCard(role:Role, card: Card) {
    override def toString() = s"$role played ${cardNumAndName(card.number)}"
  }
  
  val LabyrinthDeck = "Labyrinth Deck"  // For future support…
  val AwakeningDeck = "Awakening Deck"
  
  trait CardDeck {
    val cardMap: Map[Int, Card]
    
    def isValidCardNumber(num: Int): Boolean = cardMap contains num
    def apply(num: Int): Card      = cardMap(num)  // Allows deck(4) to get a specific card
    def cards: List[Card]          = cardMap.valuesIterator.toList.sorted
    def lapsing: List[Card]        = cards filter (_.lapsing != NoLapsing)
    def removable: List[Card]      = cards filter (_.remove != NoRemove)

    // Convenience method for add a card to the card map.
    protected def entry(card: Card) = (card.number -> card)
  }
  
  val CardDecks = Map(AwakeningDeck -> AwakeningCards)
  def deck                 = CardDecks(game.params.cardDeckName)
  def cards                = deck.cards
  def lapsingCardNumbers   = deck.lapsing map (_.number)
  def removableCardNumbers = deck.removable map (_.number)
  
  def cardNumAndName(number: Int): String = deck(number).numAndName
  def cardNumsAndNames(xs: List[Int]): String = xs.sorted map cardNumAndName mkString ", "
  
  sealed trait Country {
    val name: String
    val governance: Int
    val sleeperCells: Int
    val activeCells: Int
    val hasCadre: Boolean
    val plots: List[PlotOnMap]
    val markers: List[String]
    val wmdCache: Int        // Number of WMD plots cached
    
    def isUntested: Boolean
    def isGood         = governance == Good
    def isFair         = governance == Fair
    def isPoor         = governance == Poor
    def isIslamistRule = governance == IslamistRule
    
    def totalCells = sleeperCells + activeCells
    def hasMarker(name: String) = markers contains name
    
    def hasPlots = plots.nonEmpty
    def warOfIdeasOK(ops: Int, ignoreRegimeChange: Boolean = false): Boolean
    def recruitOK: Boolean = hasCadre || totalCells > 0
    def autoRecruit: Boolean
    def recruitSucceeds(die: Int): Boolean
  }
  
  case class NonMuslimCountry(
    name: String,
    governance: Int             = Good,
    sleeperCells: Int           = 0,
    activeCells: Int            = 0,
    hasCadre: Boolean           = false,
    plots: List[PlotOnMap]      = Nil,
    markers: List[String]       = Nil,
    posture: String             = PostureUntested,
    recruitOverride: Int        = 0,
    wmdCache: Int               = 0,  // Number of WMD plots cached
    iranSpecialCase: Boolean    = false
  ) extends Country {
    override def isUntested = posture == PostureUntested
    def isSchengen = Schengen contains name
    def isHard = posture == Hard
    def isSoft = posture == Soft
    override def warOfIdeasOK(ops: Int, ignoreRegimeChange: Boolean = false) = 
      ops >= governance && !(iranSpecialCase || name == UnitedStates || name == Israel)

    val recruitNumber = governance max recruitOverride
    def autoRecruit = false
    def recruitSucceeds(die: Int) = die <= recruitNumber
    def addMarkers(names: String*): NonMuslimCountry = this.copy(markers = markers ++ names)
    def removeMarkers(names: String*): NonMuslimCountry = this.copy(markers = markers filterNot names.contains)
    // US posture is stored in the GameState
    def canChangePosture = !(iranSpecialCase || name == UnitedStates || name == Israel)
    
  }

  case class MuslimCountry(
    name: String,
    governance: Int             = GovernanceUntested,
    sleeperCells: Int           = 0,
    activeCells: Int            = 0,
    hasCadre: Boolean           = false,
    plots: List[PlotOnMap]      = Nil,
    markers: List[String]       = Nil,
    isSunni: Boolean            = true,
    resources: Int              = 0,
    alignment: String           = Neutral,
    troops: Int                 = 0,
    militia: Int                = 0,
    oilProducer: Boolean        = false,
    aidMarkers: Int             = 0,
    regimeChange: String        = NoRegimeChange,
    besiegedRegime: Boolean     = false,
    civilWar: Boolean           = false,
    caliphateCapital: Boolean   = false,
    awakening: Int              = 0,  // number of awakening markers
    reaction: Int               = 0,  // number of reaction markers
    wmdCache: Int               = 0   // Number of WMD plots cached
  ) extends Country {
    override def isUntested = governance == GovernanceUntested
    def isAlly      = alignment == Ally
    def isNeutral   = alignment == Neutral
    def isAdversary = alignment == Adversary
    
    def isShiaMix = !isSunni
    def inRegimeChange = regimeChange != NoRegimeChange
    
    def isUntestedWithData: Boolean = isUntested && (
      totalCells > 0            ||
      hasCadre                  ||
      hasPlots                  ||
      markers.nonEmpty          ||
      totalTroopsAndMilitia > 0 ||
      inRegimeChange            ||
      besiegedRegime            ||
      civilWar                  ||
      caliphateCapital          ||
      aidMarkers > 0            ||
      awakening > 0             ||
      reaction > 0              ||
      wmdCache > 0
    )
    
    // If a muslim country is untest, then it is valid a WoI target.
    override def warOfIdeasOK(ops: Int, ignoreRegimeChange: Boolean = false) =
      (isUntested      || ops >= governance)  &&
      !(isAdversary    || (isGood && isAlly)) &&
      (!inRegimeChange || ignoreRegimeChange || (totalTroopsAndMilitia - totalCells) >= 5)
    
    def autoRecruit = isIslamistRule || civilWar || inRegimeChange || hasMarker("Training Camps")
    def recruitSucceeds(die: Int) = autoRecruit || die <= governance
    
    // TODO: Add other markers!!
    def troopsMarkers: List[TroopsMarker] = markers collect {
      case "NATO"       => TroopsMarker("NATO", 2,       canDeploy = true,  prestigeLoss = true)
      case "UNSCR 1973" => TroopsMarker("UNSCR 1973", 1, canDeploy = false, prestigeLoss = false)
    }
    
    def markerTroops: Int = troopsMarkers.foldLeft(0) { (total, tm) => total + tm.num }
    def markerTroopsThatAffectPrestige: Int =
      troopsMarkers.foldLeft(0) { (total, tm) => total + (if (tm.prestigeLoss) tm.num else 0) }
    def totalTroops = troops + markerTroops
    def totalTroopsThatAffectPrestige = troops + markerTroopsThatAffectPrestige
    def totalTroopsAndMilitia = totalTroops + militia // Used to calculate hit for attrition
    def disruptAffectsPrestige = totalTroopsAndMilitia > 1 && totalTroopsThatAffectPrestige > 0
    
    def canTakeAwakeningOrReactionMarker = !(isGood || isIslamistRule || civilWar)
    def canTakeAidMarker = !(isGood || isIslamistRule)
    def caliphateCandidate = civilWar || isIslamistRule || inRegimeChange

    def canDeployTo(ops: Int) = alignment == Ally && ops >= governance
    def maxDeployFrom = if (inRegimeChange)
      if (troops - totalCells >= 5) troops - totalCells else 0
    else
      troops
    def canDeployFrom = maxDeployFrom > 0
      
    def jihadDRM = reaction - awakening
    def jihadOK = !isIslamistRule && totalCells > 0
    def majorJihadOK(ops: Int) = 
      totalCells - totalTroopsAndMilitia >= 5 && (
        (isPoor && (ops  > 1 || besiegedRegime)) || 
        (isFair && (ops == 3 || (ops == 2 && besiegedRegime)))
      )
    
      def addMarkers(names: String*): MuslimCountry = this.copy(markers = markers ++ names)
      def removeMarkers(names: String*): MuslimCountry = this.copy(markers = markers filterNot names.contains)
  }
    
  
  trait Scenario {
    val name: String
    val cardDeckName: String
    val prestige: Int
    val usPosture: String
    val funding: Int
    val availablePlots: List[Plot]     // 1, 2, 3, 4 == WMD
    val countries: List[Country]
    val markers: List[String]
  }
  
  class Awakening2010 extends Scenario {
    val name           = "Awakening (2010 Scenario)"
    val cardDeckName   = AwakeningDeck
    val prestige       = 5
    val usPosture      = Soft
    val funding        = 5
    val availablePlots = Plot1::Plot1::Plot1::Plot2::Plot2::Plot3::Nil
    val countries = List(
      NonMuslimCountry(Canada),
      NonMuslimCountry(UnitedStates, posture = Soft),
      NonMuslimCountry(UnitedKingdom, posture = Hard),
      NonMuslimCountry(Serbia),
      NonMuslimCountry(Israel, posture = Hard),
      NonMuslimCountry(India),
      NonMuslimCountry(Scandinavia),
      NonMuslimCountry(EasternEurope),
      NonMuslimCountry(Benelux, posture = Soft),
      NonMuslimCountry(Germany),
      NonMuslimCountry(Italy),
      NonMuslimCountry(France, recruitOverride = 2, posture = Hard),
      NonMuslimCountry(Spain, recruitOverride = 2),
      NonMuslimCountry(Russia, governance = Fair),
      NonMuslimCountry(Caucasus, governance = Fair),
      NonMuslimCountry(China, governance = Fair),
      NonMuslimCountry(KenyaTanzania, governance = Fair),
      NonMuslimCountry(Thailand, governance = Fair),
      NonMuslimCountry(Philippines, governance = Fair, recruitOverride = 3),
      NonMuslimCountry(Iran, governance = Fair, wmdCache = 1, iranSpecialCase = true),
      NonMuslimCountry(Nigeria, governance = Poor),
      
      MuslimCountry(Morocco, resources = 2),
      MuslimCountry(AlgeriaTunisia, resources = 2, oilProducer = true,
                    governance = Poor, alignment = Neutral, awakening = 1),
      MuslimCountry(Libya, resources = 1, oilProducer = true),
      MuslimCountry(Egypt, resources = 3),
      MuslimCountry(Sudan, resources = 1, oilProducer = true),
      MuslimCountry(Somalia, resources = 1),
      MuslimCountry(Jordan, resources = 1),
      MuslimCountry(Syria, resources = 2, wmdCache = 2),
      MuslimCountry(CentralAsia, resources = 2),
      MuslimCountry(Turkey, isSunni = false, resources = 2),
      MuslimCountry(Lebanon, isSunni = false, resources = 1),
      MuslimCountry(Yemen, isSunni = false, resources = 1),
      MuslimCountry(Iraq, isSunni = false, resources = 3, oilProducer = true,
                    governance = Poor, alignment = Ally, troops = 2, sleeperCells = 1),
      MuslimCountry(SaudiArabia, isSunni = false, resources = 3, oilProducer = true),
      MuslimCountry(GulfStates, isSunni = false, resources = 3, oilProducer = true,
                    governance = Fair, alignment = Ally, troops = 2),
      MuslimCountry(Pakistan, isSunni = false, resources = 2, wmdCache = 3,
                    governance = Fair, alignment = Neutral, sleeperCells = 2),
      MuslimCountry(Afghanistan, isSunni = false, resources = 1,
                    governance = Poor, alignment = Ally, troops = 6, sleeperCells = 2,
                    regimeChange = TanRegimeChange),
      MuslimCountry(IndonesiaMalaysia, resources = 3, oilProducer = true),
      MuslimCountry(Mali, resources = 1)
    )
    val markers = List.empty[String]
  }
  
  // There is a limit of 22 construction arguments for case classes
  // To work around this in the GameState, we will combine a couple of parameters
  case class CampCells(inCamp: Int, onMap: Int)
  case class Reserves(us: Int, jihadist: Int)
  // Keeps track of the which countries were the target of
  // operations
  // events
  // improved or tested
  // 
  // Some events depend on this.
  case class CardTargets(
    ops:              Set[String] = Set.empty,
    testedOrImproved: Set[String] = Set.empty,
    event:            Set[String] = Set.empty
  )
  
  case class GameParameters(
    scenarioName: String,
    cardDeckName: String,
    humanRole: Role,
    humanAutoRoll: Boolean,
    botDifficulties: List[BotDifficulty]
  )
  
  case class EventParameters(
    oilPriceSpikes: Int = 0,              // Number of Oil Price Spikes in effect
    sequestrationTroops: Boolean = false  // true if 3 troops off map due to Sequestration event
  )
  
  case class GameState(
    params: GameParameters,
    turn: Int,
    prestige: Int,
    usPosture: String,
    funding: Int,
    countries: List[Country],
    markers: List[String],
    availablePlots: List[Plot] = Plot1::Plot1::Plot1::Plot2::Plot2::Plot3::Nil,
    history: Vector[String] = Vector.empty,
    offMapTroops: Int = 0,
    reserves: Reserves = Reserves(0, 0),
    trainingCampCells: CampCells = CampCells(0, 0),
    eventParams: EventParameters = EventParameters(),
    resolvedPlots: List[Plot] = Nil,
    cardsPlayed: List[PlayedCard] = Nil,   // Cards played during current turn (most recent first).
    firstPlotCard: Option[Int] = None,  // Card number
    cardsLapsing: List[Int] = Nil,       // Card numbers
    cardsRemoved: List[Int] = Nil,   // Cards removed from the game.
    targetsThisCard: CardTargets = CardTargets(),
    targetsLastCard: CardTargets = CardTargets()
  ) {
    
    def humanRole = params.humanRole
    def botRole = if (humanRole == US) Jihadist else US
      
    def markerInPlay(name: String) = markers contains name
    def lapsingInPlay(name: String) = cardsLapsing exists (num => deck(num).name == name)
    
    def cardRemoved(num: Int) = cardsRemoved contains num
    def cardLapsing(num: Int) = cardsLapsing contains num
    
    def isFirstPlot(num: Int) = firstPlotCard == Some(num)
    def muslims    = countries filter (_.isInstanceOf[MuslimCountry]) map (_.asInstanceOf[MuslimCountry])
    def nonMuslims = countries filter (_.isInstanceOf[NonMuslimCountry]) map (_.asInstanceOf[NonMuslimCountry])
    
    // The methods assume a valid name and will throw an exception if an invalid name is used!
    def getCountry(name: String)   = (countries find (_.name == name)).get
    def getMuslim(name: String)    = (muslims find (_.name == name)).get
    def getNonMuslim(name: String) = (nonMuslims find (_.name == name)).get
    
    def getCountries(names: List[String]):  List[Country]          = names map getCountry
    def getMuslims(names: List[String]):    List[MuslimCountry]    = names map getMuslim
    def getNonMuslims(names: List[String]): List[NonMuslimCountry] = names map getNonMuslim
    
    def hasCountry(test: (Country) => Boolean)            = countries  exists test
    def hasMuslim(test: (MuslimCountry) => Boolean)       = muslims    exists test
    def hasNonMuslim(test: (NonMuslimCountry) => Boolean) = nonMuslims exists test
    
    def isMuslim(name: String)    = hasMuslim(_.name == name)
    def isNonMuslim(name: String) = hasNonMuslim(_.name == name)
    
    def adjacentCountries(name: String)   = getCountries(getAdjacent(name))
    def adjacentMuslims(name: String)     = getMuslims(getAdjacent(name) filter isMuslim)
    def adjacentNonMuslims(name: String)  = getNonMuslims(getAdjacent(name) filter isNonMuslim)
  
    def adjacentToGoodAlly(name: String)     = game.adjacentMuslims(name) exists (m => m.isGood && m.isAlly)
    def adjacentToIslamistRule(name: String) = game.adjacentMuslims(name) exists (_.isIslamistRule)
    
    def caliphateCapital: Option[String] = muslims find (_.caliphateCapital) map (_.name)
    def caliphateDeclared = caliphateCapital.nonEmpty
    def isCaliphateMember(name: String): Boolean = {
      caliphateCapital match {
        case None => false  // No Caliphate declared
        case Some(capital) => caliphateDaisyChain(capital) contains name
      }
    }
  
    // Return a list of countries comprising the daisy chain of caliphate candidates
    // that are adjacent to the given country.
    def caliphateDaisyChain(name: String): List[String] = {
      def adjacentCandidates(x: MuslimCountry) = adjacentMuslims(x.name).filter(_.caliphateCandidate)
      @tailrec def tryNext(candidates: List[MuslimCountry], chain: List[String]): List[String] = {
        candidates match {
          case Nil                                 => chain
          case x :: xs  if (chain contains x.name) => tryNext(xs, chain)
          case x :: xs                             => tryNext(xs ::: adjacentCandidates(x), x.name :: chain)
        }
      }
      
      val m = getMuslim(name)
      if (m.caliphateCandidate)
        tryNext(List(m), Nil)
      else
        Nil
    }
    
    def setCaliphateCapital(name: String): GameState = {
      assert(isMuslim(name), s"setCaliphateCapital() called on non-muslim country: $name")
      val capital = getMuslim(name);
      assert(capital.caliphateCandidate, s"setCaliphateCapital() called on invalid country: $name")
      
      // First make sure there is no country marked as the capital
      val clearedState = updateCountries(muslims.map(_.copy(caliphateCapital = false)))
      val daisyChain = clearedState.caliphateDaisyChain(name).map { memberName =>
        val member = getMuslim(memberName)
        member.copy(
          caliphateCapital = member.name == name,
          activeCells      = member.totalCells,  // All cells go active in caliphate members
          sleeperCells     = 0
        )
      }
      clearedState.updateCountries(daisyChain)
    }
    
    def updateCountry(changed: Country): GameState =
      this.copy(countries = changed :: (countries filterNot (_.name == changed.name)))
    
    def updateCountries(changed: List[Country]): GameState = {
      val updates = (changed map (c => (c.name -> c))).toMap
      this.copy(countries = countries map (c => updates.getOrElse(c.name, c)))
    }
    
    def adjustPrestige(amt: Int): GameState = this.copy(prestige = (prestige + amt) max 1 min 12)
    def adjustFunding(amt: Int): GameState  = this.copy(funding = (funding + amt) max 1 min 9)
    
    def addMarker(name: String): GameState = this.copy(markers = name :: markers)
    def removeMarker(name: String): GameState = this.copy(markers = markers filterNot (_ == name))
    
    // List of countries that are valid targets for War of Ideas
    def woiTargets: List[Country] = countries filter {
      case n: NonMuslimCountry => !(n.name == UnitedStates || n.name == Israel)
      case m: MuslimCountry    => m.isUntested || m.isNeutral || (m.isAlly && !m.isGood)
    }
    
    
    def prestigeModifier = prestige match {
      case x if x <  4 => -1
      case x if x <  7 =>  0
      case x if x < 10 =>  2
      case _           =>  2
    }
    
    // Returns the current gwot 
    // posture (Soft, Even, Hard)
    // value 0, 1, 2, 3
    def gwot: (String, Int) = {
      val value = (nonMuslims.filterNot(_.name == UnitedStates).foldLeft(0) { 
        case (v, c) if c.isHard => v + 1
        case (v, c) if c.isSoft => v - 1
        case (v, _) => v // Untested
      })
      val posture = if (value == 0) Even else if (value < 0) Soft else Hard
      (posture, value.abs min 3)
    }
    
    // 0, 1, 2, 3
    def gwotPenalty: Int = {
      gwot match {
        case (posture, value)  if posture != usPosture => value
        case _ => 0
      }
    }
    
    def troopCommitment = troopsAvailable match {
      case x if x <  5 => Overstretch
      case x if x < 10 => War
      case _           => LowIntensity
    }
    
    def prestigeLevel = prestige match {
      case x if x <  4 =>  Low
      case x if x <  7 => Medium
      case x if x < 10 => High
      case _           => VeryHigh
    }
    
    def fundingLevel = funding match {
      case x if x < 4 => Tight
      case x if x < 7 => Moderate
      case _          => Ample
    }
    
    def troopsAvailable  = 15 - offMapTroops - muslims.foldLeft(0) { (a, c) => a + c.troops }
    def militiaAvailable = 15 - muslims.foldLeft(0) { (a, c) => a + c.militia }
    
    def troopsOnMap   = muslims.foldLeft(0) { (a, m) => a + m.troops }
    def militiaOnMap  = muslims.foldLeft(0) { (a, m) => a + m.militia }
    
    // If the "Training Camps" marker is in a country we add 3 extra cells available cells.
    // (5 cells if the "Training Camps" is in a caliphate country.)
    // Training Camp cells are only useable when funding is as 9 or by event or civil war attrition.
    // If some of those cells are on the map and the training camp is removed, those
    // extra cells remain on the map until eliminated.
    // We use the trainingCampCells field to keep track of the training camp status:
    // inCamp -- the number of training camp cells currently in camp.
    //           when the mark is placed this will be set to 3 (or 5)
    // onMap  -- the number of training camp cell on the map.
    //           It is adjusted as cells are added to or removed from the map.
    // Training camp cells are always the last to be placed on the map and the first
    // to be removed.
    
    def trainingCamp        = muslims find (_.hasMarker("Training Camps")) map (_.name)
    def isTrainingCamp(name: String) = trainingCamp exists (_ == name)
    def trainingCampsInPlay = trainingCamp.nonEmpty
    def trainingCampCapacity= trainingCamp map (tc => if (isCaliphateMember(tc)) 5 else 3) getOrElse 0 
    def totalCellCapacity   = 15 + trainingCampCapacity
    def totalCellsInPlay    = 15 + trainingCampCells.inCamp + trainingCampCells.onMap
    def cellsOnMap          = countries.foldLeft(0) { (a, c) => a + c.totalCells }
    def cellsOnTrack        = {
      // Don't allow it to go negative due to camp cells that are on the map
      ((15 + trainingCampCapacity) - cellsOnMap - trainingCampCells.inCamp) max 0
    }
    
    // Cells available regardless of funding. For use by events.
    def cellsAvailable = cellsOnTrack + trainingCampCells.inCamp

    // Number of cells available for recruit operations.
    def cellsToRecruit = {
      if (funding == 9)
        cellsAvailable
      else
        fundingLevel match {
          case Tight    => (cellsOnTrack - 10) max 0
          case Moderate => (cellsOnTrack -  5) max 0
          case _        => cellsOnTrack
        }
    }
    def sleeperCellsOnMap = countries.foldLeft(0) { (sum, c) => sum + c.sleeperCells }
    
    def numGoodOrFair    = muslims count (c => c.isGood || c.isFair)
    def numPoorOrIslamic = muslims count (c => c.isPoor || c.isIslamistRule)
    def numIslamistRule  = muslims count (c => c.isIslamistRule)
    def oilBump(c: MuslimCountry) = if (c.oilProducer) eventParams.oilPriceSpikes else 0
    def goodResources =
      muslims.filter(_.isGood).foldLeft(0) { (a, c) => a + c.resources + oilBump(c) }
    def islamistResources = 
      muslims.filter(_.isIslamistRule).foldLeft(0) { (a, c) => a + c.resources + oilBump(c)} +
      (if (caliphateDeclared) 1 else 0)
    // Return true if any two Islamist Rule countries are adjacent.
    def islamistAdjacency: Boolean =
      muslims.filter(_.isIslamistRule).combinations(2).exists (xs => areAdjacent(xs.head.name, xs.last.name))
    
    // Remember, troops can ALWAYS deploy to the track with a 1 op card.
    def deployPossible(ops: Int): Boolean = deployTargets(ops).nonEmpty
    
    // Returns Some(deployFrom, deployTo), or None
    // Remember, troops can ALWAYS deploy to the track with a 1 op card.
    // But if that is the only valid destination, then do not include "track" in the
    // list of sources.
    def deployTargets(ops: Int): Option[(List[String], List[String])] = {
      val fromCountries = countryNames(muslims filter (_.canDeployFrom))
      val toCountries   = countryNames(muslims filter (_.canDeployTo(ops)))
      (fromCountries, toCountries) match {
        case (Nil, Nil )                        => None
        case (Nil, to  ) if troopsAvailable > 0 => Some("track"::Nil, to)
        case (from, Nil)                        => Some(from, "track"::Nil)
        case (from, to ) if troopsAvailable > 0 => Some("track"::from, "track"::to)
        case (from, to )                        => Some(from, "track"::to)
      }
    }
    
    def regimeChangeSources: List[String] = {
      val ms = countryNames(muslims filter (_.maxDeployFrom > 5))
      if (troopsAvailable > 5) "track" :: ms else ms
    } 
      
    def regimeChangeTargets: List[String] = countryNames(muslims filter (_.isIslamistRule))
      
    def regimeChangePossible(ops: Int) = 
      ops == 3 && usPosture == Hard && regimeChangeSources.nonEmpty && regimeChangeTargets.nonEmpty
  
    def withdrawFromTargets: List[String] = countryNames(muslims filter (m => m.inRegimeChange && m.troops > 0))
    
    def withdrawToTargets: List[String] = "track" :: countryNames(muslims filter (_.canDeployTo(3)))
      
    def withdrawPossible(ops: Int) = 
        ops == 3 && usPosture == Soft && withdrawFromTargets.nonEmpty

    def disruptAffectsPrestige(name: String): Boolean = getCountry(name) match {
      case m: MuslimCountry    => m.disruptAffectsPrestige
      case _: NonMuslimCountry => false
    }
    
    // Returns the losses that would occur iif this country is the 
    // target of a disrupt operation.
    // Some(Either((sleepers, actives), ())) or None
    def disruptLosses(name: String): Option[Either[(Int, Int), Unit]] = {
      val c = getCountry(name) 
      val maxLosses = c match {
        case m: MuslimCountry =>
          if ((m.totalTroopsAndMilitia) > 1 && (m.totalTroops > 0 || m.hasMarker("Advisors"))) 2 else 1
        case n: NonMuslimCountry => if (n.isHard) 2 else 1
      }
      if (c.totalCells > 0) {
        val sleepers = maxLosses min c.sleeperCells
        val actives = (maxLosses - sleepers) min c.activeCells
        Some(Left((sleepers, actives)))
      }
      else if (c.hasCadre)
        Some(Right())
      else
        None
    } 
    
    def disruptTargets(ops: Int): List[String] = {
      val muslimTargets = muslims.filter { m =>
        ops >= m.governance &&
        (m.hasCadre || m.totalCells > 0) && 
        (m.isAlly || (m.totalTroopsAndMilitia) > 1)
      }
      val nonMuslimTargets = nonMuslims.filter { n =>
        !n.iranSpecialCase &&
        ops >= n.governance &&
        (n.hasCadre || n.totalCells > 0) 
      }
      countryNames(muslimTargets ::: nonMuslimTargets) 
    }

    def alertPossible(ops: Int) = ops == 3 && alertTargets.nonEmpty
    
    def alertTargets: List[String] = countryNames(countries filter (_.hasPlots))
    
    def warOfIdeasTargets(ops: Int): List[String] = countryNames(countries filter (_.warOfIdeasOK(ops)))
    
    def recruitTargets: List[String] = countryNames(countries filter (_.recruitOK))
    
    def recruitPossible = cellsToRecruit > 0 && recruitTargets.nonEmpty
    
    def jihadTargets: List[String] = countryNames(muslims filter (_.jihadOK))
    def jihadPossible = jihadTargets.nonEmpty
    
    def majorJihadTargets(ops: Int) = countryNames(muslims filter (_.majorJihadOK(ops)))
    def majorJihadPossible(ops: Int) = majorJihadTargets(ops).nonEmpty
    
    def plotTargets: List[String] = {
      val muslimTargets = muslims filter (m => !m.isIslamistRule && m.totalCells > 0)
      val nonMuslimTargets = nonMuslims filter (_.totalCells > 0)
      countryNames(muslimTargets ::: nonMuslimTargets) 
    }
    def plotsAvailableWith(ops: Int) = availablePlots filter (_.opsToPlace <= ops)
    def plotPossible(ops: Int) = plotsAvailableWith(ops).nonEmpty && plotTargets.nonEmpty
    
    // --- Summaries --------------------------------------
    def cardPlaySummary: Seq[String] = {
      val b = new ListBuffer[String]
      b += s"Cards played this turn"
      b += separator()
      if (cardsPlayed.isEmpty)
        b += "none"
      else
        b ++= cardsPlayed.reverse map (_.toString)
      b.toList
    }
    
    def scenarioSummary: Seq[String] = {
      val b = new ListBuffer[String]
      b += s"Scenario: ${params.scenarioName}"
      b += separator()
      b += s"The Bot is playing the $botRole"
      b += (if (botRole == US) "US Resolve" else "Jihadist Ideology")
      for (difficulty <- params.botDifficulties)
        b += s"  $difficulty"
      b.toList
    }
      
    def scoringSummary: Seq[String] = {
      val b = new ListBuffer[String]
      val adjacency = humanRole match {
        case Jihadist => if (islamistAdjacency) "with adjacency" else "without adjacency"
        case US       => "adjacency not necessary"
      }
      b += "Current Score"
      b += separator()
      b += f"Good/Fair Countries   : $numGoodOrFair%2d | Good Resources   : $goodResources%2d"
      b += f"Poor/Islamic Countries: $numPoorOrIslamic%2d | Islamic Resources: $islamistResources%2d  ($adjacency)"
      b.toList
    }
    
    def worldPostureDisplay = {
      val (worldPosture, level) = gwot
      val levelDisp = if (worldPosture == Even) "" else level.toString
      s"$worldPosture $levelDisp"
    }
    
    def statusSummary: Seq[String] = {
      val activePlotCountries = countries filter (_.hasPlots)
      val b = new ListBuffer[String]
      b += "Status"
      b += separator()
      b += f"US posture      : $usPosture | World posture     : ${worldPostureDisplay}  (GWOT penalty $gwotPenalty)"
      b += f"US prestige     : $prestige%2d   | Jihadist funding  : $funding%2d"
      b += f"US reserves     : ${reserves.us}%2d   | Jihadist reserves : ${reserves.jihadist}%2d"
      b += f"Troops on track : $troopsAvailable%2d   | Troops off map    : $offMapTroops%2d"
      b += s"Troop commitment: $troopCommitment"
      b += separator()
      b += f"Cells on track  : $cellsOnTrack%2d   | Militia on track  : $militiaAvailable%2d"
      b += f"Cells in camp   : ${trainingCampCells.inCamp}%2d   | Camp cells on map : ${trainingCampCells.onMap}%2d" 
      b += f"Cells to recruit: ${cellsToRecruit}%2d   | Funding level     : ${fundingLevel}"
      b += separator()
      (trainingCamp, trainingCampCapacity) match {
        case (Some(c), 3) => b += s"Training camps  : $c (Capacity 3, non-Caliphate country)"
        case (Some(c), 5) => b += s"Training camps  : $c (Capacity 5, Caliphate country)"
        case _            => b += s"Training camps  : Not in play"
      }
      b += s"Markers         : ${if (markers.isEmpty) "none" else markers mkString ", "}"
      b += s"Lapsing         : ${if (cardsLapsing.isEmpty) "none" else cardNumsAndNames(cardsLapsing)}"
      b += s"1st plot        : ${firstPlotCard map cardNumAndName getOrElse "none"}"
      b += s"Resloved plots  : ${plotsDisplay(resolvedPlots, Jihadist)}"
      b += s"Available plots : ${plotsDisplay(availablePlots, humanRole)}"
      if (activePlotCountries.isEmpty)
        b += s"Active plots    : none"
      else {
        b += s"Active plots"
        val fmt = "  %%-%ds : %%s".format(activePlotCountries.map(_.name.length).max)
        for (c <- activePlotCountries)
          b += fmt.format(c.name, mapPlotsDisplay(c.plots, humanRole))
      }
      b.toList
    }
    
    // If show all is false, then some attriubtes will not be displayed
    // if they have value of zero.
    def countrySummary(name: String, showAll: Boolean = false): Seq[String] = {
      val b = new ListBuffer[String]
      val items = new ListBuffer[String]
      def item(num: Int, label: String, pluralLabel: Option[String] = None): Unit = {
        if (showAll || num > 0)
          items += amountOf(num, label, pluralLabel)
      }
      def addItems(): Unit = if (items.nonEmpty) b += s"  ${items mkString ", "}"

      getCountry(name) match {
        case n: NonMuslimCountry =>
          val posture = if (n.iranSpecialCase) "" else s", ${n.posture}"
          b += s"$name -- ${govToString(n.governance)}$posture"
          item(n.activeCells, "Active cell")
          item(n.sleeperCells, "Sleeper cell")
          if (n.hasCadre)
            items += "Cadre marker"
          else if (showAll)
            items += "No Cadre marker"
          addItems()
          if (showAll || n.hasPlots)
            b += s"  Plots: ${mapPlotsDisplay(n.plots, humanRole)}"
          if (showAll || n.markers.size > 0)
            b += s"  Markers: ${markersString(n.markers)}"
          if (n.wmdCache > 0)
            b += s"  WMD cache: ${amountOf(n.wmdCache, "WMD plot")}"
          

        case m: MuslimCountry =>
          val gov = if (m.isUntested) "Untested" else s"${govToString(m.governance)} ${m.alignment}"
          val res = amountOf(m.resources, "resource")
          val oil = if (m.oilProducer) ", Oil producer" else ""
          b += s"$name -- $gov, $res$oil"
          item(m.activeCells, "Active cell")
          item(m.sleeperCells, "Sleeper cell")
          if (m.hasCadre)
            items += "Cadre marker"
          else if (showAll)
            items += "No Cadre marker"
          item(m.troops, "Troop")
          item(m.militia, "Militia", Some("Militia"))
          addItems()
            
          items.clear
          item(m.aidMarkers, "Aid marker")
          item(m.awakening, "Awakening marker")
          item(m.reaction, "Reaction marker")
          if (m.besiegedRegime)
            items += "Besieged regime"
          else if (showAll)
            items += "No Besieged regime"
          addItems()
            
          items.clear
          if (showAll || m.inRegimeChange)
            items += s"Regime Change (${m.regimeChange})"
          if (m.civilWar)
            items += "Civil War"
          else if (showAll)
            items += "No Civil War"
          if (m.caliphateCapital)
            items += "Caliphate Capital"
          else if (isCaliphateMember(m.name))
            items += "Caliphate member"
          else if (showAll)
            items += "Not Caliphate member"
          addItems()
          if (showAll || m.hasPlots)
            b += s"  Plots: ${mapPlotsDisplay(m.plots, humanRole)}"
          if (showAll || m.markers.size > 0)
            b += s"  Markers: ${markersString(m.markers)}"
          if (m.wmdCache > 0)
            b += s"  WMD cache: ${amountOf(m.wmdCache, "WMD plot")}"
      }
      b.toList
    }
    
    def caliphateSummary: Seq[String] = {
      val b = new ListBuffer[String]
      b += "Caliphate"
      b += separator()
      caliphateCapital match {
        case Some(capital) =>
          // Add the capital first
          b += s"${capital}  (Capital)"
          for (member <- caliphateDaisyChain(capital).sorted.filterNot(_ == capital))
            b += member
          log("Reminder: cells in Caliphate countries are always active")
        case None =>
          b += "There is no Caliphate declared"
      }
      b.toList
    }
    
    def civilWarSummary: Seq[String] = {
      val b = new ListBuffer[String]
      b += "Civil Wars"
      b += separator()
      val civilWars = (muslims filter (_.civilWar)).toList
      if (civilWars.isEmpty)
        b += "There are no counties in civil war"
      else
        b += civilWars map (_.name) mkString ", "
      b.toList
    }
  }
  
  def initialGameState(
    scenario: Scenario,
    humanRole: Role,
    humanAutoRoll: Boolean,
    botDifficulties: List[BotDifficulty]) = GameState(
      GameParameters(scenario.name, scenario.cardDeckName, humanRole,humanAutoRoll, botDifficulties),
      0, // Turn number, zero indicates start of game.
      scenario.prestige,
      scenario.usPosture,
      scenario.funding,
      scenario.countries,
      scenario.markers.sorted,
      scenario.availablePlots.sorted)
  
  
  // Global variables
  var game = initialGameState(new Awakening2010, US, true, Muddled :: Nil)
  
  // A history of the card plays for the current game turn, most recent first.
  // The current game state is NOT in the list. 
  // Allows user to roll back to a previous card play in the current turn.
  var previousCardPlays = List.empty[GameState]
  
  // The history of game turns, most recent first
  // The current game state is NOT in the list.  It is added at the
  // end of the turn.
  // Allows user to roll back to a previous turn.
  var previousTurns = List.empty[GameState]
  
  
  // If num is 1 use the name as is
  // otherwise either use the plural if given or add an 's' to the name.
  def amountOf(num: Int, name: String, plural: Option[String] = None) = 
    (num, plural) match {
      case (1, _)            => s"$num $name"
      case (_, Some(plural)) => s"$num $plural"
      case _                 => s"$num ${name}s"
    }
    
  def opsString(num: Int) = amountOf(num, "Op")
  
  def diceString(num: Int) = if (num == 1) "1 die" else s"$num dice"
  
  // Returns comma separated string with last choice separated by "and"
  //    List("apples")                      => "apples"
  //    List("apples", "oranges")           => "apples and oranges"
  //    List("apples", "oranges", "grapes") => "apples, oranges and grapes"
  def andList(x: Seq[Any]) = x match {
    case Seq()     => ""
    case Seq(a)    => a.toString
    case Seq(a, b) => s"${a.toString} and ${b.toString}"
    case _         => x.dropRight(1).mkString(", ") + ", and " + x.last.toString
  }
  
  // Returns comma separated string with last choice separated by "or"
  //    List("apples")                      => "apples"
  //    List("apples", "oranges")           => "apples or oranges"
  //    List("apples", "oranges", "grapes") => "apples, oranges or grapes"
  def orList(x: Seq[Any]) = x match {
    case Seq()     => ""
    case Seq(a)    => a.toString
    case Seq(a, b) => s"${a.toString} or ${b.toString}"
    case _         => x.dropRight(1).mkString(", ") + ", or " + x.last.toString
  }
  
  // Find a match for the given string in the list of options.
  // Any unique prefix of the given options will succeed.
  def matchOne(s: String, options: Seq[String], abbreviations: Map[String, String] = Map.empty): Option[String] = {
    // Filter out any abbreviations that do not have a match with one of the options.
    val abbr = abbreviations filter { case (_, name) => options contains name }
    // When showing the list of options to the user, we want to group
    // all abbreviations with the word that they represent.
    val displayList = {
      var associations = Map.empty[String, Set[String]].withDefaultValue(Set.empty)
      for ((a, o) <- abbr)
        associations += o -> (associations(o) + a)
      options map {
        case o if associations(o).nonEmpty => o + associations(o).toList.sorted.mkString(" (", ",", ")")
        case o => o
      }  
    }
    if (s == "?") {
      println(s"Enter one of:\n${orList(displayList)}")
      None
    }
    else {
      val normalizedAbbreviations = for ((a, v) <- abbr) yield (a.toLowerCase, v)  
      val normalized = (options ++ abbr.keys) map (_.toLowerCase)
      (normalized.distinct filter (_ startsWith s.toLowerCase)) match {
        case Seq() =>
          println(s"'$s' is not valid. Must be one of:\n${orList(displayList)}")
          None
        case Seq(v)  =>
          normalizedAbbreviations.get(v) match {
            case Some(opt) => Some(opt)
            case None      => Some(options(normalized.indexOf(v)))
          }
        
        case many if many exists (_ == s) =>
          normalizedAbbreviations.get(s) match {
            case Some(opt) => Some(opt)
            case None      => Some(options(normalized.indexOf(s)))
          }
      
        case ambiguous =>
          println(s"'$s' is ambiguous. (${orList(ambiguous)})")
          None
      }
    }
  }
    
  def askOneOf(prompt: String, options: Seq[Any], initial: Option[String] = None, 
               allowNone: Boolean = false, allowAbort: Boolean = true, 
               abbr: Map[String, String] = Map.empty): Option[String] = {
    val choices = if (allowAbort) options ++ List(AbortCard) else options
    def testResponse(response: Option[String]): Option[String] = {
      response flatMap (s => matchOne(s.trim, choices map (_.toString), abbr)) match {
        case None =>
          readLine(prompt) match {
            case null | "" if allowNone => None
            case null | ""              => testResponse(None)
            case input                  => testResponse(Some(input))
          }
        case Some(AbortCard) if allowAbort => 
          if (askYorN("Really abort (y/n)? ")) throw AbortCardPlay else testResponse(None)
        case s => s
      }
    }
    testResponse(initial)
  }
  
  def askYorN(prompt: String): Boolean = {
    def testResponse(r: String): Option[Boolean] = {
      if (r == null)
        None
      else
        r.trim.toLowerCase match {
          case "n" | "no"  => Some(false)
          case "y" | "yes" => Some(true)
          case _           => None
        }
    }
    
    testResponse(readLine(prompt)) match {
      case Some(result) => result
      case None         => askYorN(prompt)
    }
  }


  def askInt(prompt: String, low: Int, high: Int, default: Option[Int] = None, allowAbort: Boolean = true): Int = {
    assert(low <= high, "askInt() low cannot be greater than high")
    if (low == high)
      low
    else {
      val choices = (low to high).toList
      default match {
        case Some(d) =>
          val p = if (choices.size > 6)
            "%s (%d - %d) Default = %d: ".format(prompt, choices.head, choices.last, d)
          else
            "%s (%s) Default = %d: ".format(prompt, orList(choices), d)
          askOneOf(p, choices, allowNone = true, allowAbort = allowAbort) map (_.toInt) match {
            case None    => d
            case Some(x) => x
          }
        case None => 
          val p = if (choices.size > 6)
            "%s (%d - %d): ".format(prompt, choices.head, choices.last)
          else
            "%s (%s): ".format(prompt, orList(choices))
          (askOneOf(p, choices, None, allowAbort = allowAbort) map (_.toInt)).get
      }
    }
  }

  def askCountry(prompt: String, candidates: List[String], allowAbort: Boolean = true): String = {
    assert(candidates.nonEmpty, s"askCountry(): list of candidates cannot be empty")
    // If only one candidate then don't bother to ask
    candidates match {
      case x :: Nil => println(s"$prompt $x"); x
      case xs       => askOneOf(prompt, xs, allowAbort = allowAbort, abbr = CountryAbbreviations).get
    }
  }
  
  // Returns (actives, sleepers)
  def askCells(countryName: String, numCells: Int, sleeperFocus: Boolean = true): (Int, Int) = {
    val c = game.getCountry(countryName)
    val maxCells = numCells min c.totalCells
    
    if (maxCells == c.totalCells) (c.activeCells, c.sleeperCells)
    else if (c.activeCells  == 0) (0, maxCells)
    else if (c.sleeperCells == 0) (maxCells, 0)
    else {
      val (a, s) = (amountOf(c.activeCells, "active cell"), amountOf(c.sleeperCells, "sleeper cell"))
      println(s"$countryName has $a and $s")
      
      if (maxCells == 1) {
        val prompt = if (sleeperFocus) "Which cell (sleeper or active)? "
        else "Which cell (active or sleeper)? "
        askOneOf(prompt, List("active", "sleeper")) match {
          case Some("active") => (1, 0)
          case _              => (0, 1)
        }
      }
      else {
        if (sleeperFocus) {
          val smax     = maxCells min c.sleeperCells
          val prompt   = s"How many sleeper cells (Default = $smax): "
          val sleepers = askInt(prompt, 1, smax, Some(smax))
          (maxCells - sleepers , sleepers)
        }
        else {
          val amax    = maxCells min c.activeCells
          val prompt  = s"How many active cells (Default = $amax): "
          val actives = askInt(prompt, 1, amax, Some(amax))
          (actives, maxCells - actives)
        }
      }
    }
  }
  
  // Ask the user to select a number of available plots
  def askAvailablePlots(num: Int, ops: Int): List[Plot] = {
    val plotList = for ((p, i) <- game.availablePlots.sorted.zipWithIndex if ops >= p.opsToPlace) yield
      (i.toString -> p)
    val plotMap = ListMap(plotList:_*)
    val choices = plotMap map { case (key, plot) => (key -> plot.name)}
    println(s"Select ${amountOf(num, "available plot")}:")
    askMenu(choices, num) map plotMap
  }
  
  def askCardNumber(prompt: String, 
                    initial: Option[String] = None,
                    allowNone: Boolean = true,
                    only3Ops: Boolean = false,
                    removedLapsingOK: Boolean = false): Option[Int] = {
    def checkNumber(input: String): Boolean = input match {
      case INTEGER(num) if deck.isValidCardNumber(num.toInt) =>
        if (only3Ops && deck(num.toInt).ops != 3) {
          println("You must enter a 3 Ops card")
          false
        }
        else
          true
      case _ => 
        println(s"'$input' is not a valid card number")
        false
    }
    def testResponse(response: Option[String]): Option[Int] = {
      def outOfPlay(x: String): Boolean = {
        val num  = x.trim.toInt
        val name = cardNumAndName(num)
        removedLapsingOK match {
          case false if game.cardRemoved(num) =>
            println(s"$name has been removed from from the game")
            true
          case false if game.cardLapsing(num) =>
            println(s"$name is currently lapsing")
            true
          case false if game.isFirstPlot(num) =>
            println(s"$name is currently the first plot card")
            true
          case _ => false
        }
      }
      response filter checkNumber match {
        case None =>
          readLine(prompt) match {
            case null | "" if allowNone => None
            case null | ""              => testResponse(None)
            case input                  => testResponse(Some(input))
          }
        case Some(n) if outOfPlay(n) =>
          if (askYorN("Play it anyway (y/n)? ")) Some(n.trim.toInt) else None
        case n => n map (_.trim.toInt)
      }
    }
    testResponse(initial)
  }
  
  // Present a numbered menu of choices
  // Allow the user to choose 1 or more choices and return
  // a list of keys to the chosen items.
  // Caller should println() a brief description of what is being chosen.
  def askMenu(items: ListMap[String, String], numChoices: Int = 1, repeatsOK: Boolean = false): List[String] = {
    def nextChoice(num: Int, itemsRemaining: ListMap[String, String]): List[String] = {
      if (itemsRemaining.isEmpty || num > numChoices)
        Nil
      else if (itemsRemaining.size == 1)
        itemsRemaining.keys.head :: Nil
      else {
        println(separator())
        val indexMap = (itemsRemaining.keys.zipWithIndex map (_.swap)).toMap
        for ((key, i) <- itemsRemaining.keysIterator.zipWithIndex)
          println(s"${i+1}) ${itemsRemaining(key)}")
        val prompt = if (numChoices > 1) s"${ordinal(num)} Selection: "
        else "Selection: "
        val choice = askOneOf(prompt, 1 to itemsRemaining.size).get.toInt
        println()
        val index  = choice - 1
        val key    = indexMap(index)
        val remain = if (repeatsOK) itemsRemaining else itemsRemaining - key
        indexMap(index) :: nextChoice(num + 1, remain)
      }
    }
    nextChoice(1, items)
  }
  
  // Name of country and number of items in that country.
  case class MapItem(country: String, num: Int)
  // This method is used when a number of items such as troops, sleeper cells, etc.
  // must be selected from the map.
  // targets     - a list MapItem instances where items can be selected.
  // numItems    - the number that must be selectd
  // name        - should be the singular name of the item being selected (troop, sleeper cell)
  // Returns the list if MapItems with the results.
  // Before calling this, the caller should print a line to the terminal describing
  // what is being selected.
  def askMapItems(targets: List[MapItem], numItems: Int, name: String): List[MapItem] = {
    targets match {
      case _  if numItems == 0 => Nil  // The required number have been selected
      case target :: Nil       => MapItem(target.country, numItems) :: Nil // Last country remaining
      case _ =>
        val maxLen = (targets map (_.country.length)).max
        val fmt = "%%-%ds  (%%s)".format(maxLen)
        def disp(t: MapItem) = {fmt.format(t.country, amountOf(t.num, name))}
        val opts = targets.map(t => t.country -> disp(t))
        val choices = ListMap(opts:_*)
        println()
        println(s"${amountOf(numItems, name)} remaining, choose country")
        // println("Choose next country to activate sleeper cells")
        val country = askMenu(choices).head
        val limit   = (targets find (_.country == country)).get.num min numItems
        val num     = askInt("How many", 1, limit)
        val newTargets = targets map { t => 
          if (t.country == country) t.copy(num = t.num - num) else t
        } filterNot (_.num == 0)
        MapItem(country, num) :: askMapItems(newTargets, numItems - num, name)
    }
  }
    
  // Use the random Muslim table.
  val randomMuslimTable = Vector(
    Vector(Morocco,        Syria,       ""),
    Vector(AlgeriaTunisia, Jordan,      IndonesiaMalaysia),
    Vector(Libya,          Turkey,      Lebanon),
    Vector(Egypt,          SaudiArabia, Iraq),
    Vector(Sudan,          GulfStates,  Afghanistan),
    Vector(Somalia,        Yemen,       Pakistan))
    
  def randomMuslimCountry: MuslimCountry = {
    val row = dieRoll - 1        // tan die
    val col = (dieRoll - 1) / 2  // black die
    if (row == 0 && col == 2)
      secondaryRandomMuslimCountry
    else
      game.getMuslim(randomMuslimTable(row)(col))
  }
  
  // Secondary, Muslim table
  // Iran and Nigeria may not yet have become Muslim countries.
  // If they are selected and non Muslim, then we simply roll again.
  def secondaryRandomMuslimCountry: MuslimCountry = {
    val iranIsMuslim    = game.isMuslim(Iran)
    val nigeriaIsMuslim = game.isMuslim(Nigeria)
    @tailrec def rollOnTable: MuslimCountry = {
      dieRoll match {
        case 1 | 2                    => game.getMuslim(CentralAsia)
        case 3 if iranIsMuslim        => game.getMuslim(Iran)
        case 4                        => game.getMuslim(Mali)
        case 5 | 6 if nigeriaIsMuslim => game.getMuslim(Nigeria)
        case _                        => rollOnTable
      }
    }
    rollOnTable
  }
  
  def randomShiaMixList: List[MuslimCountry] = {
    val xs = List(Syria, SaudiArabia, Turkey, Iraq, GulfStates, Yemen, Pakistan, Lebanon, Afghanistan)
    (if (game.isMuslim(Iran)) Iran :: xs else xs) map game.getMuslim
  }  
    
  def randomShiaMixCountry: MuslimCountry = {
    val muslimKey = List(dieRoll, dieRoll, dieRoll).sum match {
      case 3 | 4 | 5 | 6                 => Syria
      case 7 if game.isMuslim(Iran)      => Iran
      case 7                             => Syria
      case 8                             => SaudiArabia
      case 9                             => Turkey
      case 10                            => Iraq
      case 11                            => GulfStates
      case 12                            => Yemen
      case 13                            => Pakistan
      case 14                            => Lebanon
      case _  /* 15 | 16 | 17 | 18 */    => Afghanistan
    }
    game.getMuslim(muslimKey) 
  }
  
  def randomSchengenCountry: NonMuslimCountry = {
    dieRoll match {
      case 1 => game.getNonMuslim(Scandinavia)
      case 2 => game.getNonMuslim(Benelux)
      case 3 => game.getNonMuslim(Germany)
      case 4 => game.getNonMuslim(France)
      case 5 => game.getNonMuslim(Spain)
      case _ => game.getNonMuslim(Italy)
    }
  }
  
  // When selecting a country for a posture roll the bot will 
  // select first among those with the same posture as the US,
  // then among untested.
  def botCountryForPostureRoll(include: Set[String] = Set.empty, exclude: Set[String] = Set.empty): Option[String] = {
    def allowed(name: String) = (include.isEmpty || include(name)) && !exclude(name)
    val possibles = game.nonMuslims filter (_.canChangePosture)
    val candidates = List(
      possibles filter (c=> c.posture == game.usPosture && allowed(c.name)) map (_.name),
      possibles filter (c=> c.isUntested && allowed(c.name)) map (_.name),
      possibles filter (c=> allowed(c.name)) map (_.name)
    ).dropWhile(_.isEmpty)
    candidates match {
      case Nil   => None
      case x::xs => Some(shuffle(x).head)
    }
  }
  
  def addOpsTarget(name: String): Unit = {
    val targets = game.targetsThisCard
    game = game.copy(targetsThisCard = targets.copy(ops = targets.ops + name))
  }
  
  def addTestedOrImproved(name: String): Unit = {
    val targets = game.targetsThisCard
    game = game.copy(targetsThisCard = targets.copy(testedOrImproved = targets.testedOrImproved + name))
  }
  
  def addEventTarget(names: String*): Unit = {
    val targets = game.targetsThisCard
    game = game.copy(targetsThisCard = targets.copy(event = targets.event ++ names))
  }
  
  // If the country is untested, test it and return true
  // otherwise return false.
  def testCountry(name: String): Boolean = {
    val country = game.getCountry(name)
    if (country.isUntested) {
      addTestedOrImproved(name)
      country match {
        case m: MuslimCountry    =>
          val newGov = if (dieRoll < 5) Poor else Fair
          game = game.updateCountry(m.copy(governance = newGov, alignment = Neutral))
          log(s"${m.name} tested: Set to ${govToString(newGov)} Neutral")
          
        case n: NonMuslimCountry =>
          val newPosture = if (dieRoll < 5) Soft else Hard
          game = game.updateCountry(n.copy(posture = newPosture))
          log(s"${n.name} tested: Set posture to $newPosture")
          logWorldPosture()
      }
      true
    }
    else
      false
  }
  
  def rollUSPosture(): Unit = {
    val die = dieRoll
    val newPosture = if (die + 1 > 4) Hard else Soft
    log(s"Roll United States posture")
    log(s"Die roll: $die")
    log("+1: Rolling US posture")
    log(s"Modified roll: ${die + 1}")
    setUSPosture(newPosture)
  }
  
  def setUSPosture(newPosture: String): Unit = {
    if (game.usPosture == newPosture)
      log(s"US posture remains $newPosture")
    else {
      game = game.copy(usPosture = newPosture)
      log(s"Set US posture to $newPosture")
      logWorldPosture()
    }
  }
  
  def rollCountryPosture(name: String): Unit = {
    assert(name != UnitedStates, "rollCountryPosture() called for United States.  Use RollUSPosture()")
    val n = game.getNonMuslim(name)
    val die = dieRoll
    log(s"Roll posture of $name")
    log(s"Die roll: $die")
    val newPosture = if (die < 5) Soft else Hard
    if (n.posture == newPosture)
      log(s"The posture of $name remains $newPosture")
    else {
      game = game.updateCountry(n.copy(posture = newPosture))
      log(s"Set the posture of $name to $newPosture")
      logWorldPosture()
    }
  }
  
  
  def modifyWoiRoll(die: Int, m: MuslimCountry, ignoreGwotPenalty: Boolean = false, silent: Boolean = false): Int = {
    def logNotZero(value: Int, msg: String): Unit =
      if (!silent && value != 0) log(f"$value%+2d: $msg")
    val prestigeMod = game.prestige match {
      case x if x < 4  => -1
      case x if x < 7  =>  0
      case x if x < 10 =>  1
      case _           =>  2
    }
    val shiftToGoodMod   = if (m.isAlly && m.isFair) -1 else 0
    val gwotMod          = if (ignoreGwotPenalty) 0 else -game.gwotPenalty
    val aidMod           = m.aidMarkers
    val adjToGoodAllyMod = if (game.adjacentToGoodAlly(m.name)) 1 else 0
    val awakeningMod     = m.awakening
    val reactionMod      = -m.reaction
    logNotZero(prestigeMod,      "Prestige")
    logNotZero(shiftToGoodMod,   "Shift to Good governance")
    logNotZero(gwotMod,          "GWOT penalty")
    logNotZero(aidMod,           "Aid")
    logNotZero(adjToGoodAllyMod, "Adjacent to Good Ally")
    logNotZero(awakeningMod,     "Awakening")
    logNotZero(reactionMod,      "Reaction")
    val modRoll = die + (prestigeMod + shiftToGoodMod + gwotMod + aidMod + adjToGoodAllyMod + awakeningMod + reactionMod)
    val anyMods = (prestigeMod.abs + shiftToGoodMod.abs + gwotMod.abs + aidMod.abs + 
                   adjToGoodAllyMod.abs + awakeningMod.abs + reactionMod.abs) > 0
    if (!silent && anyMods)
      log(s"Modified roll: $modRoll")
    modRoll
  }
  
  def modifyJihadRoll(die: Int, m: MuslimCountry, silent: Boolean = false): Int = {
    def logNotZero(value: Int, msg: String): Unit =
      if (!silent && value != 0) log(f"$value%+2d: $msg")
    val awakeningMod   = m.awakening
    val reactionMod    = -m.reaction
    logNotZero(awakeningMod,   "Awakening")
    logNotZero(reactionMod,    "Reaction")
    val modRoll = die + (awakeningMod + reactionMod)
    if (!silent && (awakeningMod.abs + reactionMod.abs) > 0)
      log(s"Modified roll: $modRoll")
    modRoll
  }

  def askDeclareCaliphate(capital: String): Unit = {
    if (!game.caliphateDeclared && 
        askYorN(s"Do you wish to declare a Caliphate with $capital as the the Captial (y/n)? "))
      declareCaliphate(capital)
  }

  // Throws an exception if a Caliphate already exists
  def declareCaliphate(capital: String): Unit = {
    assert(!game.caliphateDeclared, "declareCaliphate() called and a Caliphate Capital already on the map")
    log(s"A Caliphate is declared with $capital as the Capital")
    setCaliphateCapital(capital)
    increaseFunding(2)
    logSummary(game.caliphateSummary)
    
  }
  
  def setCaliphateCapital(name: String): Unit = {
    assert(game.isMuslim(name), s"setCaliphateCapital() called on non-muslim country: $name")
    val capital = game.getMuslim(name);
    assert(capital.caliphateCandidate, s"setCaliphateCapital() called on invalid country: $name")
    // First make sure there is no country marked as the capital
    game = game.updateCountries(game.muslims.map(_.copy(caliphateCapital = false)))
    val daisyChain = game.caliphateDaisyChain(name).map { memberName =>
      val member = game.getMuslim(memberName)
      if (member.sleeperCells > 0)
        log(s"Activate all sleeper cells in $memberName")
      member.copy(
        caliphateCapital = member.name == name,
        activeCells      = member.totalCells,  // All cells go active in caliphate members
        sleeperCells     = 0
      )
    }
    game = game.updateCountries(daisyChain)
  }
  
  
  // Important: Assumes that the game has already been updated, such that the
  // previousCapital is no longer a caliphateCandidate! Othewise the caliphate
  // size comparisons for the the new capital candidate would include the old
  // capital which is wrong.
  def displaceCaliphateCapital(previousCapital: String): Unit = {
    // Used when the Bot is selecting new caliphate capital
    case class CaliphateCapitalCandidate(m: MuslimCountry) {
      val size = game.caliphateDaisyChain(m.name).size
    }
    // We define this outside of the CaliphateCapitalCandidateOrdering so it can
    // easily be called by the displaceCaliphateCapital() method.
    //
    // Sort first by daisy chain size large to small
    // then by non Ally before Ally
    // then by worse governance before better governance
    // then by worst WoI drm before better WoI drm
    def compareCapitalCandidates(x: CaliphateCapitalCandidate, y: CaliphateCapitalCandidate) = {
      if (x.size != y.size)                      y.size compare x.size         // y first: to sort large to small.
      else if (x.m.isAlly != y.m.isAlly)         x.m.isAlly compare y.m.isAlly // x first: nonAlly before Ally
      else if (x.m.governance != y.m.governance) y.m.governance compare x.m.governance // y first: because Good < Islamist Rule
      else {
        val (xMod, yMod) = (modifyWoiRoll(1, x.m, false, true), modifyWoiRoll(1, y.m, false, true))
        xMod compare yMod
      }
    }
    implicit val CaliphateCapitalCandidateOrdering = new Ordering[CaliphateCapitalCandidate] {
      def compare(x: CaliphateCapitalCandidate, y: CaliphateCapitalCandidate) = compareCapitalCandidates(x, y)
    }
    // Caliphate capital displaced.  Attempt to move it to adjacent caliphate country.
    val adjacents = game.adjacentMuslims(previousCapital) filter (_.caliphateCandidate)
    log()
    log("The Caliphate capital has been displaced!")
    log(separator())
    if (adjacents.size == 0) {
      log(s"There are no adjacent Caliphate candidates, remove Caliphate capital")
      decreaseFunding(2)
      increasePrestige(2)
    }
    else {
      // If Jihadist is human, ask them to pick the new capital.
      // If Jihadist is a bot, the country that connects to the 
      // largest number of daisy chained caliphate memeber.
      // Then pick one at random among any ties.
      val newCapitalName = if (adjacents.size == 1)
        adjacents.head.name
      else if (game.humanRole == Jihadist) {
        val choices = adjacents map (_.name)
        askCountry(s"Choose new Caliphate capital (${orList(choices)}): ", choices, allowAbort = false)
      }
      else {
        // The Bot pick the best candidate for the new capital base on
        // the set of conditons outlined by compareCapitalCandidates().
        // We sort the best to worst.  If more than one has the best score then 
        // we choose randomly among them.
        val sorted = adjacents.map(CaliphateCapitalCandidate).sorted
        val best   = sorted.takeWhile(compareCapitalCandidates(_, sorted.head) == 0) map (_.m.name)
        shuffle(best).head
      }
      game = game.setCaliphateCapital(newCapitalName)
      log(s"Move Caliphate capital to ${newCapitalName}")
      decreaseFunding(1)
      increasePrestige(1)
      logSummary(game.caliphateSummary)
    }
  }
  
  // Check to see if there are any sleeper cells in any caliphate members
  // and flip them to active
  def flipCaliphateSleepers(): Unit = {
    for {
      capital <- game.caliphateCapital
      member  <- game.caliphateDaisyChain(capital)
      m       =  game.getMuslim(member)
      if m.sleeperCells > 0
    } {
      game = game.updateCountry(m.copy(sleeperCells = 0, activeCells = m.totalCells))
      log(s"$member is now a caliphate member, flip all sleeper cells to active")
    }
  }
  
  // Convergence
  def performConvergence(forCountry: String, awakening: Boolean): Unit = {
    def randomConvergenceTarget: MuslimCountry = {
      @tailrec def getTarget: MuslimCountry = {
        val rmc = randomMuslimCountry
        if (rmc.canTakeAwakeningOrReactionMarker)
          rmc
        else 
          shuffle(game.adjacentMuslims(rmc.name) filter (_.canTakeAwakeningOrReactionMarker)) match {
            case Nil    => getTarget  // Try again
            case x :: _ => x
          }
      }
      getTarget
    }
    
    val rmc = randomConvergenceTarget
    if (awakening) {
      game = game.updateCountry(rmc.copy(awakening = rmc.awakening + 1))
      log(s"Convergence for ${forCountry}: Add 1 awakening marker to ${rmc.name}")
    }
    else {
      game = game.updateCountry(rmc.copy(reaction = rmc.reaction + 1))
      log(s"Convergence for ${forCountry}: Add 1 reaction marker to ${rmc.name}")
    }
  }
  
  def inspect[T](name: String, value: T): T = {
    println(s"DEBUG: $name == ${value.toString}")
    value
  }
  
  def pause() {
      readLine("Continue ↩︎ ")
  }
  
  var indentation = 0
  
  // Add a two spaces to the current indent.
  def indentLog(): Unit = indentation += 2
  def outdentLog(): Unit = indentation = (indentation - 2) max 0
  def clearIndent(): Unit = indentation = 0
  
  // Print the line to the console and save it in the game's history.
  def log(line: String = ""): Unit = {
    val indentedLine = (" " * indentation) + line
    println(indentedLine)
    game = game.copy(history = game.history :+ indentedLine)
  }
  
  def logAdjustment(name: String, oldValue: Any, newValue: Any): Unit = {
    def normalize(value: Any) = value match {
      case None                       => "none"
      case Some(x)                    => x.toString.trim
      case true                       => "yes"
      case false                      => "no"
      case s: Seq[_] if s.isEmpty     => "none"
      case s: Seq[_]                  => s map (_.toString) mkString ", "
      case x if x.toString.trim == "" => "none"
      case x                          => x.toString.trim
    }
    log(s"$name adjusted from [${normalize(oldValue)}] to [${normalize(newValue)}]")
  }
    
  def logAdjustment(countryName: String, attributeName: String, oldValue: Any, newValue: Any): Unit =
    logAdjustment(s"$countryName: $attributeName", oldValue, newValue)
  
  def logCardPlay(player: Role, card: Card): Unit = {
    val opponent = if (player == US) Jihadist else US
    val playable = card.eventIsPlayable(player)  
    val trigger  = card.eventWillTrigger(opponent)
    val postfix = if (playable)
      s"(The ${card.association} event is playable)"
    else if (card.association == opponent && game.humanRole == player)
      s"(The ${card.association} event will ${if (trigger) "" else "not "}be triggered)"
    else 
      s"(The ${card.association} event is not playable)"
    
    log(s"$player plays $card  $postfix")
  }

  def separator(length: Int = 52, char: Char = '-'): String = char.toString * length

  def markersString(markers: List[String]): String = if (markers.isEmpty)
    "none"
  else
    markers mkString ", "

  // Return a sorted list of the names of the given countries
  def countryNames(candidates: List[Country]) = (candidates map (_.name)).sorted

  // Get ordinal number.  Good for 1 to 20.
  def ordinal(i: Int): String = i match {
    case 1 => "1st"
    case 2 => "2nd"
    case 3 => "3rd"
    case x if x > 20 => throw new IllegalArgumentException("ordinal() only good for numbers <= 20")
    case x => s"${x}th"
  }

  // Sorts a list column wise.  Returns a list of rows where
  // eash row is a string with the items of that row lined up
  // with a minimum of two spaces separating the columns.
  def columnFormat(list: List[String], numCols: Int): Seq[String] = {
    def padLeft(s: String, width: Int) = s + (" " * (width - s.length))
    val numRows = (list.size + numCols - 1) / numCols
    def colsInRow(row: Int) = {
      val mod = list.size % numCols
      if (mod == 0 || row < numRows - 1) numCols else mod
    }
    val rows      = Array.fill(numRows)(new ListBuffer[String])
    val colWidths = Array.fill(numCols)(0)
    var (row, col) = (0, 0)
    for (entry <- list.sorted) {
      rows(row) += entry
      colWidths(col) = colWidths(col) max entry.length
      if (row + 1 == numRows || col >= colsInRow(row + 1) ) {
        row = 0; col += 1
      }
      else
        row += 1
    }
    
    rows map { entries =>
      (entries.toList.zipWithIndex map { case (entry, col) => 
        padLeft(entry, colWidths(col))
      }).mkString("  ")
    }
  }
  
    
  def logWorldPosture(): Unit = {
    log(s"World Posture is ${game.worldPostureDisplay}  (GWOT penalty ${game.gwotPenalty})")
  }
  

  def printSummary(summary: Seq[String]): Unit = {
    println()
    summary foreach println
  }
  
  def logSummary(summary: Seq[String]): Unit = {
    log()
    summary foreach log
  }
    
  // Save the current game state.
  def saveGameState(filename: String): Unit = {
    // To be done.
  }
  
  // Display a list of what needs to be done to get the game board into
  // the proper state when going from one state to another.
  def displayGameStateDifferences(from: GameState, to: GameState, heading: String = ""): Unit = {
    def show(oldValue: Any, newValue: Any, msg: String) =
      if (oldValue != newValue) println(msg)
    
    if (heading.nonEmpty) {
      println()
      println("The following changes should be made to the game board")
      println(separator())
    }
    show(from.params.botDifficulties, to.params.botDifficulties, 
        "Set the bot difficulties to %s".format(to.params.botDifficulties map (_.name) mkString ", ")) 
    show(from.goodResources, to.goodResources, s"Set Good Resources to ${to.goodResources}")
    show(from.islamistResources, to.islamistResources, s"Set Islamic Resources to ${to.islamistResources}")
    show(from.numGoodOrFair, to.numGoodOrFair, s"Set Good/Fair Countries to ${to.numGoodOrFair}")
    show(from.numPoorOrIslamic, to.numPoorOrIslamic, s"Set Poor/Islamic Countries to ${to.numPoorOrIslamic}")
    show(from.prestige, to.prestige, s"Set prestige to ${to.prestige}")
    show(from.usPosture, to.usPosture, s"Set US posture to ${to.usPosture}")
    show(from.gwot, to.gwot, s"Set World posture to ${to.worldPostureDisplay}")
    show(from.reserves.us, to.reserves.us, s"Set US reserves to ${to.reserves.us}")
    show(from.reserves.jihadist, to.reserves.jihadist, s"Set Jihadist reserves to ${to.reserves.jihadist}")
    show(from.troopsAvailable, to.troopsAvailable, 
          s"Set troops on the track to ${to.troopsAvailable} (${to.troopCommitment})") 
    show(from.offMapTroops, to.offMapTroops, s"Set troops in of map box to ${to.offMapTroops}")
    show(from.militiaAvailable, to.militiaAvailable, s"Set available militia to ${to.militiaAvailable}")
    show(from.reserves.jihadist, to.reserves.jihadist, s"Set Jihadist reserves to ${to.reserves.jihadist}")
    show(from.funding, to.funding, s"Set jihadist funding to ${to.funding} (${to.fundingLevel})")
    show(from.cellsOnTrack, to.cellsOnTrack, s"Set cells on the funding track to ${to.cellsOnTrack}")
    show(from.trainingCampCells.inCamp, to.trainingCampCells.inCamp, s"Set cells in training camp to ${to.trainingCampCells}")
    show(from.resolvedPlots.sorted, to.resolvedPlots.sorted, 
          s"Set resolvedPlots plots to ${plotsDisplay(to.resolvedPlots, Jihadist)}")
    show(from.availablePlots.sorted, to.availablePlots.sorted, 
          s"Set available plots to ${plotsDisplay(to.availablePlots, to.params.humanRole)}")
    show(from.markers.sorted,  to.markers.sorted, 
            s"  Set global event markers to: ${markersString(to.markers)}" )
    (from.firstPlotCard, to.firstPlotCard) match {
      case (x, y) if x == y =>  // No change
      case (_, Some(c))     => println(s"Set ${cardNumAndName(c)} as the first plot card")
      case (_, None)        => println("There should be no first plot card")
    }
    if (from.cardsLapsing.sorted != to.cardsLapsing.sorted) {
      println("The following cards are lapsing:")
        to.cardsLapsing.sorted foreach (c => println(s"  ${cardNumAndName(c)}"))
    }
    if (from.cardsRemoved.sorted != to.cardsRemoved.sorted) {
      println("The following cards have been removed from play:")
      to.cardsRemoved.sorted foreach (c => println(s"  ${cardNumAndName(c)}"))
    }
    
    for (fromC <- from.muslims; toC = to.getMuslim(fromC.name)) {
      val b = new ListBuffer[String]
      def showC(oldValue: Any, newValue: Any, msg: String) = if (oldValue != newValue) b += msg
      def showBool(oldValue: Boolean, newValue: Boolean, marker: String) = 
        (oldValue, newValue) match {
          case (true, false) => b += s"  Remove $marker"
          case (false, true) => b += s"  Add $marker"
          case _ => // No change
          
        }
      showC(fromC.governance, toC.governance, s"  Set governance to ${govToString(toC.governance)}")    
      showC(fromC.alignment, toC.alignment, s"  Set alignment to ${toC.alignment}")    
      showC(fromC.sleeperCells, toC.sleeperCells, s"  Set active cells to ${toC.sleeperCells}")
      showC(fromC.activeCells, toC.activeCells, s"  Set active cells to ${toC.activeCells}")
      showBool(fromC.hasCadre, toC.hasCadre, "cadre marker")
      showC(fromC.troops, toC.troops, s"  Set troops to ${toC.troops}")
      showC(fromC.militia, toC.militia, s"  Set militia to ${toC.militia}")
      showC(fromC.aidMarkers, toC.aidMarkers, s"  Set aid markers to ${toC.aidMarkers}")
      showC(fromC.awakening, toC.awakening, s"  Set awakening markers to ${toC.awakening}")
      showC(fromC.reaction, toC.reaction, s"  Set reaction markers to ${toC.reaction}")
      showBool(fromC.besiegedRegime, toC.besiegedRegime, "besieged regime marker")
      if (fromC.regimeChange != toC.regimeChange) {
        toC.regimeChange match {
          case NoRegimeChange => b += "  Remove regime change marker"
          case x              => b += s"  Add ${x} regime change marker"
        }
      }
      showBool(fromC.caliphateCapital, toC.caliphateCapital, "Caliphate capital marker")
      showBool(from.isCaliphateMember(fromC.name), to.isCaliphateMember(toC.name), "Caliphate member marker")
      showC(fromC.plots.sorted, toC.plots.sorted, 
            s"  Set plots to ${mapPlotsDisplay(toC.plots, to.params.humanRole)}")
      showC(fromC.markers.sorted,  toC.markers.sorted, 
        s"  Set markers to: ${markersString(toC.markers)}" )
      showC(fromC.wmdCache, toC.wmdCache, s"  Set WMD cache to ${amountOf(toC.wmdCache, "WMD plot")}")
      
      if (b.nonEmpty) {
        b.prepend(s"\n${toC.name} changes:\n${separator()}")
        b foreach println
      }
    }
    
    for (fromC <- from.nonMuslims; toC = to.getNonMuslim(fromC.name)) {
      val b = new ListBuffer[String]
      def showC(oldValue: Any, newValue: Any, msg: String) = if (oldValue != newValue) b += msg
          
      showC(fromC.posture, toC.posture, s"  Set posture to ${toC.posture}")
      showC(fromC.sleeperCells, toC.sleeperCells, s"  Set active cells to ${toC.sleeperCells}")
      showC(fromC.activeCells, toC.activeCells, s"  Set active cells to ${toC.activeCells}")
      (fromC.hasCadre, toC.hasCadre) match {
        case (true, false) => b += "  Remove cadre marker"
        case (false, true) => b += "  Add cadre marker"
        case _ => // No change
      }
      showC(fromC.plots.sorted, toC.plots.sorted, 
            s"  Set plots to ${mapPlotsDisplay(toC.plots, to.params.humanRole)}")
      showC(fromC.markers.sorted,  toC.markers.sorted, 
        s"  Set markers to: ${markersString(toC.markers)}" )
      showC(fromC.wmdCache, toC.wmdCache, s"  Set WMD cache to ${amountOf(toC.wmdCache, "WMD plot")}")
      
      if (b.nonEmpty) {
        b.prepend(s"\n${toC.name} changes:\n${separator()}")
        b foreach println
      }
    }
  }
  
  
  // Change the current game state and print to the console all
  // ajdustments that need to be made to the game 
  def performRollback(previousGameState: GameState): Unit = {
    displayGameStateDifferences(game, previousGameState)
    game = previousGameState
  }
  
  def polarization(): Unit = {
    val candidates = game.muslims filter (m => (m.awakening - m.reaction).abs > 1)
    log()
    log("Polarization")
    log(separator())
    if (candidates.isEmpty)
      log("No countries affected")
    else {
      // Remember any Caliphate capital in case it is displaced.
      val caliphateCapital = candidates find (_.caliphateCapital) map (_.name)
      val priorCampCapacity = game.trainingCampCapacity
      // Work with names, because the underlying state of the countries will
      // undergo multiple changes, thus we will need to get the current copy of 
      // the country from the game each time we use it.
      val names = candidates.map(_.name)
      case class Converger(name: String, awakening: Boolean)
      implicit val ConvererOrdering = new Ordering[Converger] {
        def compare(x: Converger, y: Converger) = {
          // Awakening first, then order by name
          if (x.awakening == y.awakening) x.name compare y.name else -(x.awakening compare y.awakening)
        }
      }
      
      // Keep track of countries that cause convergence and do the convergence at the 
      // end so it does not affect the polarization in progress.
      var convergers = List.empty[Converger]
      for (name <- names; m = game.getMuslim(name)) {
        (m.awakening - m.reaction) match {
          case 0 =>  // Nothing happens
          case 2 =>
            game = game.updateCountry(m.copy(awakening = m.awakening + 1))
            log(s"Add an awakening marker to ${name}")
          case -2 =>
            game = game.updateCountry(m.copy(reaction = m.reaction + 1))
            log(s"Add a reaction marker to ${name}")
          case x if x > 2 =>
            if (m.isAlly) {
              improveGovernance(name)
              if (game.getMuslim(name).isGood)
                convergers = Converger(name, awakening = true) :: convergers
            }
            else 
              shiftAlignment(m.name, if (m.isNeutral) Ally else Neutral)
          case _ => // x < -2
            if (m.isAdversary) {
              degradeGovernance(name)
              if (game.getMuslim(name).isIslamistRule)
                convergers = Converger(name, awakening = false) :: convergers
            }
            else 
              shiftAlignment(m.name, if (m.isNeutral) Adversary else Neutral)
        }
      }
      
      // Now perform convergence for each country that became Good or Islamist Rule.
      if (convergers.nonEmpty) {
        log()
        for (Converger(name, awakening) <- convergers.sorted)
          performConvergence(forCountry = name, awakening)
      }

      // Check to see if the Caliphate Capital has been displaced because its country
      // was improved to Good governance.
      caliphateCapital foreach { capitalName =>
        if (game.getMuslim(capitalName).caliphateCapital == false) {
          displaceCaliphateCapital(capitalName)
          updateTrainingCampCapacity(priorCampCapacity)
        }
      }
    }
  }

  
  // Process the US civil war losses.
  // If the US is human then prompt for pieces to remove.
  // Return the number of unresolved hits
  def usCivilWarLosses(m: MuslimCountry, hits: Int): Int = {
    if (hits == 0 || m.totalTroopsAndMilitia == 0) {
      hits
    }
    else {
      val (markersLost, troopsLost, militiaLost, hitsRemaining) = if (game.humanRole == US) {
        // If there are any markers representing troops or
        // if there is a mix of multiple type of cubes that can take losses (troops and militia),
        // and the hits are not sufficient to eliminate all forces present, 
        // then allow the user to choose which units aborb the losses.
        val mixedCubes = m.troops > 0 && m.militia > 0
        if (hits >= m.totalTroopsAndMilitia)
           (m.troopsMarkers map (_.name), m.troops, m.militia, hits - m.totalTroopsAndMilitia)
        else if (!mixedCubes && m.troopsMarkers.isEmpty) {
          if (m.troops > 0)
            (Nil, m.troops min hits, 0, (hits - m.troops) max 0)
          else if (m.militia > 0)
            (Nil, 0, m.militia min hits, (hits - m.militia) max 0)
          else
            (m.troopsMarkers map (_.name), 0, 0, (hits - m.markerTroops) max 0)
        }
        else {
          var markersLost = List.empty[String]
          var (troopsLost, militiaLost, hitsRemaining) = (0, 0, hits)
          def nextHit(markers: List[TroopsMarker], 
                      troops: Int,
                      militia: Int): Unit = {
            if (hitsRemaining > 0 && (markers.nonEmpty || troops > 0 || militia > 0)) {
              println(s"${amountOf(hitsRemaining, "hit")} remaining")
              println("Choose which unit will aborb the next hit")
              
              val choices = List(
                if (troops  > 0) Some("troop"   -> "Troop cube") else None,
                if (militia > 0) Some("militia" -> "Militia cube") else None
              ).flatten ++ (markers map (m => m.name -> s"${m.name}  (absorbs ${amountOf(m.num, "hit")})"))
              askMenu(ListMap(choices:_*)).head match {
                case "troop"   => troopsLost += 1;  hitsRemaining -=1; nextHit(markers, troops - 1, militia)
                case "militia" => militiaLost += 1; hitsRemaining -=1; nextHit(markers, troops, militia - 1)
                case name      => 
                  val m = (markers find (_.name == name)).get
                  markersLost = name :: markersLost
                  hitsRemaining -= m.num; 
                  nextHit(markers filterNot (_ == name), troops, militia)
              }
            }
          }
          println(s"The US must absorb ${amountOf(hits, "hit")} due to attrition")
          nextHit(m.troopsMarkers, m.troops, m.militia)
          (markersLost, troopsLost, militiaLost, hitsRemaining)
        }
      }
      else {
        // Calculate the losses for the bot.
        // [Rule 13.3.7] The bot removes troops cubes first.
        // Then militia, then troops markers
        var hitsRemaining = hits
        val troopsLost = hitsRemaining min m.troops
        hitsRemaining -= troopsLost
        val militiaLost = hitsRemaining min  m.militia
        hitsRemaining -= militiaLost
        val markersLost = for (TroopsMarker(name, num, _, _) <- m.troopsMarkers.sorted; if hitsRemaining > 0) 
          yield {
            hitsRemaining = (hitsRemaining - num) max 0
            name
          }
        (markersLost, troopsLost, militiaLost, hitsRemaining)
      }
      
      removeEventMarkersFromCountry(m.name, markersLost:_*)
      moveTroops(m.name, "track", troopsLost)
      removeMilitiaFromCountry(m.name, militiaLost)
      hitsRemaining    
    }
  }
  
  // Process the Jihadist civil war losses.
  // If the Jihadist is human then prompt for pieces to remove.
  // Return the number of unresolved losses
  def jihadistCivilWarLosses(m: MuslimCountry, hits: Int): Int = {
    if (hits == 0 || m.totalCells == 0) {
      hits
    }
    else {
      // Remove two cells per hit is any troops present or if "Advisors" marker present.
      val multiplier = if (m.totalTroops > 0 || m.hasMarker("Advisors")) 2 else 1
      val losses     = hits * multiplier
      
      val (activesLost, sleepersLost) = if (m.totalCells <= losses)
        (m.activeCells, m.sleeperCells)
      else {
        val active    = m.activeCells min losses
        val remaining = losses - active
        val sleeper   = m.sleeperCells min remaining
        (active, sleeper)
      }
      val hitsRemaining = ((losses - activesLost - sleepersLost) max 0) / multiplier
      
      removeCellsFromCountry(m.name, activesLost, sleepersLost, addCadre = true)
      hitsRemaining    
    }
  }
  
  def civilWarAttrition(): Unit = {
    val civilWars = game.muslims filter (m => (m.civilWar))
    log()
    log("Civil War Attrition")
    log(separator())
    if (civilWars.isEmpty)
      log("No countries in civil war")
    else {
      val caliphateCapital = civilWars find (_.caliphateCapital) map (_.name)
      val priorCampCapacity = game.trainingCampCapacity
      // First if an "Advisors" marker is present in any of the countries, then 
      // add one militia to that country.
      for (m <- civilWars filter (_.hasMarker("Advisors")) if game.militiaAvailable > 0) {
        log("Add one militia to ${m.name} due to presence of Advisors")
        game = game.updateCountry(m.copy(militia = m.militia + 1))
      } 
      
      for (m <- civilWars) {
        val jihadHits = m.totalCells / 6 + (if (dieRoll <= m.totalCells % 6) 1 else 0)
        val usHits    = m.totalTroopsAndMilitia / 6 + (if (dieRoll <= m.totalTroopsAndMilitia % 6) 1 else 0)
        if (jihadHits + usHits == 0)
          log(s"${m.name}: no attrition suffered by either side")
        else {
          log(s"${m.name}:")
          log(s"The Jihadist inflicts ${amountOf(jihadHits, "hit")} on the US")
          val unfulfilledJihadHits = usCivilWarLosses(m, jihadHits)
          if (unfulfilledJihadHits > 0) {
            log(s"$unfulfilledJihadHits unfulfilled Jihadist hits against the US")
            val (shifts, newAlign) = (unfulfilledJihadHits, m.alignment) match {
              case (_, Adversary) => (0, Adversary)
              case (1, Ally)      => (1, Neutral)
              case (_, Neutral)   => (1, Adversary)
              case _              => (2, Adversary)
            }
            
            if (shifts > 0)
              shiftAlignment(m.name, newAlign)
            val steps = unfulfilledJihadHits - shifts
            if (steps > 0) {
              degradeGovernance(m.name, steps)
              if (game.getMuslim(m.name).isIslamistRule)
                performConvergence(forCountry = m.name, awakening = false)
            }
          }
          
          log(s"The US inflicts ${amountOf(usHits, "hit")} on the Jihadist")
          val unfulfilledUSHits    = jihadistCivilWarLosses(m, usHits)
          if (unfulfilledUSHits > 0) {
            log(s"$unfulfilledUSHits unfulfilled US hits against the Jihadist")
            // Shift toward Ally/Improve governance
            val (shifts, newAlign) = (unfulfilledUSHits, m.alignment) match {
              case (_, Ally)      => (0, Ally)
              case (1, Adversary) => (1, Neutral)
              case (_, Neutral)   => (1, Ally)
              case _              => (2, Ally)
            }
            if (shifts > 0)
              shiftAlignment(m.name, newAlign)
            val steps = unfulfilledUSHits - shifts
            if (steps > 0) {
              improveGovernance(m.name, steps)
              if (game.getMuslim(m.name).isGood)
                performConvergence(forCountry = m.name, awakening = true)
            }
          }
        }
        log()
      }
      
      // Check to see if the Caliphate Capital has been displaced because its country
      // was improved to Good governance.
      caliphateCapital foreach { capitalName =>
        if (game.getMuslim(capitalName).caliphateCapital == false) {
          displaceCaliphateCapital(capitalName)
          updateTrainingCampCapacity(priorCampCapacity)
        }
      }
    }
  }

  def performWarOfIdeas(name: String, ops: Int, ignoreGwotPenalty: Boolean = false): Unit = {
    game.getCountry(name) match {
      case m: MuslimCountry =>
        assert(!m.isAdversary, "Cannot do War of Ideas in Adversary country")
        assert(!m.isGood, "Cannot do War of Ideas in muslim country with Good governance")
        assert(!(m.inRegimeChange && (m.totalTroopsAndMilitia - m.totalCells) < 5),
                 "Cannot do War of Ideas in regime change country, not enought troops + militia")
        addOpsTarget(name)
        log()
        testCountry(name)
        val tested = game.getMuslim(name)
        if (ops < 3 && tested.isPoor)
          log(s"Not enough Ops to complete War of Ideas in $name")
        else {
          log(s"$US performs War of Ideas in $name")
          log(separator())
          val die = humanDieRoll()
          log(s"Die roll: $die")
          val modRoll = modifyWoiRoll(die, tested, ignoreGwotPenalty)
          if (modRoll <= 4) {
            log("Failure")
            log(s"$name remains ${govToString(tested.governance)} ${tested.alignment}")
            if (modRoll == 4 && tested.aidMarkers == 0)
              addAidMarker(name)
          }
          else if (tested.isNeutral) {
            log("Success")
            shiftAlignment(name, Ally)
          }
          else {
            val caliphateCapital = tested.caliphateCapital
            val priorCampCapacity = game.trainingCampCapacity
            log("Success")
            improveGovernance(name)
            if (game.getMuslim(name).isGood) {
              performConvergence(forCountry = name, awakening = true)
              if (caliphateCapital) {
                displaceCaliphateCapital(name)
                updateTrainingCampCapacity(priorCampCapacity)
              }
            }
          }          
        }  
        
      case n: NonMuslimCountry if n.iranSpecialCase => println("Cannot do War of Ideas in Iran")
      case n: NonMuslimCountry =>
        log()
        log(s"$US performs War of Ideas in $name")
        log(separator())
        val die = humanDieRoll()
        val newPosture = if (die > 4) Hard else Soft
        game = game.updateCountry(n.copy(posture = newPosture))
        log(s"Die roll: $die")
        if (newPosture == n.posture)
          log(s"Posture of $name remains $newPosture")
        else 
          log(s"Change posture of $name from ${n.posture} to $newPosture")
        if (newPosture == game.usPosture && game.prestige < 12) {
          game = game.adjustPrestige(1)
          log(s"New posture matches US posture, increase US prestige by +1 to ${game.prestige}")
        }
        logWorldPosture()
    }
  }

  // • Deploy at least six troops into the country.
  // • Place a green Regime Change marker on them (4.8.2). • Roll its Governance on the Country Tests table.
  // • Shift its Alignment to Ally.
  // • Shift any Sleeper cells there to Active (4.7.4.1).
  // • Roll Prestige
  def performRegimeChange(source: String, dest: String, numTroops: Int): Unit = {
    log()
    moveTroops(source, dest, numTroops)
    val m = game.getMuslim(dest)
    val newGov = if (dieRoll < 5) Poor else Fair
    addOpsTarget(dest)
    game = game.updateCountry(m.copy(
      governance   = newGov,
      alignment    = Ally,
      regimeChange = GreenRegimeChange,
      activeCells  = m.activeCells + m.sleeperCells,
      sleeperCells = 0
    ))
    log(s"Place a green regime change maker in $dest")
    log(s"Set governance of ${m.name} ${govToString(newGov)}")
    log(s"Set alignment of ${m.name} Ally")
    if (m.sleeperCells > 0)
      log(s"Flip the ${amountOf(m.sleeperCells, "sleeper cell")} in ${m.name} to active")
    rollPrestige()
    flipCaliphateSleepers()
    
  }
  
  // • Deploy any number troops out of the Regime Change country (regardless of cells present).
  // • Remove any Aid markers there.
  // • Place a Besieged Regime marker there (if there is not one already).
  // • Roll Prestige.
  def performWithdraw(source: String, dest: String, numTroops: Int): Unit = {
    log()
    addOpsTarget(dest)
    moveTroops(source, dest, numTroops)
    val m = game.getMuslim(source)
    if (m.aidMarkers > 0)
      log(s"Remove aid marker${if (m.aidMarkers > 1) "s" else ""} from ${m.name}")
    if (!m.besiegedRegime)
      log(s"Add besieged regime marker to ${m.name}")
    game = game.updateCountry(m.copy(aidMarkers = 0,besiegedRegime = true))
    rollPrestige()
  }
  
  def performDisrupt(target: String): Unit = {
    val bumpPrestige = game.disruptAffectsPrestige(target)
    addOpsTarget(target)
    game.disruptLosses(target) match {
      case Some(Left((sleepers, actives))) =>
        flipSleeperCells(target, sleepers)
        removeActiveCellsFromCountry(target, actives, addCadre = true)
      case Some(Right(_)) =>
        removeCadre(target)
      case None =>
        throw new IllegalStateException(s"performDisrupt(): $target has no cells or cadre")
    }
    
    if (bumpPrestige)
      increasePrestige(1)
  }
  
  def performAlert(target: String): Unit = {
    val c = game.getCountry(target)
    assert(c.hasPlots, s"performAlert(): $target has no plots")
    addOpsTarget(target)
    // Any any are backlashed, then don't add them to the list unless that is all there is.
    val (alerted :: remaining) = if (c.plots exists (_.backlashed))
      shuffle(c.plots filterNot (_.backlashed))
    else
      shuffle(c.plots)
    val prestigeDelta = if (alerted.plot == PlotWMD) 1 else 0
    val updated = c match {
      case m: MuslimCountry    => game = game.updateCountry(m.copy(plots = remaining)).adjustPrestige(prestigeDelta)
      case n: NonMuslimCountry => game = game.updateCountry(n.copy(plots = remaining)).adjustPrestige(prestigeDelta)
    }
    if (alerted.plot == PlotWMD) {
      log(s"$alerted alerted in $target, remove it from the game.")
      log(s"Increase prestige by +1 to ${game.prestige} for alerting a WMD plot")
    }
    else {
      log(s"$alerted alerted in $target, move it to the resolved plots box.")
      game = game.copy(resolvedPlots = alerted.plot :: game.resolvedPlots)
    }
  }
  
  def performReassessment(): Unit = {
    val newPosture = oppositePosture(game.usPosture)
    log(s"Change US posture from ${game.usPosture} to $newPosture")
    game = game.copy(usPosture = newPosture)
  }

  def performTravel(fromName: String, toName: String, activeCell: Boolean, die: Int, prefix: String = ""): Unit = {
    testCountry(toName)
    log(s"Die roll: $die")
    val modRoll = if (game.isTrainingCamp(fromName)) {
      log(s"-1: Travelling from Training Camps")
      log(s"Modified roll: ${die - 1}")
      die - 1
    }
    else
      die
    val success = modRoll <= game.getCountry(toName).governance
    val result  = if (success) "succeeds" else "fails"
      
    log(s"${prefix}Travel from $fromName to $toName $result")
    if (success)
      moveCellsBetweenCountries(fromName, toName, 1, activeCell)
    else if (activeCell)
      removeActiveCellsFromCountry(fromName, 1, addCadre = false)
    else
      removeSleeperCellsFromCountry(fromName, 1, addCadre = false)
  }
  
  def performJihad(name: String,
                  majorJihad: Boolean,
                  sleepersParticipating: Int, // not used if majorJihad == false
                  dice: List[Int]): Unit = {
    val m = game.getMuslim(name)
    val jihad = if (majorJihad) "Major Jihad" else "Jihad"
    val numDice = dice.size
    assert(!m.isIslamistRule, s"Cannot perform $jihad in Islamist Rule country.")
    assert(!(majorJihad && m.isGood), s"Cannot perform $jihad in Good governance country.")
    
    log()
    log(s"Conduct $jihad in $name, rolling ${diceString(numDice)}")
    log(separator())
    if (majorJihad && m.sleeperCells > 0)              flipAllSleepersCells(name)
    else if (!majorJihad && sleepersParticipating > 0) flipSleeperCells(name, sleepersParticipating)
    
    val results: List[Boolean] = for ((die, i) <- dice.zipWithIndex) yield {
      val ord = if (numDice == 1) "" else s"${ordinal(i+1)} "
      log()
      log(s"${ord}Die roll: $die")
      val modRoll = modifyJihadRoll(die, m)
      modRoll <= m.governance
    }
    val successes = results count (_ == true)
    val failures  = numDice - successes
    val majorSuccess = majorJihad && (
      (m.isPoor && (successes  > 1 || m.besiegedRegime)) || 
      (m.isFair && (successes == 3 || (successes == 2 && m.besiegedRegime)))
    )
    
    log()
    (successes, failures) match {
      case (0, f) => log(s"${amountOf(f, "failure")}") 
      case (s, 0) => log(s"${amountOf(s, "success", Some("successes"))}") 
      case (s, f) => log(s"${amountOf(s, "success", Some("successes"))} and ${amountOf(f, "failure")}") 
    }
    if (majorJihad)
      log(s"Major Jihad ${if (majorSuccess) "succeeds" else "fails"}")
        
    // Remove one active cell for each failure
    removeActiveCellsFromCountry(name, failures, addCadre = false)
    degradeGovernance(name, successes, removeAid = true, canShiftToIR = majorSuccess)
    if (game.getMuslim(name).isIslamistRule)
      performConvergence(forCountry = name, awakening = false)
    // A major jihad failure rolling 3 dice in a country that was 
    // already at Poor governance before the operation begain will
    // add a besieged regime marker and shift alignment toward ally
    if (majorJihad && !majorSuccess && m.governance == Poor && numDice == 3) {
      val updated = game.getMuslim(name)
      log(s"Major Jihad failure with three dice in a Poor country:")
      if (updated.besiegedRegime)
        log(s"Besieged regime marker already present in $name")
      else {
        log(s"Add besieged regime marker to $name")
        game = game.updateCountry(updated.copy(besiegedRegime = true))
      }
      val newAlignment = if (updated.alignment == Adversary) Neutral else Ally
      if (m.alignment == newAlignment)
        log(s"No shift in Aligment, $name is already at Ally.")
      else
        shiftAlignment(name, newAlignment)
    }
  }
  
  def performCardEvent(card: Card, role: Role, triggered: Boolean = false): Unit = {
    if (triggered)
      log("\n%s event \"%s\" triggers".format(role, card.name))
    else
      log("\n%s executes the \"%s\" event".format(role, card.name))
    log(separator())
    card.executeEvent(role)
    
    val remove = (card.remove, role) match {
      case (Remove, _)                => true
      case (USRemove, US)             => true
      case (JihadistRemove, Jihadist) => true
      case _                          => false
    }
    val lapsing = (card.lapsing, role) match {
      case (Lapsing, _)                => true
      case (USLapsing, US)             => true
      case (JihadistLapsing, Jihadist) => true
      case _                           => false
    }
    
    if (remove)
      removeCardFromGame(card.number)
    else if (lapsing)
      markCardAsLapsing(card.number)
  }
  
  def removeCardFromGame(cardNumber: Int): Unit = {
    log("Remove the \"%s\" card from the game".format(deck(cardNumber).name))
    game = game.copy(cardsRemoved = cardNumber :: game.cardsRemoved)
  }
  
  def markCardAsLapsing(cardNumber: Int): Unit = {
    log("Mark the \"%s\" card as lapsing".format(deck(cardNumber).name))
    game = game.copy(cardsLapsing = cardNumber :: game.cardsLapsing)
  }
  
  // Prestige roll used
  //   After regime change, withdraw, unblocked plot in the US, or by event.
  def rollPrestige(): Unit = {
    log("Roll Prestige...")
    val dirDie      = dieRoll
    val shiftDice   = List(dieRoll, dieRoll)
    val shiftAmount = if (dirDie + (if (game.gwotPenalty > 0) -1 else 0) < 5)
       -shiftDice.min
    else
      shiftDice.min
    
    log(s"Direction roll: $dirDie")
    if (game.gwotPenalty > 0) {
      log(s"-1: GWOT penalty is not zero")
      log(s"Modified roll: ${dirDie - 1}")
    }
    log(s"Rolls for shift amount: ${shiftDice.head} and ${shiftDice.last} (lowest value is used)")
    game = game.adjustPrestige(shiftAmount)
    val desc = if (shiftAmount < 0) "drops" else "rises"
    log(s"$US prestige $desc by $shiftAmount to ${game.prestige}")
  }

  // Note: The caller is responsible for handling convergence and the possible
  //       displacement of the caliphate captial.
  def improveGovernance(name: String, levels: Int = 1): Unit = {
    val m = game.getMuslim(name)
    if (levels > 0) {
      assert(m.isAlly, s"improveGovernance() called on non-ally - $name")
      assert(!m.isGood, s"improveGovernance() called on Good country - $name")
      addTestedOrImproved(name)
      val newGov = (m.governance - levels) max Good
      val delta  = m.governance - newGov
      if (newGov == Good) {
        // Note: "Training Camps" marker is handle specially.
        log(s"Improve governance of $name to ${govToString(newGov)}")
        if (m.besiegedRegime  ) log(s"Remove besieged regime marker from $name")
        if (m.aidMarkers > 0  ) log(s"Remove ${amountOf(m.aidMarkers, "aid marker")} from $name")
        if (m.awakening > 0   ) log(s"Remove ${amountOf(m.awakening, "awakening marker")} from $name")
        if (m.reaction > 0    ) log(s"Remove ${amountOf(m.reaction, "reaction marker")} from $name")
        if (m.reaction > 0    ) log(s"Remove ${amountOf(m.reaction, "reaction marker")} from $name")
        if (m.militia > 0     ) log(s"Remove ${m.militia} miltia from $name")

        val improved = m.copy(governance = Good, awakening = 0, reaction = 0, aidMarkers = 0,
               militia = 0, besiegedRegime = false)
        game = game.updateCountry(improved)
        removeTrainingCamp_?(name)
        endRegimeChange(name)
        endCivilWar(name)
      }
      else {
        log(s"Improve the governance of $name to ${govToString(newGov)}")
        if (m.awakening > 0)
          log(s"Remove ${amountOf(delta min m.awakening, "awakening marker")} from $name")
        val improved = m.copy(governance = newGov, 
               awakening  = (m.awakening - delta) max 0) // One awakening for each level actually improved
        game = game.updateCountry(improved)
      }
    }
  }

  // Degrade the governance of the given country and log the results.
  // Note: The caller is responsible for handling convergence!
  def degradeGovernance(name: String,
                        levels: Int = 1,
                        removeAid: Boolean = false,
                        canShiftToIR: Boolean = true): Unit = {
    if (levels > 0) {
      val m = game.getMuslim(name)
      assert(!m.isIslamistRule, s"degradeGovernance() called on Islamist Rule country - $name")
      val maxGov = if (canShiftToIR) IslamistRule else Poor
      val newGov = (m.governance + levels) min maxGov
      val delta  = newGov - m.governance
      if (newGov == IslamistRule) {
        log(s"Set governance of $name to ${govToString(newGov)}")
        increaseFunding(m.resources)
        if (m.totalTroopsThatAffectPrestige > 0) {
          log(s"Set US prestige to 1  (troops present)")
          game = game.copy(prestige = 1)
        }
        // Always remove aid when degraded to IR
        if (m.besiegedRegime) log(s"Remove besieged regime marker from $name")
        if (m.aidMarkers > 0) log(s"Remove ${amountOf(m.aidMarkers, "aid marker")} from $name")
        if (m.awakening > 0 ) log(s"Remove ${amountOf(m.awakening, "awakening marker")} from $name")
        if (m.reaction > 0  ) log(s"Remove ${amountOf(m.reaction, "reaction marker")} from $name")
        if (m.militia > 0   ) log(s"Remove ${m.militia} miltia from $name")
        val degraded = m.copy(
          governance = IslamistRule, alignment = Adversary, awakening = 0, reaction = 0, 
          aidMarkers = 0, militia = 0, besiegedRegime = false)
        game = game.updateCountry(degraded).copy(
          availablePlots = List.fill(m.wmdCache)(PlotWMD) ::: game.availablePlots)
        moveWMDCachedToAvailable(name)
        endRegimeChange(name)
        endCivilWar(name)
        flipCaliphateSleepers()
      }
      else if (delta == 0) {
        // There was no change in governance.  ie: Poor and canShiftToIR == false 
        // May still remove aid
        log(s"The governance of $name remains ${govToString(m.governance)}")
        if (removeAid && m.aidMarkers > 0)
          log(s"Remove ${amountOf(levels min m.aidMarkers, "aid marker")} from $name")
        game = game.updateCountry(m.copy(aidMarkers = (m.aidMarkers - levels) max 0))
        
      }
      else {
        log(s"Degrade the governance of $name to ${govToString(newGov)}")
        if (removeAid && m.aidMarkers > 0)
          log(s"Remove ${amountOf(levels min m.aidMarkers, "aid marker")} from $name")
        if (m.reaction > 0)
          log(s"Remove ${amountOf(delta min m.reaction, "reaction marker")} from $name")
        val degraded = m.copy(governance = newGov, 
               aidMarkers = (m.aidMarkers - levels) max 0, // One aid for each level (ie success die)
               reaction   = (m.reaction   - delta)  max 0) // One reaction for each level actually degraded
        game = game.updateCountry(degraded)
      }
    }
  }

  // Remove all markers from the country (except any wmd cache)
  // and make it untested.
  def setCountryToUntested(name: String): Unit = {
    log(s"Revert $name to an untested country")
    log(separator())
    if (game isMuslim name) {
      val m = game.getMuslim(name)
      removeCellsFromCountry(name, m.activeCells, m.sleeperCells, addCadre = false)
      removeCadre(name)
      moveTroops(name, "track", m.troops)
      removeMilitiaFromCountry(name, m.militia)
      for (p <- m.plots)
        removePlotFromCountry(name, p, toAvailable = true)
      removeEventMarkersFromCountry(name, m.markers:_*)
      removeBesiegedRegimeMarker(name)
      removeAidMarker(name, m.aidMarkers)
      removeAwakeningMarker(name, m.awakening)
      removeReactionMarker(name, m.reaction)
      // Ending the regime change orcivil war will also remove the caliphateCapital
      // status if it is in effect
      endRegimeChange(name)
      endCivilWar(name)
      game = game.updateCountry(game.getMuslim(name).copy(governance = GovernanceUntested))
      log(s"Remove the ${govToString(m.governance)} governance marker from $name")
    }
    else {
      val n = game.getNonMuslim(name)
      removeCellsFromCountry(name, n.activeCells, n.sleeperCells, addCadre = false)
      removeCadre(name)
      for (p <- n.plots)
        removePlotFromCountry(name, p, toAvailable = true)
      removeEventMarkersFromCountry(name, n.markers:_*)
      game = game.updateCountry(game.getNonMuslim(name).copy(posture = PostureUntested))
      log(s"Remove the ${n.posture} posture marker from $name")
    }
  }
  
  def shiftAlignment(name: String, newAlign: String): Unit = {
    var m = game.getMuslim(name)
    if (m.alignment != newAlign) {
      log(s"Shift the alignment of $name to $newAlign")
      game = game.updateCountry(m.copy(alignment = newAlign))
      if (newAlign == Adversary)
        removeEventMarkersFromCountry(name, "Advisors")
    }
  }
  
  def setCountryPosture(name: String, newPosture: String): Unit = {
    val n = game.getNonMuslim(name)
    assert(n.canChangePosture, s"Cannot set posture in $name")
    if (n.posture == newPosture) 
      log(s"The posture of $name reamins $newPosture")
    else {
      game = game.updateCountry(n.copy(posture = newPosture))
      log(s"Set posture of $name to $newPosture")
      logWorldPosture()
    }
  }
  
  def globalEventInPlay(name: String)    = (game markerInPlay name)
  def globalEventNotInPlay(name: String) = !globalEventInPlay(name)
  def lapsingEventInPlay(name: String)   = (game lapsingInPlay name)
  
  def addGlobalEventMarker(marker: String): Unit = {
    if (!(game markerInPlay marker)) {
      game = game.addMarker(marker)
      log(s"Place $marker marker in the Events In Play box")
    }
  }
  
  def removeGlobalEventMarker(marker: String): Unit = {
    if (game markerInPlay marker) {
      game = game.removeMarker(marker)
      log(s"Remove $marker marker from the Events In Play box")
    }
  }
  
  def addEventMarkersToCountry(countryName: String, markers: String*): Unit = {
    for (marker <- markers) {
      val c = game.getCountry(countryName)
      if (!(c hasMarker marker)) {
        log(s"Place $marker marker in $countryName")
        c match {
          case m: MuslimCountry    => game = game.updateCountry(m.addMarkers(marker))
          case n: NonMuslimCountry => game = game.updateCountry(n.addMarkers(marker))
        }
      }
    }
  }

  def removeEventMarkersFromCountry(countryName: String, markers: String*): Unit = {
    for (marker <- markers) {
      val c = game.getCountry(countryName)
      if (c hasMarker marker) {
        log(s"Remove $marker marker from $countryName")
        c match {
          case m: MuslimCountry    => game = game.updateCountry(m.removeMarkers(marker))
          case n: NonMuslimCountry => game = game.updateCountry(n.removeMarkers(marker))
        }
      }
    }
  }

  // Check to see if the given country has the "Training Camps".
  // If it does, but no longer meets the requirements for housing
  // the "Training Camps", the remove it and log all necessary 
  // changes to the game.
  def removeTrainingCamp_?(name: String): Unit = {
    if (game.isTrainingCamp(name)) {
      val m = game.getMuslim(name)
      if (m.isGood || (m.totalCells == 0 && !m.hasCadre)) {
        val priorCapacity  = game.trainingCampCapacity
        removeEventMarkersFromCountry(name, "Training Camps")
        updateTrainingCampCapacity(priorCapacity)
      }
    }
  }

  // Check to see if the current training camp capacity is different from 
  // the prior capacity that is given as a parameter.
  // If so log the update the game.trainingCampCells and log the changes.
  // The capacity can be 0, 3, or 5
  
  def updateTrainingCampCapacity(priorCapacity: Int): Unit = {
    val CampCells(campCelsInCamp, campCellsOnMap) = game.trainingCampCells
    val capacity = game.trainingCampCapacity
    if (capacity != priorCapacity) {
      capacity match {
        case 0 => log("Training Camps is no longer in play")
        case 3 => log("Training Camps is now in a non Caliphate country")
        case 5 => log("Training Camps is now in a Caliphate country")
        case x => throw new IllegalStateException(s"Invalid training camp capacity: $x")
      }
      log(s"The training camps available area now has a capacity of ${capacity} cells")
      if (capacity > campCelsInCamp + campCellsOnMap) {
        val delta = capacity - (campCelsInCamp + campCellsOnMap)
        log(s"Add ${amountOf(delta, "out of play cell")} to the training camps available area")
        game = game.copy(trainingCampCells = game.trainingCampCells.copy(inCamp = campCelsInCamp + delta))
      }
      else if (campCelsInCamp > capacity) {
        val delta = campCelsInCamp - capacity  // We do NOT remove training camp cells from the map!
        delta match {
          case 1 => log("Remove 1 cell from the training camps available area to out of play")
          case n => log(s"Remove the $n cells from the training camps available area to out of play")
        }
        game = game.copy(trainingCampCells = game.trainingCampCells.copy(inCamp = campCelsInCamp - delta))
      }
    }
  }

  
  def startCivilWar(name: String): Unit = {
    val m = game.getMuslim(name)
    if (!m.civilWar) {
      testCountry(name)
      if (m.isGood)
        degradeGovernance(name, 1)
      else if (m.isIslamistRule)
        improveGovernance(name, 1)
      game = game.updateCountry(m.copy(civilWar = true, regimeChange = NoRegimeChange))
      log(s"Add civil war marker to $name")
      if (m.inRegimeChange)
        log(s"Remove regime change marker from $name")
      removeAwakeningMarker(name, m.awakening)
      addMilitiaToCountry(name, m.awakening min game.militiaAvailable)
      removeReactionMarker(name, m.reaction)
      val newSleepers = m.reaction min game.cellsAvailable
      addSleeperCellsToCountry(name, newSleepers, ignoreFunding = true)
      flipCaliphateSleepers()
    }
  }
    
  def endCivilWar(name: String): Unit = {
    val markersRemovedWhenCivilWarEnds = List("Advisors", "UNSCR 1973")
    val m = game.getMuslim(name)
    val priorCampCapacity = game.trainingCampCapacity
    if (m.civilWar) {
      game = game.updateCountry(m.copy(civilWar = false))
      log(s"Remove civil war marker from $name")
      removeMilitiaFromCountry(name, m.militia)
      removeEventMarkersFromCountry(name, markersRemovedWhenCivilWarEnds:_*)
      if (m.caliphateCapital && !m.isIslamistRule && !m.inRegimeChange) {
        game = game.updateCountry(game.getMuslim(name).copy(caliphateCapital = false))
        log(s"Remove the Caliphate Capital marker from $name")
        displaceCaliphateCapital(m.name)
        updateTrainingCampCapacity(priorCampCapacity)
      }
    }
  }
  
  def endRegimeChange(name: String): Unit = {
    val m = game.getMuslim(name)
    val priorCampCapacity = game.trainingCampCapacity
    if (m.inRegimeChange) {
      game = game.updateCountry(m.copy(regimeChange = NoRegimeChange))
      log(s"Remove regime change marker from $name")
      if (m.caliphateCapital && !m.isIslamistRule && !m.civilWar) {
        game = game.updateCountry(game.getMuslim(name).copy(caliphateCapital = false))
        log(s"Remove the Caliphate Capital marker from $name")
        displaceCaliphateCapital(m.name)
        updateTrainingCampCapacity(priorCampCapacity)
      }
    }
  }
  
  // Source/dest may be "track" or a muslim country.
  // The source cannot be the same as the dest or an exception is thrown.
  // The must be enough troops available in the source or an exception is thrown
  // It is OK to specify zero troops, in which case nothing happens.
  def moveTroops(source: String, dest: String, num: Int): Unit = {
    if (num > 0) {
      assert(source != dest, "The source and destination for moveTroops() cannot be the same.")
      def disp(name: String) = if (name == "track") "the troops track" else name
      log(s"Move ${amountOf(num, "troop")} from ${disp(source)} to ${disp(dest)}")
      if (source == "track")
        assert(game.troopsAvailable >= num, "moveTroop(): Not enough troops available on track")
      else {
        val m = game.getMuslim(source)
        assert(m.troops >= num, s"moveTroop(): Not enough troops available in $source")
        game  = game.updateCountry(m.copy(troops = m.troops - num))
      }
      if (dest != "track") {
        val m = game.getMuslim(dest)
        game = game.updateCountry(m.copy(troops = m.troops + num))
        removeEventMarkersFromCountry(dest, "Advisors")
      }
    }
  }
  
  def removeAllTroopsMarkers(name: String): Unit = {
    val markers = game.getMuslim(name).troopsMarkers map (_.name)
    removeEventMarkersFromCountry(name: String, markers:_*)
  }
  
  def takeTroopsOffMap(source: String, num: Int): Unit = {
    if (num > 0) {
      def disp(name: String) = if (name == "track") "the troops track" else name
        log(s"Move ${amountOf(num, "troop")} from ${disp(source)} to the off map box")
        // Note: No need to "remove" them from the track as the number on the track is
        // calcualted base on those on the map and in the off map box.
        if (source == "track")
          assert(game.troopsAvailable >= num, "takeTroopsOffMap(): Not enough troops available on track")
        else {
          val m = game.getMuslim(source)
          assert(m.troops >= num, s"takeTroopsOffMap(): Not enough troops available in $source")
          game  = game.updateCountry(m.copy(troops = m.troops - num))
        }
        game = game.copy(offMapTroops = game.offMapTroops + num)
    }
  }
  
  // Must be enough available militia on track or an exception is thrown.
  def addMilitiaToCountry(name: String, num: Int): Unit = {
    if (num > 0) {
      assert(game.militiaAvailable >= num, "addMilitiaToCountry(): Not enough militia available on track")
      val m = game.getMuslim(name)
      game = game.updateCountry(m.copy(militia = m.militia + num))
      log(s"Add $num militia to $name from the track")
    }
  }
  
  // Must be enough militia in country or an exception is thrown.
  def removeMilitiaFromCountry(name: String, num: Int): Unit = {
    if (num > 0) {
      val m = game.getMuslim(name)
      assert(m.militia >= num, s"removeilitiaFromCountry(): Not enough militia in $name")
      game = game.updateCountry(m.copy(militia = m.militia - num))
      log(s"Remove $num militia from $name to the track")
    }
  }
  
  def addActiveCellsToCountry(name: String, num: Int, ignoreFunding: Boolean = false, logPrefix: String = "") =
    addCellsToCountry(name, true, num, ignoreFunding, logPrefix)
  
  def addSleeperCellsToCountry(name: String, num: Int, ignoreFunding: Boolean = false, logPrefix: String = "") =
    addCellsToCountry(name, false, num, ignoreFunding, logPrefix)
  
  // Move cells from the track (or training camp) to a country on the map.
  // Caller should ensure there are enough available cells to satisfy the move.
  // otherwise the function will throw an exception!
  def addCellsToCountry(name: String, active: Boolean, num: Int, ignoreFunding: Boolean = false, logPrefix: String = ""): Unit = {
    if (num > 0) {
      val isActive = active || game.isCaliphateMember(name)
      val cellType = if (isActive) "active cell" else "sleeper cell"
      val available = game.cellsAvailable
      assert(available >= num, s"not enough available cells have: $available, need $num")
      
      val CampCells(campCellsInCamp, campCellsOnMap) = game.trainingCampCells
      val fromTrack = num min game.cellsOnTrack  // Take as many as possible from the track first
      val fromCamp  = num - fromTrack            // Any left over come from the camp
      
      // The number on the track is calculated, so it does not need to be set here.
      val newCampCells = CampCells(campCellsInCamp - fromCamp, campCellsOnMap + fromCamp)
      val updated = game.getCountry(name) match {
        case m: MuslimCountry if isActive    => m.copy(activeCells  = m.activeCells  + num, hasCadre = false)
        case m: MuslimCountry                => m.copy(sleeperCells = m.sleeperCells + num, hasCadre = false)
        case n: NonMuslimCountry if isActive => n.copy(activeCells  = n.activeCells  + num, hasCadre = false)
        case n: NonMuslimCountry             => n.copy(sleeperCells = n.sleeperCells + num, hasCadre = false)
      }
      game = game.copy(trainingCampCells = newCampCells).updateCountry(updated)
      
      if (game.getCountry(name).hasCadre)
        log("%sRemove cadre marker from %s.".format(logPrefix, name))
      if (fromTrack > 0)
        log("%sMove %s to %s from the funding track".format(logPrefix, amountOf(fromTrack, cellType), name))
      if (fromCamp > 0)
        log("%sMove %s to %s from the training camp available area".format(logPrefix, amountOf(fromCamp, cellType), name))
    }
  }

  
  def removeActiveCellsFromCountry(name: String, num: Int, addCadre: Boolean, logPrefix: String = "") =
    removeCellsFromCountry(name, num, 0, addCadre, logPrefix)
    
  def removeSleeperCellsFromCountry(name: String, num: Int, addCadre: Boolean, logPrefix: String = "") =
    removeCellsFromCountry(name, 0, num, addCadre, logPrefix)
  
  // Remove cells from the a country.
  // The removed cells will moved to:
  // 1. Out of play if there are cells on the map that came from the training camp and
  //    the training camp is currently at capacity.  This can happen if the training camp
  //    was previously in play then subsequently removed from play. (Or was in a Caliphate
  //    Country that is no longer part of the Caliphate)
  // 2. The training camp if it is in play and not a full capacity
  // 3. The funding track.  This is a calculated value, so in effect the cell are simply
  //    removed from the country and no further fields are updated.
  // Caller should ensure there are enough cells of the requested type to satisfy
  // the removal, otherwise the function will throw an exception!
  // Removing zero is OK and will not do anything.
  def removeCellsFromCountry(name: String, actives: Int, sleepers: Int, addCadre: Boolean, logPrefix: String = ""): Unit = {
    val c = game.getCountry(name)
    if (actives + sleepers > 0) {
      assert(c.activeCells >= actives, s"removeCellsFromCountry(): not enough active cells present")
      assert(c.sleeperCells >= sleepers, s"removeCellsFromCountry(): not enough sleeper cells present")

      val cadreAdded = addCadre && c.totalCells == (actives + sleepers)
      for ((num, active) <- List((actives, true), (sleepers, false)); if num > 0) {
        val cellType = if (active) "active cell" else "sleeper cell"
        val CampCells(campCellsInCamp, campCellsOnMap) = game.trainingCampCells
        val campCellsRemoved = num min campCellsInCamp
        val campCapacity     = game.trainingCampCapacity
        val roomInCamp       = campCapacity - campCellsInCamp
        val toOutOfPlay      = if (roomInCamp == 0) (campCellsOnMap min num) else 0
        val toCamp           = (num - toOutOfPlay) min roomInCamp
        val toTrack          = num - toOutOfPlay - toCamp  // The remainder to the track
        // The number on the track is calculated, so it does not need to be set here.
        val newCampCells = CampCells(campCellsInCamp + toCamp, campCellsOnMap - campCellsRemoved)
        val updated = game.getCountry(name) match {
          case m: MuslimCountry if active    => m.copy(activeCells  = m.activeCells  - num, hasCadre = cadreAdded)
          case m: MuslimCountry              => m.copy(sleeperCells = m.sleeperCells - num, hasCadre = cadreAdded)
          case n: NonMuslimCountry if active => n.copy(activeCells  = n.activeCells  - num, hasCadre = cadreAdded)
          case n: NonMuslimCountry           => n.copy(sleeperCells = n.sleeperCells - num, hasCadre = cadreAdded)
        }
        game = game.copy(trainingCampCells = newCampCells).updateCountry(updated)
        
        if (toOutOfPlay > 0)
           log("%sRemove %s from %s to out of play".format(logPrefix, amountOf(toOutOfPlay, cellType), name))
        if (toCamp > 0)
          log("%sRemove %s from %s to the training camp available area".format(logPrefix, amountOf(toCamp, cellType), name))
        if (toTrack > 0)
          log("%sRemove %s from %s to the funding track".format(logPrefix, amountOf(toTrack, cellType), name))
        if (cadreAdded)
          log("%sAdd cadre marker to %s.".format(logPrefix, name))
      }
      removeTrainingCamp_?(name)
    }
  }
  
  
  def moveCellsBetweenCountries(fromName: String, toName: String, num: Int, active: Boolean): Unit = {
    val makeActive = game.isCaliphateMember(toName)
    val fromType = if (active)     "active cell" else "sleeper cell"
    val toType   = if (makeActive) "active" else "sleeper"
    val (from, to) = (game.getCountry(fromName), game.getCountry(toName))
    if (active)
      assert(from.activeCells >= num, s"moveCellsBetweenCountries(): $fromName does not have $num active cells")
    else
      assert(from.sleeperCells >= num, s"moveCellsBetweenCountries(): $fromName does not have $num sleeper cells")
    
    if (fromName == toName)
      num match {
        case 1 => log(s"1 $fromType in $fromName travels in place and becomes a $toType cell")
        case n => log(s"$n ${fromType}s in $fromName travel in place and become $toType cells")
      }
    else {
      num match {
        case 1 => log(s"Move 1 $fromType from $fromName to $toName as a $toType cell")
        case n => log(s"Move $n ${fromType}s from $fromName to $toName as $toType cells")
      }
      if (to.hasCadre)
        log(s"Remove the cadre marker in $toName")
    }
    
    from match {
      case m: MuslimCountry if active    => game = game.updateCountry(m.copy(activeCells  = m.activeCells  - num))
      case m: MuslimCountry              => game = game.updateCountry(m.copy(sleeperCells = m.sleeperCells - num))
      case n: NonMuslimCountry if active => game = game.updateCountry(n.copy(activeCells  = n.activeCells  - num))
      case n: NonMuslimCountry           => game = game.updateCountry(n.copy(sleeperCells = n.sleeperCells - num))
    }
    to match {
      case m: MuslimCountry if makeActive    => game = game.updateCountry(m.copy(activeCells = m.activeCells + num, hasCadre = false))
      case m: MuslimCountry                  => game = game.updateCountry(m.copy(sleeperCells = m.sleeperCells + num, hasCadre = false))
      case n: NonMuslimCountry if makeActive => game = game.updateCountry(n.copy(activeCells = n.activeCells + num, hasCadre = false))
      case n: NonMuslimCountry               => game = game.updateCountry(n.copy(sleeperCells = n.sleeperCells + num, hasCadre = false))
    }
    removeTrainingCamp_?(fromName)
  }
  
  
  def flipSleeperCells(name: String, num: Int): Unit = {
    if (num > 0) {
      val c = game.getCountry(name)
      assert(c.sleeperCells >= num, s"Cannot flip $num sleepers cells in $name, only ${c.sleeperCells} present")
      log("Flip %s in %s to active".format(amountOf(num, "sleeper cell"), name))
      c match {
        case m: MuslimCountry    => game = game.updateCountry(
          m.copy(sleeperCells = m.sleeperCells - num, activeCells = m.activeCells  + num))
        case n: NonMuslimCountry => game = game.updateCountry(
          n.copy(sleeperCells = n.sleeperCells - num, activeCells = n.activeCells  + num))
      }
    }
  }
  
  def flipAllSleepersCells(name: String): Unit = {
    val c = game.getCountry(name)
    if (c.sleeperCells > 0) {
      log(s"Flip all sleeper cells in $name to active")
      c match {
        case m: MuslimCountry    => game = game.updateCountry(
          m.copy(sleeperCells = 0, activeCells  = m.activeCells + m.sleeperCells))
        case n: NonMuslimCountry => game = game.updateCountry(
          n.copy(sleeperCells = 0, activeCells  = n.activeCells + n.sleeperCells))
      }
    }
  }
  
  def addCadre(name: String): Unit = {
    val c = game.getCountry(name)
    if (c.hasCadre) 
      log(s"$name already has a cadre marker")
    else {
      log(s"Add cadre marker to $name.")
      c match {
        case m: MuslimCountry    => game = game.updateCountry(m.copy(hasCadre = true))
        case n: NonMuslimCountry => game = game.updateCountry(n.copy(hasCadre = true))
      }
    }
    
  }
  
  
  def removeCadre(name: String): Unit = {
    val c = game.getCountry(name)
    if (c.hasCadre) {
      log(s"Remove cadre marker from $name.")
      c match {
        case m: MuslimCountry    => game = game.updateCountry(m.copy(hasCadre = false))
        case n: NonMuslimCountry => game = game.updateCountry(n.copy(hasCadre = false))
      }
    }
  }
  
  def addBesiegedRegimeMarker(name: String): Unit = {
    val m = game.getMuslim(name)
    if (m.besiegedRegime)
      log(s"$name already has a besieged regime marker")
    else {
      log(s"Add a besieged regime marker to $name")
      game = game.updateCountry(m.copy(besiegedRegime = true))
    }
  }
  
  def removeBesiegedRegimeMarker(name: String): Unit = {
    val m = game.getMuslim(name)
    if (m.besiegedRegime) {
      log(s"Remove besieged regime marker from $name")
      game = game.updateCountry(m.copy(besiegedRegime = false))
    }
  }
  
  def addAidMarker(target: String, num: Int = 1): Unit = {
    if (num > 0) {
      val m = game.getMuslim(target)
      assert(!m.isGood && !m.isIslamistRule, s"$target cannot take an aid marker")
      game = game.updateCountry(m.copy(aidMarkers = m.aidMarkers + num))
      log(s"Add ${amountOf(num, "aid marker")} to $target")
    }
  }
  
  def removeAidMarker(target: String, num: Int = 1): Unit = {
    if (num > 0) {
      val m = game.getMuslim(target)
      assert(m.aidMarkers >= num, "removeAidMarker() not enough markers")
      game = game.updateCountry(m.copy(aidMarkers = m.aidMarkers - num))
      log(s"Remove ${amountOf(num, "aid marker")} from $target")
    }
  }
  
  def addAwakeningMarker(target: String, num: Int = 1): Unit = {
    if (num > 0) {
      if (lapsingEventInPlay("Arab Winter")) 
        log(s"${amountOf(num, "awakening marker")} NOT added to $target because Arab Winter is in effect")
      else {
        val m = game.getMuslim(target)
        assert(m.canTakeAwakeningOrReactionMarker, s"$target cannot take an awakening marker")
        game = game.updateCountry(m.copy(awakening = m.awakening + num))
        log(s"Add ${amountOf(num, "awakening marker")} to $target")
      }
    }
  }
  
  def removeAwakeningMarker(target: String, num: Int = 1): Unit = {
    if (num > 0) {
      val m = game.getMuslim(target)
      assert(m.awakening >= num, "removeAwakeningMarker() not enough markers")
      game = game.updateCountry(m.copy(awakening = m.awakening - num))
      log(s"Remove ${amountOf(num, "awakening marker")} from $target")
    }
  }
  
  def addReactionMarker(target: String, num: Int = 1): Unit = {
    if (num > 0) {
      val m = game.getMuslim(target)
      assert(m.canTakeAwakeningOrReactionMarker, s"$target cannot take an awakening marker")
      game = game.updateCountry(m.copy(reaction = m.reaction + num))
      log(s"Add ${amountOf(num, "reaction marker")} to $target")
    }
  }
  
  def removeReactionMarker(target: String, num: Int = 1): Unit = {
    if (num > 0) {
      val m = game.getMuslim(target)
      assert(m.reaction >= num, "removeReactionMarker() not enough markers")
      game = game.updateCountry(m.copy(reaction = m.reaction - num))
      log(s"Remove ${amountOf(num, "reaction marker")} from $target")
    }
  }

  def moveWMDCachedToAvailable(name: String): Unit = {
    val c = game.getCountry(name)
    if (c.wmdCache > 0) {
      c match {
        case m: MuslimCountry    => game = game.updateCountry(m.copy(wmdCache = 0))
        case n: NonMuslimCountry => game = game.updateCountry(n.copy(wmdCache = 0))
      }
      log(s"Move ${amountOf(c.wmdCache, "unavailable WMD Plot")} from $name to available plots")
      game = game.copy(availablePlots = List.fill(c.wmdCache)(PlotWMD) ::: game.availablePlots)
    }
  }
  
  def removeCachedWMD(name: String, num: Int, bumpPrestige: Boolean = true): Unit = {
    val c = game.getCountry(name)
    assert(c.wmdCache >= num, s"removeCachedWMD(): not enough WMD in $name")
    c match {
      case m: MuslimCountry    => game = game.updateCountry(m.copy(wmdCache = m.wmdCache - num))
      case n: NonMuslimCountry => game = game.updateCountry(n.copy(wmdCache = n.wmdCache - num))
    }
    log(s"Remove ${amountOf(num, "unavailable WMD plot")} from $name")
    if (bumpPrestige) {
      game = game.adjustPrestige(num)
      log(s"Increase prestige by +$num to ${game.prestige} for removing a WMD plots")
    }
  }
  
  def increaseFunding(amount: Int): Unit = {
    if (amount > 0) {
      game = game.adjustFunding(amount)
      log(s"Increase funding by +$amount to ${game.funding}")
    }
  }
  
  def decreaseFunding(amount: Int): Unit = {
    if (amount > 0) {
      game = game.adjustFunding(-amount)
      log(s"Decrease funding by -$amount to ${game.funding}")
    }
  }
  
  def increasePrestige(amount: Int): Unit = {
    if (amount > 0) {
      game = game.adjustPrestige(amount)
      log(s"Increase prestige by +$amount to ${game.prestige}")
    }
  }
  
  def decreasePrestige(amount: Int): Unit = {
    if (amount > 0) {
      game = game.adjustPrestige(-amount)
      log(s"Decrease prestige by -$amount to ${game.prestige}")
    }
  }
  
  def resolvePlots(): Unit = {
    case class Unblocked(country: Country, mapPlot: PlotOnMap)
    def chng(amt: Int) = if (amt > 0) "Increase" else "Decrease"
    val unblocked = for (c <- game.countries filter (_.hasPlots); p <- c.plots)
      yield Unblocked(c, p)
    var wmdsInCivilWars = Set.empty[String]
    log()
    log("Reslove plots")
    if (unblocked.isEmpty) {
      log(separator())
      log("There are no unblocked plots on the map")
    }
    else if (unblocked exists (ub => ub.country.name == UnitedStates && ub.mapPlot.plot == PlotWMD)) {
      // If there is a WMD in the United States reslove it first as it will end the game.
      log(separator())
      log("An unblocked WMD plot was resolved in the United States")
      log("Game Over - Jihadist automatic victory!")
    }
    else {
      for (Unblocked(country, mapPlot) <- unblocked) {
        val name = country.name
        log(separator())
        log(s"Unblocked $mapPlot in $name")
        country match {
          //------------------------------------------------------------------
          case m: MuslimCountry =>
            // Funding
            if (mapPlot.plot == PlotWMD && mapPlot.backlashed) {
              game = game.copy(funding = 1)
              log(s"Set funding to 1.  (WMD Plot that was backlashed)")
            }
            else if (m.isGood) {
              val delta = if (mapPlot.backlashed) -2 else 2
              game = game.adjustFunding(delta)
              log(f"${chng(delta)} funding by $delta%+d to ${game.funding} (Muslim country at Good governance)")
            }
            else {
              val delta = if (mapPlot.backlashed) -1 else 1
              game = game.adjustFunding(delta)
              log(f"${chng(delta)} funding by $delta%+d to ${game.funding} (Muslim country at worse than Good governance)")
            }
            // Prestige
            if (m.totalTroopsThatAffectPrestige > 0 && mapPlot.plot == PlotWMD) {
              game = game.copy(prestige = 1)
              log(s"Set prestige to 1 (Troops present with WMD)")
            }
            else if (m.totalTroopsThatAffectPrestige > 0) {
              game = game.adjustPrestige(-1)
              log(s"Decrease prestige by -1 to ${game.prestige} (Troops present)")
            }
            // Sequestration
            if (mapPlot.plot == PlotWMD && game.eventParams.sequestrationTroops) {
              log("Resolved WMD plot releases the troops off map for Sequestration")
              returnSequestrationTroopsToAvailable()
            }
            
            // rule 11.2.6
            // If second WMD in the same civil war.  Shift immediately to Islamist Rule.
            if (mapPlot.plot == PlotWMD && m.civilWar && (wmdsInCivilWars contains m.name))
              degradeGovernance(m.name, 3, canShiftToIR = true) // 3 levels guarantees shift to IR
            else {
              if (mapPlot.plot == PlotWMD && m.civilWar) {
                // First WMD in civil war, remove a militia.
                wmdsInCivilWars += m.name
                if (m.militia > 0) {
                  log("A WMD plot in a civil war country removes a militia")
                  removeMilitiaFromCountry(m.name, 1)
                }
              }
              
              // roll plot dice
              val dice = List.fill(mapPlot.plot.number)(dieRoll)
              val successes = dice count (_ <= m.governance)
              val diceStr = dice map (d => s"$d (${if (d <= m.governance) "success" else "failure"})")
              log(s"Dice rolls to degrade governance: ${diceStr.mkString(", ")}")
              degradeGovernance(name, successes, removeAid = true, canShiftToIR = false)
            }
            
          //------------------------------------------------------------------
          case n: NonMuslimCountry =>
            // Funding
            if (n.iranSpecialCase) {
              game = game.adjustFunding(1)
              log(s"Increase funding by +1 to ${game.funding} (Iran at Fair governance)")
            }
            else if (name == UnitedStates) {
              game = game.copy(funding = 9)
              log(s"Set funding to 9.  (Plot in the United States)")
            }
            else if (mapPlot.plot == PlotWMD) {
              game = game.copy(funding = 9)
              log(s"Set funding to 9.  (WMD plot in a non-Muslim country)")
            }
            else if (n.isGood) {
              val delta = mapPlot.plot.number * 2
              game = game.adjustFunding(delta)
              log(s"Increase funding by +$delta to ${game.funding} (Plot number times 2, non-Muslim Good country)")
            }
            else {
              val delta = mapPlot.plot.number
              game = game.adjustFunding(delta)
              log(s"Increase funding by +$delta to ${game.funding} (Plot number, non-Muslim country)")
            }
            
            // Posture
            if (name == UnitedStates)
              rollUSPosture()
            else if (n.canChangePosture) {
              rollCountryPosture(n.name)
              if (n.isSchengen)
                if (game.botRole == Jihadist) {
                  val omit = Set(n.name)
                  val s1 = botCountryForPostureRoll(include = Schengen.toSet, exclude = omit).get
                  val s2 = botCountryForPostureRoll(include = Schengen.toSet, exclude = omit ++ Set(s1)).get
                  log(s"Jihadist selects two other Schengen countries: $s1 and $s2")
                  rollCountryPosture(s1)
                  rollCountryPosture(s2)
                }
                else {
                  println("Select two other Schengen countries for posture rolls")
                  val s1 = askCountry("First Schengen country: ", Schengen, allowAbort = false)
                  val s2 = askCountry("Second Schengen country: ", Schengen filterNot (_ == s1), allowAbort = false)
                  log(s"Jihadist selects two other Schengen countries: $s1 and $s2")
                  rollCountryPosture(s1)
                  rollCountryPosture(s2)
                }
            }
            // Sequestration
            if (mapPlot.plot == PlotWMD && game.eventParams.sequestrationTroops) {
              log("Resolved WMD plot releases the troops off map for Sequestration")
              returnSequestrationTroopsToAvailable()
            }
            // Prestige
            if (name == UnitedStates)
              rollPrestige()
        } // match
        log() // blank line before next one
      } // for 
      // Move all of the plots to the resolved plots box.
      println("Put the plots in the resolved plots box")
      (game.countries filter (_.hasPlots)) foreach {
        case m: MuslimCountry    =>
           game = game.updateCountry(m.copy(plots = Nil)).copy(resolvedPlots = (m.plots map (_.plot)) ::: game.resolvedPlots)
        case n: NonMuslimCountry => 
          game = game.updateCountry(n.copy(plots = Nil)).copy(resolvedPlots = (n.plots map (_.plot)) ::: game.resolvedPlots)
      }
    }
  }
  
  def piratesConditionsInEffect: Boolean = List(Somalia, Yemen) map game.getMuslim exists { m =>
    (m.isPoor && m.isAdversary) || m.isIslamistRule
  }
  
    // If Sequestration troops are off map and there is a 3 Resource country at IslamistRule
    // then return the troops to available.
  def returnSequestrationTroopsToAvailable(): Unit = {
    if (game.eventParams.sequestrationTroops) {
      log("Return 3 (Sequestration) troops from the off map box to the troops track")
      game = game.copy(offMapTroops = game.offMapTroops - 3,
                       eventParams = game.eventParams.copy(sequestrationTroops = false))
    }
  }
  
  def endTurn(): Unit = {
    if (askYorN("Really end the turn (y/n)? ")) {
      log()
      log("End of turn")
      log(separator())
    
      if (game.markerInPlay("Pirates") && piratesConditionsInEffect) {
        log("No funding drop because Pirates is in effect")
      }
      else {
        game = game.adjustFunding(-1)
        log(s"Jihadist funding drops -1 to ${game.funding}")
      }
      if (game.markerInPlay("Fracking")) {
        game = game.adjustFunding(-1)
        log(s"Jihadist funding drops -1 to ${game.funding} because Fracking is in effect")
      }
      if (game.numIslamistRule > 0) {
        game = game.adjustPrestige(-1)
        log(s"US prestige drops -1 to ${game.prestige} (At least 1 country is under Islamist Rule)")
      }
      else
        log(s"US prestige stays at ${game.prestige} (No countries under Islamist Rule)")
    
      val (worldPosture, level) = game.gwot
      if (game.usPosture == worldPosture && level == 3) {
        game = game.adjustPrestige(1)
        log(s"US prestige increases +1 to ${game.prestige} (World posture is $worldPosture $level and US posture is ${game.usPosture})")
      }
    
      if (game.cardsLapsing.nonEmpty) {
        log(s"Discard the lapsing events: ${cardNumsAndNames(game.cardsLapsing)}")
        game = game.copy(cardsLapsing = Nil, eventParams = game.eventParams.copy(oilPriceSpikes = 0))
      }
      game.firstPlotCard foreach { num => 
        log(s"Discard the firstplot card: ${cardNumAndName(num)}")
        game = game.copy(firstPlotCard = None)
      }
      // The Bot's reserves are not cleared
      if (game.humanRole == US && game.reserves.us > 0) {
        game = game.copy(reserves = game.reserves.copy(us = 0))
        log(s"$US reserves set to zero")
      }
      else if (game.humanRole == Jihadist && game.reserves.jihadist > 0) {
        game = game.copy(reserves = game.reserves.copy(jihadist = 0))
        log(s"Jihadist reserves set to zero")
      }
    
      if (game.resolvedPlots.nonEmpty) {
        game = game.copy(resolvedPlots = Nil, availablePlots = game.availablePlots ::: game.resolvedPlots)
        log("Return all resolved plots to the available plots box")
      }
    
      polarization()
      civilWarAttrition()
    
      // Calculate number of cards drawn
      val usCards = USCardDraw(game.troopCommitment)
      val jihadistCards = JihadistCardDraw(game.fundingLevel)
      log()
      log("Draw Cards")
      log(separator())
      log(s"$US player will draw $usCards cards")
      log(s"Jihadist player will draw $jihadistCards cards")
    
      // If Sequestration troops are off map and there is a 3 Resource country at IslamistRule
      // then return the troops to available.
      if (game.eventParams.sequestrationTroops && (game hasMuslim (m => m.resources == 3 && m.isIslamistRule))) {
        log("There is a 3 Resource Mulim country at Islamist Rule and")
        log("troops off map for Sequestration")
        returnSequestrationTroopsToAvailable()
      }
    
      val offMapTroopsToReturn = game.offMapTroops - (if (game.eventParams.sequestrationTroops) 3 else 0)
      if (offMapTroopsToReturn > 0) {
        log(s"Return ${offMapTroopsToReturn} troops from the off map box to the troops track")
        game = game.copy(offMapTroops = game.offMapTroops - offMapTroopsToReturn)
      }
    
      for (rc <- game.muslims filter (_.regimeChange == GreenRegimeChange)) {
        game = game.updateCountry(rc.copy(regimeChange = TanRegimeChange))
        log(s"Flip green regime change marker in ${rc.name} to its tan side")
      }
    
      // Reset history of cards for current turn
      // Move the current turn Ops targets to the previous 
      game = game.copy(turn = game.turn + 1, cardsPlayed = Nil)
    
      previousCardPlays = Nil
      previousTurns = game :: previousTurns
      saveGameState("some file name")      
    }
  }
  
  val AbortCard = "abort card"
  case object ExitGame extends Exception
  case object AbortCardPlay extends Exception
  
  // def doWarOfIdeas(country: Country)
  def main(args: Array[String]): Unit = {
    // parse cmd line args -- to be done
    // prompt for scenario -- to be done
    // prompt for bot's (jihadish ideology / us resolve) difficulty level. -- to be done
    val scenario = new Awakening2010
    // ask which side the user wishes to play -- to be done
    val (humanRole, botDifficulties) =
      askOneOf("Which side do you wish play? (US or Jihadist) ", "US"::"Jihadist"::Nil, allowAbort = false) match {
        case Some("US") => (US, Muddled :: Coherent :: Attractive :: Nil)
        case _          => (Jihadist, OffGuard :: Competent :: Adept :: Nil)
      }
    
    val humanAutoRoll = false
    game = initialGameState(scenario, humanRole, humanAutoRoll, botDifficulties)
    
    logSummary(game.scenarioSummary)
    printSummary(game.scoringSummary)
    printSummary(game.statusSummary)
    saveGameState("initial state before first turn.")
    game = game.copy(turn = game.turn + 1)
    log()
    log()
    log(s"Start of turn ${game.turn}")
    log(separator(char = '='))
    try commandLoop()
    catch {
      case ExitGame => 
    }
  }

  // Check to see if any automatic victory condition has been met.
  // Note: The WMD resolved in United States condition is checked by resolvePlots()
  def checkAutomaticVictory(): Unit = {
    def gameOver(victor: Role, reason: String) = {
      log()
      log(separator())
      log(reason)
      log(s"Game Over - $victor automatic victory!")
    }
    if (game.goodResources >= 12)
      gameOver(US, s"${game.numGoodOrFair} resources controlled by countries with Good governance")
    else if (game.numGoodOrFair >= 15)
      gameOver(US, s"${game.numGoodOrFair} Muslim countries have Fair or Good governance")
    else if (game.cellsOnMap == 0 && game.humanRole == Jihadist)
      gameOver(US, s"There are no cells on the map")
    else if (game.islamistResources >= 6 && game.botRole == Jihadist)
      gameOver(Jihadist, s"${game.islamistResources} resources controlled by countries with Islamist Rule governance")
    else if (game.islamistResources >= 6 && game.islamistAdjacency) {
      val reason = s"${game.islamistResources} resources controlled by countries with Islamist Rule governance\n" +
                    "and at least two of the countries are adjacent"
      gameOver(Jihadist, reason)
    }
    else if (game.numPoorOrIslamic >= 15 && game.prestige == 1) {
      val reason = s"${game.numPoorOrIslamic} Muslim countries have Poor or Islamist rule governance " +
                    "and US prestige is 1"
      gameOver(US, reason)
    }
  }
  
  // ---------------------------------------------
  // Process all top level user commands.
  @tailrec def commandLoop(): Unit = {
    checkAutomaticVictory()
    val prompt = {
      val cards = game.cardsPlayed.size
      val plots = game.countries.foldLeft(0) { (sum, c) => sum + c.plots.size }
      val plotDisp = if (plots == 0) "" else s", ${amountOf(plots, "unresolved plot")}"
      s"""
         |>>> Turn ${game.turn}  (${amountOf(cards, "card")} played$plotDisp) <<<
         |${separator()}
         |Command : """.stripMargin
    }
    readLine(prompt) match {
      case null => println() // User pressed Ctrl-d (end of file)
      case cmd =>
        doCommand(cmd.trim)
        commandLoop()
    }
  }
  
  
  // Parse the top level input and execute the appropriate command.
  def doCommand(input: String): Unit = {
    case class Command(val name: String, val help: String)
    val Commands = List(
      Command("us",           """Enter a card number for a US card play"""),
      Command("jihadist",     """Enter a card number for a Jihadist card play"""),
      Command("remove cadre", """Voluntarily remove a cadre from the map"""),
      Command("resolve plots","""Resolve any unblocked plots on the map"""),
      Command("end turn",     """End the current turn.
                                |This should be done after the last US card play.
                                |Any plots will be resolved and the end of turn
                                |actions will be conducted."""),
      Command("show",         """Display the current game state
                                |  show all        - entire game state
                                |  show played     - cards played during the current turn
                                |  show summary    - game summary including score
                                |  show scenario   - scenario and difficulty level
                                |  show caliphate  - countries making up the Caliphate
                                |  show civil wars - countries in civil war
                                |  show <country>  - state of a single country""".stripMargin),
      Command("adjust",       """Adjust game settings <Minimal rule checking is applied>
                                |  adjust prestige      - US prestige level
                                |  adjust posture       - US posture
                                |  adjust funding       - Jihadist funding level
                                |  adjust difficulty    - Jihadist ideology/US resolve
                                |  adjust lapsing cards - Current lapsing cards
                                |  adjust removed cards - Cards removed from the game
                                |  adjust first plot    - Current first plot card
                                |  adjust markers       - Current global event markers
                                |  adjust reserves      - US and/or Jihadist reserves
                                |  adjust plots         - Available/resolved plots
                                |  adjust offmap troops - Number of troops in off map box
                                |  adjust auto roll     - Auto roll for human operations
                                |  adjust <country>     - Country specific settings""".stripMargin),
      Command("history",      """Display game history"""),
      Command("rollback",     """Roll back card plays in the current turn or
                                |roll back to the start of any previous turn""".stripMargin),
      Command("help",         """List available commands"""),
      Command("quit",         """Save the current turn and quit the game""")
    ) filter { 
      case Command("remove cadre", _) => game.humanRole == Jihadist && (game.hasCountry(_.hasCadre))
      case _                          => true
    } 

    val CmdNames = (Commands map (_.name))
    
    def showCommandHelp(cmd: String) = Commands find (_.name == cmd) foreach (c => println(c.help))
    
    val tokens = input.split("\\s+").toList.dropWhile(_ == "")
    tokens.headOption foreach { verb =>
      val param = if (tokens.tail.nonEmpty) Some(tokens.tail.mkString(" ")) else None
      matchOne(verb, CmdNames) foreach {
        case "us"            => usCardPlay(param)
        case "jihadist"      => jihadistCardPlay(param)
        case "remove cadre"  => humanRemoveCadre()
        case "resolve plots" => resolvePlots()
        case "end turn"      => endTurn()
        case "show"          => showCommand(param)
        case "adjust"        => adjustSettings(param)
        case "history"       => game.history foreach println  // TODO: Allow > file.txt
        case "rollback"      => println("Not implemented.")
        case "quit" =>
          throw ExitGame
        case "help" if param.isEmpty =>
          println("Available commands: (type help <command> for more detail)")
          println(orList(CmdNames))
        case "help"     => matchOne(param.get, CmdNames) foreach showCommandHelp
        case cmd        => println(s"Internal error: Command '$cmd' is not valid")
      }
    }
  }
  
  // The Jihadist play can voluntarily remove cadre markers on the map
  // (To avoid giving the US an easy prestige bump)
  def humanRemoveCadre(): Unit = {
    val candidates = countryNames(game.countries filter (_.hasCadre))
    val target = askCountry(s"Remove cadre in which country: ", candidates)
    game.getCountry(target) match {
      case m: MuslimCountry    => game = game.updateCountry(m.copy(hasCadre = false))
      case n: NonMuslimCountry => game = game.updateCountry(n.copy(hasCadre = false))
    }
    log()
    log(separator())
    log(s"$Jihadist voluntarily removes a cadre from $target")
  }
  
  
  def showCommand(param: Option[String]): Unit = {
    val options = "all" :: "cards" :: "summary" :: "scenario" :: "caliphate" ::
                  "civil wars" :: countryNames(game.countries)
    askOneOf("Show: ", options, param, allowNone = true, abbr = CountryAbbreviations, allowAbort = false) foreach {
      case "cards"      => printSummary(game.cardPlaySummary)
      case "summary"    => printSummary(game.scoringSummary); printSummary(game.statusSummary)
      case "scenario"   => printSummary(game.scenarioSummary)
      case "caliphate"  => printSummary(game.caliphateSummary)
      case "civil wars" => printSummary(game.civilWarSummary)
      case "all"        => printGameState()
      case name         => printSummary(game.countrySummary(name))
    }
  }
  
  // Print the entire game state to stdout
  def printGameState(): Unit = {
    def printCountries(title: String, countries: List[String]): Unit = {
      println()
      println(title)
      println(separator())
      if (countries.isEmpty)
        println("none")
      else
        for (name <- countries; line <- game.countrySummary(name))
          println(line)
    }
    
    printSummary(game.scenarioSummary)
    printCountries("Muslim Countries with Good Governance",  countryNames(game.muslims filter (_.isGood)))
    printCountries("Muslim Countries with Fair Governance",  countryNames(game.muslims filter (_.isFair)))
    printCountries("Muslim Countries with Poor Governance",  countryNames(game.muslims filter (_.isPoor)))
    printCountries("Muslim Countries under Islamic Rule",    countryNames(game.muslims filter (_.isIslamistRule)))
    printCountries("Untested Muslim Countries with Data",    countryNames(game.muslims filter (_.isUntestedWithData)))
    printCountries("Non-Muslim Countries with Hard Posture", countryNames(game.nonMuslims filter (_.isHard)))
    printCountries("Non-Muslim Countries with Soft Posture", countryNames(game.nonMuslims filter (_.isSoft)))
    val iranSpecial = game.nonMuslims find (_.iranSpecialCase) map (_.name)
    if (iranSpecial.nonEmpty)
      printCountries("Iran Special Case", iranSpecial.toList)
    printSummary(game.scoringSummary)
    printSummary(game.statusSummary)
    printSummary(game.civilWarSummary)
    printSummary(game.caliphateSummary)
  }
  
  sealed trait CardAction
  case class  Event(card: Card) extends CardAction
  case object Ops               extends CardAction
  
  // Return a list of actions.
  // card2 is only used for US reassessment
  def getActionOrder(card: Card, opponent: Role, card2: Option[Card] = None): List[CardAction] =
    ((card :: card2.toList) filter (c => c.autoTrigger || c.eventWillTrigger(opponent))) match {
      case c :: Nil =>
        println("\nThe %s event \"%s\" will trigger, which should happen first?".format(opponent, c.name))
        println(separator())
        val choices = List("event", "operations")
        askOneOf(s"${orList(choices)}? ", choices) match {
          case None               => Nil
          case Some("operations") => List(Ops, Event(c))
          case _                  => List(Event(c), Ops)
        }
        
      case c1 :: c2 :: Nil =>
        println("\nThe %s events 1:\"%s\" and 2:\"%s\" will trigger, which should happen first?".
                format(opponent, c1.name, c2.name))
        println(separator())
        val choices1 = List("1st event", "2nd event", "operations")
        askOneOf(s"${orList(choices1)}? ", choices1) match {
          case None => Nil
          case Some(first) =>
            println("Which should happen second?")
            val choices2 = choices1 filterNot (_ == first)
            askOneOf(s"${orList(choices2)}? ", choices2) match {
              case None => Nil
              case Some(second) =>
                val third = (choices2 filterNot (_ == second)).head
                List(first, second, third) map {
                  case "1st event" => Event(c1)
                  case "2nd event" => Event(c2)
                  case _           => Ops
                }
            }
        }
      case _ => // No events triggered
        List(Ops)
    }
  
  def usCardPlay(param: Option[String]): Unit = {
    askCardNumber("Card # ", param) foreach { cardNumber =>
      val card    = deck(cardNumber)
      // Save the game state so we can roll back later 
      previousCardPlays = game :: previousCardPlays
      // Add the card to the list of played cards for the turn
      // and clear the op targets for the current card.
      game = game.copy(
        cardsPlayed        = PlayedCard(US, card) :: game.cardsPlayed,
        targetsLastCard = game.targetsThisCard,
        targetsThisCard = CardTargets()
      )
      logCardPlay(US, card)
      try game.humanRole match {
          case US       => humanUsCardPlay(card)
          case Jihadist => // The US bot plays the card
        }
      catch {
        case AbortCardPlay =>
          println("\n>>>> Aborting the current card play <<<<")
          println(separator())
          val mostRecent :: rest = previousCardPlays
          previousCardPlays = rest
          performRollback(mostRecent)
      }
    }
  }
  
  // Once the user enters a valid command (other than using reserves), then in order to
  // abort the command in progress they must type 'abort' at any prompt during the turn.
  // We will then roll back to the game state as it was before the card play.
  def humanUsCardPlay(card: Card): Unit = {
    val ExecuteEvent = "event"
    val WarOfIdeas   = "woi"
    val Deploy       = "deploy"
    val RegimeChg    = "regime change"
    val Withdraw     = "withdraw"
    val Disrupt      = "disrupt"
    val Alert        = "alert"
    val Reassess     = "reassessment"
    val AddReserves  = "add to reserves"
    val UseReserves  = "expend reserves"
    var reservesUsed = 0
    def inReserve    = game.reserves.us
    var secondCard: Option[Card] = None   // For reassessment only
    def opsAvailable = (card.ops + reservesUsed) min 3
    
    @tailrec def getNextResponse(): Option[String] = {
      val actions = List(
        if (card.eventIsPlayable(US) && reservesUsed == 0)      Some(ExecuteEvent) else None,
                                                                Some(WarOfIdeas),
        if (game.deployPossible(opsAvailable))                  Some(Deploy)       else None,
        if (game.regimeChangePossible(opsAvailable))            Some(RegimeChg)    else None,
        if (game.withdrawPossible(opsAvailable))                Some(Withdraw)     else None,
        if (game.disruptTargets(opsAvailable).nonEmpty)         Some(Disrupt)      else None,
        if (game.alertPossible(opsAvailable))                   Some(Alert)        else None,
        if (card.ops == 3 && reservesUsed == 0)                 Some(Reassess)     else None,
        if (card.ops < 3 && reservesUsed == 0 && inReserve < 2) Some(AddReserves)  else None,
        if (card.ops < 3 && inReserve > 0)                      Some(UseReserves)  else None
      ).flatten
    
      println(s"\nYou have ${opsString(opsAvailable)} available and ${opsString(inReserve)} in reserve")
      println(s"Available actions: ${actions.mkString(", ")}")
      askOneOf(s"$US action: ", actions) match {
        case Some(UseReserves) =>
          reservesUsed = inReserve
          log(s"$US player expends their reserves of ${opsString(reservesUsed)}")
          game = game.copy(reserves = game.reserves.copy(us = 0))
          getNextResponse()
        case Some(Reassess) =>
          println("You must play a second 3 Ops card")
          askCardNumber("Card # ", None, true, only3Ops = true) match {
            case None => None // Cancel the operation
            case Some(cardNum) =>
              val card2 = deck(cardNum)
              // Add to the list of played cards for the turn
              game = game.copy(cardsPlayed = PlayedCard(US, card2) :: game.cardsPlayed)
              secondCard = Some(card2)
              log(s"$US plays $card2")
              Some(Reassess)
          }
        case action => action
      }
    }
    
    getNextResponse() foreach { action =>
      if (action == AddReserves) {
        // Don't prompt for event/ops order when playing to reserves.
        game = game.copy(reserves = game.reserves.copy(us = card.ops))
        log(s"$US adds ${opsString(card.ops)} to reserves.  Reserves are now ${opsString(inReserve)}.")
        if (card.eventWillTrigger(Jihadist))
          performCardEvent(card, Jihadist, triggered = true)
      }
      else if (action == ExecuteEvent)
        performCardEvent(card, US)
      else
        getActionOrder(card, opponent = Jihadist, secondCard) match {
          case Nil => // Cancel the operation
          case actions =>
            actions foreach {
              case Event(c) =>
                performCardEvent(card, Jihadist, triggered = true)
              case Ops =>
                action match {
                  case WarOfIdeas => humanWarOfIdeas(opsAvailable)
                  case Deploy     => humanDeploy(opsAvailable)
                  case Disrupt    => humanDisrupt(opsAvailable)
                  case RegimeChg  => humanRegimeChange()
                  case Withdraw   => humanWithdraw()
                  case Alert      => humanAlert()
                  case Reassess   => humanReassess()
                  case _ => throw new IllegalStateException(s"Invalid US action: $action")
                }
              case _ =>
            }
        }
    }
  }
  
  // This method allows the human player to execute an operation with the
  // given number of Ops.  This is called by some event card actions.
  def humanExecuteOperation(ops: Int): Unit = {
    val WarOfIdeas   = "woi"
    val Deploy       = "deploy"
    val RegimeChg    = "regime change"
    val Withdraw     = "withdraw"
    val Disrupt      = "disrupt"
    val Alert        = "alert"
    val actions = List(
                                             Some(WarOfIdeas),
      if (game.deployPossible(ops))          Some(Deploy)       else None,
      if (game.regimeChangePossible(ops))    Some(RegimeChg)    else None,
      if (game.withdrawPossible(ops))        Some(Withdraw)     else None,
      if (game.disruptTargets(ops).nonEmpty) Some(Disrupt)      else None,
      if (game.alertPossible(ops))           Some(Alert)        else None
    ).flatten
    println(s"Available actions: ${actions.mkString(", ")}")
    askOneOf(s"$US action: ", actions).get match {
      case WarOfIdeas => humanWarOfIdeas(ops)
      case Deploy     => humanDeploy(ops)
      case RegimeChg  => humanRegimeChange()
      case Withdraw   => humanWithdraw()
      case Disrupt    => humanDisrupt(ops)
      case Alert      => humanAlert()
      case illegal    => throw new IllegalStateException(s"Invalid US action: $illegal")
    }
  }
  
  def humanWarOfIdeas(ops: Int): Unit = {
    log()
    log(s"$US attempts War of Ideas operation with ${opsString(ops)}")
    val target = askCountry("War of Ideas in which country: ", game.warOfIdeasTargets(ops))
    performWarOfIdeas(target, ops)
  }
  
  // Troops can alwasy deploy to the track.
  def humanDeploy(ops: Int): Unit = {
    log()
    log(s"$US performs Deploy operation with ${opsString(ops)}")
    log(separator())
    // If the only 
    val (from, to) = game.deployTargets(ops).get
    val source     = askCountry("Deploy troops from: ", from)
    // Some troops markers can be deployed out of a country.
    val troopsMarkers: List[String] = if (source == "track")
      Nil 
    else {
      val candidates = game.getMuslim(source).troopsMarkers filter (_.canDeploy) map (_.name)
      if (candidates.isEmpty)
        Nil
      else {
        val combos = for (i <- 1 to candidates.size; combo <- candidates.combinations(i).toList)
          yield (combo.mkString(",") -> andList(combo))
        val choices = ListMap("none" -> "none") ++ combos
        println(s"Which troops markers will deploy out of $source")
        askMenu(choices).head match {
          case "none" => Nil
          case str    => str.split(",").toList
        }
      }
    }
    val dest       = askCountry("Deploy troops to: ", to filterNot (_ == source))
    val maxTroops  = if (source == "track") game.troopsAvailable else game.getMuslim(source).maxDeployFrom
    val numTroops  = askInt("Deploy how many troops: ", 0, maxTroops)
    log()
    removeEventMarkersFromCountry(source, troopsMarkers:_*)
    moveTroops(source, dest, numTroops)
  }
  
  def humanRegimeChange(): Unit = {
    log()
    log(s"$US performs Regime Change operation with ${opsString(3)}")
    log(separator())
    val dest      = askCountry("Regime change in which country: ", game.regimeChangeTargets)
    val source    = askCountry("Deploy troops from: ", game.regimeChangeSources)
    val maxTroops = if (source == "track") game.troopsAvailable else game.getMuslim(source).maxDeployFrom
    val numTroops = askInt("How many troops: ", 6, maxTroops)
    performRegimeChange(source, dest, numTroops)
  }
  
  def humanWithdraw(): Unit = {
    log()
    log(s"$US performs Withdraw operation with ${opsString(3)}")
    log(separator())
    val source = askCountry("Withdraw troops from which country: ", game.withdrawFromTargets)
    val dest   = askCountry("Deploy withdrawn troops to: ", (game.withdrawToTargets filter (_ != source)))
    val numTroops = askInt("How many troops: ", 1, game.getMuslim(source).troops)
    performWithdraw(source, dest, numTroops)
  }

  def humanDisrupt(ops: Int): Unit = {
    log()
    log(s"$US performs Disrupt operation with ${opsString(ops)}")
    log(separator())
    performDisrupt(askCountry("Disrupt in which country: ", game.disruptTargets(ops)))
  }
  
    
  def humanAlert(): Unit = {
    log()
    log(s"$US performs Alert operation with ${opsString(3)}")
    log(separator())
    performAlert(askCountry("Alert plot in which country: ", game.alertTargets))
  }
    
  def humanReassess(): Unit = {
    log()
    log(s"$US performs a Reassessment operation with ${opsString(6)}")
    log(separator())
    performReassessment()
  }
        
  def jihadistCardPlay(param: Option[String]): Unit = {
    askCardNumber("Card # ", param) foreach { cardNumber =>
      val card    = deck(cardNumber)
      // Save the game state so we can roll back later 
      previousCardPlays = game :: previousCardPlays
      // Add the card to the list of played cards for the turn
      // and clear the op targets for the current card.
      game = game.copy(
        cardsPlayed        = PlayedCard(Jihadist, card) :: game.cardsPlayed,
        targetsLastCard = game.targetsThisCard,
        targetsThisCard = CardTargets()
      )
      logCardPlay(Jihadist, card)
      try game.humanRole match {
          case US       => // The Jihadist bot plays the card
          case Jihadist => humanJihadistCardPlay(card)
        }
      catch {
        case AbortCardPlay =>
          println("\n>>>> Aborting the current card play <<<<")
          println(separator())
          val mostRecent :: rest = previousCardPlays
          previousCardPlays = rest
          performRollback(mostRecent)
      }
    }
  }
  
  // Once the user enters a valid command (other than using reserves), then in order to
  // abort the command in progress they must type 'abort' at any prompt during the turn.
  // We will then roll back to the game state as it was before the card play.
  def humanJihadistCardPlay(card: Card): Unit = {
    val ExecuteEvent = "event"
    val Recruit      = "recruit"
    val Travel       = "travel"
    val Jihad        = "jihad"
    val PlotAction   = "plot"
    val AddReserves  = "add to reserves"
    val UseReserves  = "expend reserves"
    var reservesUsed = 0
    def inReserve    = game.reserves.jihadist
    def opsAvailable = (card.ops + reservesUsed) min 3
  
    @tailrec def getNextResponse(): Option[String] = {
      val actions = List(
        if (card.eventIsPlayable(Jihadist) && reservesUsed == 0) Some(ExecuteEvent) else None,
        if (game.recruitPossible)                                Some(Recruit)      else None,
         /* Travel must be possible or the Jihadist has lost */  Some(Travel),
        if (game.jihadPossible)                                  Some(Jihad)        else None,
        if (game.plotPossible(opsAvailable))                     Some(PlotAction)   else None,
        if (card.ops < 3 && reservesUsed == 0 && inReserve < 2)  Some(AddReserves)  else None,
        if (card.ops < 3 && inReserve > 0)                       Some(UseReserves)  else None
      ).flatten
  
      println(s"\nYou have ${opsString(opsAvailable)} available and ${opsString(inReserve)} in reserve")
      println(s"Available actions: ${actions.mkString(", ")}")
      askOneOf(s"$Jihadist action: ", actions) match {
        case Some(UseReserves) =>
          reservesUsed = inReserve
          log(s"$Jihadist player expends their reserves of ${opsString(reservesUsed)}")
          game = game.copy(reserves = game.reserves.copy(jihadist = 0))
          getNextResponse()
        case action => action
      }
    }

    getNextResponse() foreach { action =>
      if (action == AddReserves) {
        // Don't prompt for event/ops order when playing to reserves.
        game = game.copy(reserves = game.reserves.copy(jihadist = card.ops))
        log(s"$Jihadist adds ${opsString(card.ops)} to reserves.  Reserves are now ${opsString(inReserve)}.")
        if (card.eventWillTrigger(US))
          performCardEvent(card, US, triggered = true)
      }
      else if (action == ExecuteEvent)
        performCardEvent(card, Jihadist)
      else if (action == PlotAction && game.firstPlotCard.isEmpty) {
        println()
        println(separator())
        log(s"Place the $card card in the first plot box")
        if (card.eventWillTrigger(US))
          log("%s event \"%s\" does not trigger".format(US, card.name))
        game = game.copy(firstPlotCard = Some(card.number))
        humanPlot(opsAvailable)
      }
      else
        getActionOrder(card, opponent = US) match {
          case Nil => // Cancel the operation
          case actions =>
            actions foreach {
              case Event(c) =>
                performCardEvent(card, US, triggered = true)
              case Ops =>
                action match {
                  case Recruit      => humanRecruit(opsAvailable)
                  case Travel       => humanTravel(opsAvailable)
                  case Jihad        => humanJihad(opsAvailable)
                  case PlotAction   => humanPlot(opsAvailable)
                  case _ => throw new IllegalStateException(s"Invalid Jihadist action: $action")
                }
              case _ =>
            }
        }
    }
  }    

  def humanRecruit(ops: Int): Unit = {
    val availableCells = game.cellsToRecruit
    log()
    log(s"$Jihadist performs a Recruit operation with ${opsString(ops)}")
    log(separator())
    if (availableCells == 1)
      log(s"There is 1 cell available for recruitment")
    else
      log(s"There are $availableCells cells available for recruitment")
    // All of the target destinations must be declared before rolling any dice.
    val targets = for (i <- 1 to ops) yield {
      val ord  = ordinal(i)
      val dest = askCountry(s"$ord Recruit destination: ", game.recruitTargets)
      (ord, dest)
    }
    
    val results = for ((ord, dest) <- targets; c = game.getCountry(dest)) yield {
      addOpsTarget(dest)
      if (c.autoRecruit) {
        log(s"$ord Recruit automatically succeeds in $dest")
        (dest, true)
      }
      else {
        val die     = humanDieRoll(s"Die roll for $ord Recruit in $dest: ")
        val success = c.recruitSucceeds(die)
        val result  = if (success) "succeeds" else "fails"
        log(s"$ord Recruit $result in $dest with a roll of $die")
        (dest, success)
      }
    }
    println()
    val successes = (for ((dest, success) <- results; if success) yield dest).toList
    def allTheSame(l: List[String]) = l match {
      case Nil => false
      case x::xs => xs forall (_ == x)
    }
    // If we have more successes than there are available cells, then we must
    // allow the user to choose where to put the cells.  
    if (successes.size > availableCells) {
        // But if all of the success targets are the same country, then
        // just add all available cell there without asking.
      if (allTheSame(successes))
        addSleeperCellsToCountry(successes.head, availableCells)
      else {
        log(s"${successes.size} successful recruits, but only $availableCells available cells")
        println("Specify where to put the cells:")
        def ask(dest: String) = askOneOf(s"1 cell in $dest (place or skip): ", Seq("place", "skip")).get
        // Keep asking until all of the available cells have been placed or until 
        // the remaining target destination are all the same.
        @tailrec def placeNext(dests: List[String], skipped: List[String], cellsLeft: Int): Unit =
          if (cellsLeft != 0)
            (dests, skipped) match {
              case (Nil, Nil)                  =>  // should not get here
              case (Nil, sk) if allTheSame(sk) => addSleeperCellsToCountry(sk.head, cellsLeft)
              case (Nil, sk)                   => placeNext(sk.reverse, Nil, cellsLeft) // Go around again
              case (ds, _) if allTheSame(ds)   => addSleeperCellsToCountry(ds.head, cellsLeft)
              case (d :: ds, sk)               => ask(d) match {
                                                    case "place" => addSleeperCellsToCountry(d, 1)
                                                                    placeNext(ds, sk, cellsLeft - 1)
                                                    case _       => placeNext(ds, d :: sk, cellsLeft)
                                                  }
            }
        placeNext(successes, Nil, availableCells)
      }
    }
    else // We have enough cells to place one in all destinations
      for ((dest, num) <- successes groupBy (dest => dest) map { case (dest, xs) => (dest -> xs.size) })
        addSleeperCellsToCountry(dest, num)
  }
  
  def travelIsAutomatic(src: String, dest: String): Boolean = {
    if (lapsingEventInPlay("Islamic Maghreb") && (Schengen contains dest))
      false
    else
      src == dest || areAdjacent(src, dest)
  }
  
  def humanTravel(ops: Int): Unit = {
    val Active  = true
    log()
    log(s"$Jihadist performs a Travel operation with ${opsString(ops)}")
    log(separator())
    log(s"There are ${{amountOf(game.cellsOnMap, "cell")}} on the map")
    val maxRolls = ops min game.cellsOnMap
    val numRolls = askInt("How many dice do you wish to roll?", 1, maxRolls, Some(maxRolls))
    // Keep track of where cells have been selected. They can only be selected once!
    case class Source(name: String, sleepers: Int, actives: Int)
    var sourceCountries = 
      (game.countries
        filter (_.totalCells > 0) 
        map (c => c.name -> Source(c.name, c.sleeperCells, c.activeCells))
      ).toMap
    
    // All of the travelling cells must be declared before rolling any dice.
    val attempts = for (i <- 1 to numRolls) yield {
      println()
      val ord        = ordinal(i)
      val candidates = sourceCountries.keys.toList.sorted
      val src        = sourceCountries(askCountry(s"$ord Travel from which country: ", candidates))
      val cellType   = if (src.sleepers > 0 && src.actives > 0) {
        val prompt = s"$ord Travel which cell (active or sleeper) Default = active: "
        askOneOf(prompt, List("active", "sleeper"), allowNone = true) match {
          case None    => Active
          case Some(x) => x == "active"
        } 
      }
      else {
        println(s"$ord Travel cell will be ${if (src.actives > 0) "an active cell" else "a sleeper cell"}")
        src.actives > 0
      }
        
      val dest = askCountry(s"$ord Travel to which country: ", countryNames(game.countries))
      // Remove the selected cell so that it cannot be selected twice.
      if (src.sleepers + src.actives == 1)
        sourceCountries -= src.name
      else if (cellType == Active)
        sourceCountries += src.name -> src.copy(actives = src.actives - 1)
      else
        sourceCountries += src.name -> src.copy(sleepers = src.sleepers - 1)
      (ord, src.name, dest, cellType)
    }
    
    // Now we can roll the die for each one see what happens and log the result.
    for ((ord, srcName, destName, active) <- attempts) {
      addOpsTarget(destName)
      
      if (travelIsAutomatic(srcName, destName)) {
        log(s"$ord Travel from $srcName to $destName succeeds automatically")
        moveCellsBetweenCountries(srcName, destName, 1, active)
      }
      else {
        println()
        testCountry(destName)
        val die    = humanDieRoll(s"Die roll for $ord Travel from $srcName to $destName: ")
        performTravel(srcName, destName, active, die, s"$ord ")
      }
    }
  }
  
  // When ever a country is targeted and is a candidate for Major Jihad
  // ask the user if they want to do Major Jihad.
  def humanJihad(ops: Int): Unit = {
    val Active  = true
    log()
    log(s"$Jihadist performs a Jihad operation with ${opsString(ops)}")
    log(separator())
    val jihadCountries = game.muslims filter (_.jihadOK)
    val maxDice = ops min (jihadCountries map (_.totalCells)).sum
    case class Target(name: String, major: Boolean, actives: Int, sleepers: Int)
    var candidates = countryNames(jihadCountries)
    // All of the jihad cells must be declared before rolling any dice.
    @tailrec def getJihadTargets(diceLeft: Int, candidates: List[String], targets: List[Target]): List[Target] = {
      if (diceLeft == 0 || candidates.isEmpty)
        targets.reverse
      else {
        if (diceLeft < maxDice)
          println(s"You have ${diceString(diceLeft)} remaining")
        val name = askCountry(s"Jihad in which country: ", candidates)
        val m    = game.getMuslim(name)
        val majorJihad = if (m.majorJihadOK(diceLeft)) askYorN(s"Conduct Major Jihad in $name (y/n)? ")
        else false
        val numRolls = if (majorJihad) {
          val minRolls = if (m.besiegedRegime) 1 else 2
          val maxRolls = diceLeft  // We know there are at least 5 cells present
          askInt(s"Roll how many dice in $name?", minRolls, maxRolls, Some(maxRolls))
        }
        else {
          val maxRolls = diceLeft min m.totalCells
          askInt(s"Roll how many dice in $name?", 1, maxRolls, Some(maxRolls))
        }
        val (actives, sleepers) = if (majorJihad) (numRolls, 0)
        else if (m.totalCells == numRolls) (m.activeCells, m.sleeperCells)
        else if (m.activeCells == 0) (0, numRolls)
        else if (m.sleeperCells == 0) (numRolls,0)
        else {
          val ac = amountOf(m.activeCells, "active cell")
          val sc = amountOf(m.sleeperCells, "sleeper cell")
          val maxAc = m.activeCells min numRolls
          val minAc = (numRolls - m.sleeperCells) max 0
          // Display the number of active and sleeper cells in the country
          // Then prompt for how many actives
          println(s"$name contains $ac and $sc")
          val actives = askInt("How many active cells will participate?", minAc, maxAc, Some(maxAc))
          val sleepers = numRolls - actives
          (actives, sleepers)
        }
        
        getJihadTargets(
          diceLeft - numRolls,
          candidates filterNot (_ == name), // Don't allow same country to be selected twice
          Target(name, majorJihad, actives, sleepers)::targets)
      }
    }
    val targets = getJihadTargets(maxDice, candidates, Nil)
    // Now resolve all jihad targets.
    // Now we can roll the die for each one see what happens and log the result.
    for (Target(name, major, actives, sleepers) <- targets) {
      addOpsTarget(name)
      val jihad = if (major) "Major Jihad" else "Jihad"
      val numDice = actives + sleepers
      println()
      def getDieRolls(dieNumber: Int): List[Int] = {
        if (dieNumber > numDice) Nil
        else {
          val ord = ordinal(dieNumber)
          val die = humanDieRoll(s"$ord Die roll for $jihad in $name: ")
          die :: getDieRolls(dieNumber + 1)
        }
      }
      performJihad(name, major, sleepers, getDieRolls(1))
    }
  }
  
  def humanPlot(ops: Int): Unit = {
    val Active  = true
    val numPlots       = game.plotsAvailableWith(ops).size // Can only use WMD Plots or Plot with number <= ops
    val maxCells       = (game.plotTargets map (name => game.getCountry(name).totalCells)).sum
    log()
    log(s"$Jihadist performs a Plot operation with ${opsString(ops)}")
    log(separator())
    if (maxCells == 1)
      println("There is one cell on the map that can plot")
    else
      println(s"There are $maxCells cells on the map that can plot")
    if (numPlots == 1)
      
      println(s"There is 1 plot available for this operation")
    else
      println(s"There are $numPlots plots available for this operation")
    
    val maxRolls = ops min numPlots min maxCells
    val numRolls = askInt("How many dice do you wish to roll?", 1, maxRolls, Some(maxRolls))
    // Keep track of where cells have been selected. They can only be selected once!
    case class Target(name: String, sleepers: Int, actives: Int)
    var targetCountries = 
      (game.countries
        filter (_.totalCells > 0) 
        map (c => c.name -> Target(c.name, c.sleeperCells, c.activeCells))
      ).toMap
    
    // All of the plotting cells must be declared before rolling any dice.
    val attempts = for (i <- (1 to numRolls).toList) yield {
      println()
      val ord        = ordinal(i)
      val candidates = targetCountries.keys.toList.sorted
      val target     = targetCountries(askCountry(s"$ord Plot in which country: ", candidates))
      val cellType   = if (target.sleepers > 0 && target.actives > 0) {
        val prompt = s"$ord Plot with (active or sleeper) cell. Default = active: "
        askOneOf(prompt, List("active", "sleeper"), allowNone = true) match {
          case None    => Active
          case Some(x) => x == "active"
        } 
      }
      else {
        println(s"$ord Plot cell will be ${if (target.actives > 0) "an active cell" else "a sleeper cell"}")
        target.actives > 0
      }
      addOpsTarget(target.name)
      // Remove the selected cell so that it cannot be selected twice.
      if (target.sleepers + target.actives == 1)
        targetCountries -= target.name
      else if (cellType == Active)
        targetCountries += target.name -> target.copy(actives = target.actives - 1)
      else
        targetCountries += target.name -> target.copy(sleepers = target.sleepers - 1)
      (ord, target.name, cellType)
    }

    def showPlots(plots: Vector[Plot]): Unit = {
      @tailrec def showNext(list: List[Plot], index: Int): Unit = list match {
        case Nil =>
        case plot :: rest =>
          println(s"$index) ${plot.name}")
          showNext(rest, index + 1)
      }
      showNext(plots.toList, 1)
    }
    
    var available = game.plotsAvailableWith(ops).sorted.toVector
    // Now we can roll the die for each one see what happens and log the result.
    for ((ord, name, active) <- attempts) {
      val c       = game.getCountry(name)
      println()
      println(separator())
      if (!active)  
        flipSleeperCells(name, 1)
      val die     = humanDieRoll(s"Die roll for $ord plot int $name: ")
      val success = die <= c.governance
      val result  = if (success) "succeeds" else "fails"
      
      log(s"$ord Plot in $name $result with a roll of $die")
      if (success) {
        val plotIndex = if (available.size == 1) 0
        else {
          showPlots(available)
          askInt(s"Select plot to place in $name: ", 1, available.size) - 1
        }
        addAvailablePlotToCountry(name, available(plotIndex))
        available = available.patch(plotIndex, Vector.empty, 1)
      }
    }
  }

  def addAvailablePlotToCountry(name: String, plot: Plot): Unit = {
    val index = game.availablePlots.indexOf(plot)
    assert(index >= 0, s"addAvailablePlotToCountry(): $plot is not available")
    val newAvail = game.availablePlots.take(index) ::: game.availablePlots.drop(index + 1)
    game = game.copy(availablePlots = newAvail)
    game.getCountry(name) match {
      case m: MuslimCountry    => game = game.updateCountry(m.copy(plots = PlotOnMap(plot) :: m.plots))
      case n: NonMuslimCountry => game = game.updateCountry(n.copy(plots = PlotOnMap(plot) :: n.plots))
    }
    if (game.humanRole == Jihadist)
      log(s"Add $plot to $name")
    else
      log(s"Add a hidden plot to $name")
  }
  
  // The plot will be moved to the resolved plots box unless toAvailable is true
  def removePlotFromCountry(name: String, mapPlot: PlotOnMap, toAvailable: Boolean = false): Unit = {
    val c = game.getCountry(name)
    val index = c.plots.indexOf(mapPlot)
    assert(index >= 0, s"removePlotFromCountry(): $mapPlot is not present in $name")
    val newPlots = c.plots.take(index) ::: c.plots.drop(index + 1)
    c match {
      case m: MuslimCountry    => game = game.updateCountry(m.copy(plots = newPlots))
      case n: NonMuslimCountry => game = game.updateCountry(n.copy(plots = newPlots))
    }
    if (toAvailable) {
      game = game.copy(availablePlots = mapPlot.plot :: game.availablePlots)
      log(s"Move $mapPlot to the available plots box")
    }
    else {
      game = game.copy(resolvedPlots = mapPlot.plot :: game.resolvedPlots)
      log(s"Move $mapPlot to the resolved plots box")
    }
  }

  
  def adjustSettings(param: Option[String]): Unit = {
    val options = "prestige" ::"funding" :: "difficulty" :: "lapsing cards" :: "removed cards" :: 
                  "first plot" :: "markers" ::"reserves" :: "plots" :: "offmap troops" :: "posture" ::
                  "auto roll" :: countryNames(game.countries)
    askOneOf("Adjust: ", options, param, allowNone = true, abbr = CountryAbbreviations, allowAbort = false) foreach {
      case "prestige" =>
        adjustInt("Prestige", game.prestige, 1 to 12) foreach { value =>
          logAdjustment("Prestige", game.prestige, value)
          game = game.copy(prestige = value)
        }
      case "posture" =>
        val newValue = oppositePosture(game.usPosture)
        logAdjustment("US posture", game.usPosture, newValue)
        game = game.copy(usPosture = newValue)
        
      case "funding" =>
        adjustInt("Funding", game.funding, 1 to 9) foreach { value =>
          logAdjustment("Prestige", game.funding, value)
          game = game.copy(funding = value)
        }
      case "offmap troops" =>
        adjustInt("Offmap troops", game.offMapTroops, 0 to (game.offMapTroops + game.troopsAvailable)) foreach { value =>
          logAdjustment("Offmap troops", game.offMapTroops, value)
          game = game.copy(offMapTroops = value)
        }
      case "auto roll" => 
        logAdjustment("Human auto roll", game.params.humanAutoRoll, !game.params.humanAutoRoll)
        game = game.copy(params = game.params.copy(humanAutoRoll = !game.params.humanAutoRoll))
      
      case "difficulty"    => adjustDifficulty()
      case "lapsing cards" => adjustLapsingCards()
      case "removed cards" => adjustRemovedCards()
      case "first plot"    => adjustFirstPlot()
      case "markers"       => adjustMarkers()
      case "reserves"      => adjustReserves()
      case "plots"         => adjustPlots()
      case name            => adjustCountry(name)
    }
  }
  
  def adjustInt(name: String, current: Int, range: Range): Option[Int] = {
    val prompt = s"$name is $current.  Enter new value (${range.min} - ${range.max}) "
    @tailrec def getResponse(): Option[Int] =
      readLine(prompt) match {
        case null | "" => None
        case INTEGER(x) if range contains x.toInt => Some(x.toInt)
        case input =>
          println(s"$input is not valid")
          getResponse()
      }
    getResponse()
  }
  
  def adjustReserves(): Unit = {
    val choices = List(US.toString, Jihadist.toString)
    for {
      roleName <- askOneOf(s"Which side (${orList(choices)}): ", choices, allowNone = true, allowAbort = false)
      role     = Role(roleName)
      current  = if (role == US) game.reserves.us else game.reserves.jihadist
      value    <- adjustInt(s"$role reserves", current, 0 to 2)
    } {
      logAdjustment(s"$role reserves", current, value)
      role match {
        case US       => game = game.copy(reserves = game.reserves.copy(us = value))
        case Jihadist => game = game.copy(reserves = game.reserves.copy(jihadist = value))
      }
    }
  }
  
  
  def adjustDifficulty(): Unit = {
    val AllLevels = if (game.botRole == US)
      OffGuard::Competent::Adept::Vigilant::Ruthless::NoMercy::Nil
    else
      Muddled::Coherent::Attractive::Potent::Infectious::Virulent::Nil
    val AllNames = AllLevels map (_.name)
    var inEffect = game.params.botDifficulties map (_.name)
    
    val label = if (game.botRole == US) "US resolve" else "Jihadist ideology"
    val standard = for (num <- 1 to AllNames.size; included = AllNames take num)
      yield s"$num) ${included.mkString(", ")}"
    // Don't allow the first name as a choice, as it represents the base
    // line difficulty and cannot be removed.
    val choices = List.range(1, 7) ::: (AllNames drop 1)
      
    @tailrec def getNextResponse(): Unit = {
      val current = s"Current $label: ${inEffect.mkString(", ")}"
      val help1 = "" :: current :: separator(current.length) :: 
                  s"Select a number for standard $label combinations" :: standard.toList
      
      val help2 = "" :: s"Or enter the name of a $label to toggle its inclusion" ::
                     (AllLevels map (_.toString))
      
      help1 foreach println
      help2 foreach println
      askOneOf(s"$label: ", choices, allowNone = true, allowAbort = false) match {
        case None =>
        case Some(INTEGER(x)) =>
          inEffect = AllNames take x.toInt
          getNextResponse()
        case Some(name) if inEffect contains name =>
          inEffect = inEffect filterNot (_ == name)
          getNextResponse()
        case Some(name) =>
          inEffect = AllNames filter (n => (inEffect contains n) || n == name) // Maintain standard order.
          getNextResponse()
      }
    }
    getNextResponse()
    val updated = inEffect map BotDifficulty.apply
    if (updated != game.params.botDifficulties) {
      logAdjustment(s"$label", game.params.botDifficulties.map(_.name), updated.map(_.name))
      game = game.copy(params = game.params.copy(botDifficulties = updated))
    }  
  }
  
  def adjustLapsingCards(): Unit = {
    var lapsing = game.cardsLapsing
    @tailrec def getNextResponse(): Unit = {
      println()
      println("Cards that are currently lapsing:")
      println(if (lapsing.isEmpty) "none" else cardNumsAndNames(lapsing.sorted))
      println()
      println("Enter a card number to move it between lapsing and not lapsing.")
      askCardNumber("Card #: ", removedLapsingOK = true) match {
        case None =>
        case Some(num) if lapsing contains num =>
          lapsing = lapsing filterNot(_ == num)
          getNextResponse()
        case Some(num) =>
          lapsing = num :: lapsing
          getNextResponse()
      }
    }
    getNextResponse()
    if (lapsing.toSet != game.cardsLapsing.toSet) {
      logAdjustment("Lapsing Events", cardNumsAndNames(game.cardsLapsing), cardNumsAndNames(lapsing))
      game = game.copy(cardsLapsing = lapsing)
    }  
  }

  def adjustRemovedCards(): Unit = {
    var outOfPlay = game.cardsRemoved
    @tailrec def getNextResponse(): Unit = {
      println()
      println("Cards that are currently out of play:")
      println(if (outOfPlay.isEmpty) "none" else cardNumsAndNames(outOfPlay.sorted))
      println()
      println("Enter a card number to move it between in play and out of play.")
      askCardNumber("Card #: ", removedLapsingOK = true) match {
        case None =>
        case Some(num) if outOfPlay contains num =>
          outOfPlay = outOfPlay filterNot(_ == num)
          getNextResponse()
        case Some(num) =>
          outOfPlay = num :: outOfPlay
          getNextResponse()
      }
    }
    getNextResponse()
    if (outOfPlay.toSet != game.cardsRemoved.toSet) {
      logAdjustment("Removed Cards", cardNumsAndNames(game.cardsRemoved), cardNumsAndNames(outOfPlay))
      game = game.copy(cardsRemoved = outOfPlay)
    }  
  }
  
  def adjustFirstPlot(): Unit = {
    var inPlay = game.firstPlotCard
    println()
    println(s"Current first plot card: ${inPlay map cardNumAndName getOrElse "none"}")
    println()
    println("Enter a card number to add or remove it as the first plot card.")
    askCardNumber("Card #: ", removedLapsingOK = true) foreach {
      case num if inPlay.exists(_ == num) => inPlay = None
      case num                            => inPlay = Some(num)
    }
    
    if (inPlay != game.firstPlotCard) {
      logAdjustment("First plot", game.firstPlotCard map cardNumAndName, inPlay map cardNumAndName)
      game = game.copy(firstPlotCard = inPlay)
    }  
  }
  
    
  def adjustMarkers(): Unit = {
    val globalMarkers = (deck.cards filter (_.marker == GlobalMarker) map (_.name)).sorted.distinct
    var inPlay = game.markers
    def available = globalMarkers filterNot inPlay.contains
    def showColums(xs: List[String]): Unit = {
      if (xs.isEmpty) println("none")
      else columnFormat(xs, 4) foreach println
    }
    @tailrec def getNextResponse(): Unit = {
      println()
      println(separator())
      println("Global markers that are currently in play:")
      showColums(inPlay)
      println()
      println("Global markers that are out of play:")
      showColums(available)
      println()
      println("Enter a marker name to move it between in play and out of play.")
      askOneOf("Marker: ", globalMarkers, allowNone = true, allowAbort = false) match {
        case None =>
        case Some(name) if inPlay contains name =>
          inPlay = inPlay filterNot(_ == name)
          getNextResponse()
        case Some(name) =>
          inPlay = name :: inPlay
          getNextResponse()
      }
    }
    getNextResponse()
    inPlay = inPlay.sorted
    if (inPlay != game.markers) {
      logAdjustment("Global Markers", game.markers, inPlay)
      game = game.copy(markers = inPlay)
    }  
  }

  // If the human is the Jihadist, the all plots are visible.
  // If the human is the US, then only resolved plots are visible.
  def adjustPlots(): Unit = {
    def showPlots(plots: Vector[Plot], startIndex: Int): Unit = {
      @tailrec def showNext(list: List[Plot], index: Int): Unit = list match {
        case Nil =>
        case plot :: rest =>
          println(s"$index) ${plot.name}")
          showNext(rest, index + 1)
      }
      
      if (plots.isEmpty)
        println("none")
      else
        showNext(plots.toList, startIndex)
    }
    
    var available = game.availablePlots.toVector
    var resolved  = game.resolvedPlots.toVector

    if (game.humanRole == US) {
      @tailrec def getNextResponse(): Unit = {
        println()
        println("Available plots:")
        println(s"${available.size} hidden plots")
        println()
        println("Resolved plots:")
        showPlots(resolved, 1)
        println()
        if (resolved.isEmpty)
          println("Plots cannot be adjusted at this time.")
        else {
          println("Select a resolved plot to make available")
          askOneOf("Plot: ", 1 to (resolved.size ), allowNone = true, allowAbort = false) map (_.toInt) match {
            case None =>
            case Some(num) =>
              val index    = num - 1
              val selected = resolved(index)
              resolved  = resolved.patch(index, Vector.empty, 1)
              available = available :+ selected
              if (resolved.nonEmpty)
                getNextResponse()
          }
        }
      }
      getNextResponse()
    }
    else { // humanRole == Jihadist
      @tailrec def getNextResponse(): Unit = {
        println()
        println("Available plots:")
        showPlots(available, 1)
        println()
        println("Resolved plots:")
        showPlots(resolved, available.size + 1)
        println()
        if (available.isEmpty && resolved.isEmpty)
          println("Plots cannot be adjusted at this time.")
        else {
          println("Select a plot to move between available and resolved")
          askOneOf("Plot: ", 1 to (available.size + resolved.size ), allowNone = true, allowAbort = false) map (_.toInt) match {
            case None =>
            case Some(num) if num <= available.size =>
              val index    = num - 1
              val selected = available(index)
              available = available.patch(index, Vector.empty, 1)
              resolved  = resolved :+ selected
              getNextResponse()
            
            case Some(num) =>
              val index    = num - available.size - 1
              val selected = resolved(index)
              resolved  = resolved.patch(index, Vector.empty, 1)
              available = available :+ selected
              getNextResponse()
          }
        }
      }
      getNextResponse()
    }
    val alist = available.toList.sorted
    val rlist = resolved.toList.sorted
    if (alist != game.availablePlots.sorted || rlist != game.resolvedPlots.sorted) {
      logAdjustment("Available plots", 
                    plotsDisplay(game.availablePlots, game.humanRole),
                    plotsDisplay(alist, game.humanRole))
      logAdjustment("Resolved plots", 
                    plotsDisplay(game.resolvedPlots, Jihadist),
                    plotsDisplay(rlist, Jihadist))
      game = game.copy(availablePlots = alist, resolvedPlots = rlist)
    }
  }
  
  def adjustCountry(name: String): Unit = {
    @tailrec def getNextResponse(): Unit = {
      println()
      println(separator())
      game.countrySummary(name, showAll = true) foreach println
      println()
        
      if (game.isMuslim(name)) {
        val choices = List(
          "alignment", "governance", "active cells", "sleeper cells", "regime change",
          "cadre", "troops", "militia", "aid", "awakening", "reaction", "wmd cache",
          "besieged regime", "civil war", "caliphate", "plots", "markers"
        ).sorted
        askOneOf("Attribute (? for list): ", choices, allowNone = true, allowAbort = false) match {
          case None        =>
          case Some(attribute) =>
            attribute match {
              case "alignment"       => adjustAlignment(name)
              case "governance"      => adjustGovernance(name)
              case "active cells"    => adjustActiveCells(name)
              case "sleeper cells"   => adjustSleeperCells(name)
              case "cadre"           => adjustCadre(name)
              case "troops"          => adjustTroops(name)
              case "militia"         => adjustMilitia(name)
              case "aid"             => adjustAid(name)
              case "awakening"       => adjustAwakening(name)
              case "reaction"        => adjustReaction(name)
              case "besieged regime" => adjustBesiegedRegime(name)
              case "regime change"   => adjustRegimeChange(name)
              case "civil war"       => adjustCivilWar(name)
              case "caliphate"       => adjustCaliphateCapital(name)
              case "plots"           => adJustCountryPlots(name)
              case "markers"         => adjustCountryMarkers(name)
              case "wmd cache"       => adjustCountryWMDCache(name)
            }
            getNextResponse()
        }
      }
      else { // Nonmuslim
        val choices = {
          var xs = List("posture", "active cells", "sleeper cells", "cadre", "plots", "markers").sorted
          if (name == UnitedStates || name == Israel || name == Iran)
            xs filterNot (_ == "posture")
          else
            xs
        }
        askOneOf("Attribute (? for list): ", choices, allowNone = true, allowAbort = false) match {
          case None        =>
          case Some(attribute) =>
            attribute match {
              case "posture"       => adjustPosture(name)
              case "active cells"  => adjustActiveCells(name)
              case "sleeper cells" => adjustSleeperCells(name)
              case "cadre"         => adjustCadre(name)
              case "plots"         => adJustCountryPlots(name)
              case "markers"       => adjustCountryMarkers(name)
            }
            getNextResponse()
        }
      }
    }
    getNextResponse()
  }
  
  def adjustPosture(name: String): Unit = {
    game.getCountry(name) match {
      case _: MuslimCountry => throw new IllegalArgumentException(s"Cannot set posture of Muslim country: $name")
      case n: NonMuslimCountry if n.iranSpecialCase =>
        println("Iran is a special case that does not have a posture.")
        pause()
      case n: NonMuslimCountry =>
        val choices = (PostureUntested::Soft::Hard::Nil) filterNot (_ == n.posture)
        val prompt = s"New posture (${orList(choices)}): "
        askOneOf(prompt, choices, allowNone = true, allowAbort = false) foreach { newPosture =>
          logAdjustment(name, "Prestige", n.posture, newPosture)
          game = game.updateCountry(n.copy(posture = newPosture))
        }
    }
  }
  
  def adjustAlignment(name: String): Unit = {
    game.getCountry(name) match {
      case _: NonMuslimCountry => throw new IllegalArgumentException(s"Cannot set alignment of non-Muslim country: $name")
      case m: MuslimCountry if m.isUntested =>
        println(s"$name is untested. Set the governance first.")
        pause()
      case m: MuslimCountry =>
        val choices = (Ally::Neutral::Adversary::Nil) filterNot (_ == m.alignment)
        val prompt = s"New alignment (${orList(choices)}): "
        askOneOf(prompt, choices, allowNone = true, allowAbort = false) foreach { newAlignment =>
          logAdjustment(name, "Alignment", m.alignment, newAlignment)
          game = game.updateCountry(m.copy(alignment = newAlignment))
        }
    }
  }

  def adjustGovernance(name: String): Unit = {
    game.getCountry(name) match {
      case _: NonMuslimCountry => throw new IllegalArgumentException(s"Cannot set governance of non-Muslim country: $name")
      case m: MuslimCountry =>
        val choices = ((GovernanceUntested::Good::Fair::Poor::IslamistRule::Nil)
                      filterNot (_ == m.governance)
                      map govToString)
        val prompt = s"New governance (${orList(choices)}): "
        askOneOf(prompt, choices, allowNone = true, allowAbort = false) map govFromString foreach { newGov =>
          // When a country becomes Good or Islamist Rule, the country cannot contain:
          //  aid, besiege regime, civil war, regime change, awakening, reaction
          // Further when the country becomes Good, it cannot be the Calipate Capital.
          val goodOrIslamist = newGov == Good || newGov == IslamistRule
          val nixCapital      = newGov == IslamistRule && m.caliphateCapital
          val nixAid          = goodOrIslamist && m.aidMarkers != 0
          val nixBesieged     = goodOrIslamist && m.besiegedRegime
          val nixCivilWar     = goodOrIslamist && m.civilWar
          val nixRegimeChange = goodOrIslamist && m.inRegimeChange
          val nixAwakening    = goodOrIslamist && m.awakening != 0
          val nixReaction     = goodOrIslamist && m.reaction != 0
          val nixTrainingCamps= newGov == Good && m.hasMarker("Training Camps")
          val anyWarnings     = nixCapital || nixAid || nixBesieged || nixCivilWar || 
                                nixRegimeChange || nixAwakening || nixRegimeChange || nixTrainingCamps
          def warn(condition: Boolean, message: String): Unit = if (condition) println(message)
          warn(nixCapital, s"$name will no longer be the Caliphate Capital.\n" +
                           "The Caliphate will be removed completely.")
          warn(nixAid, "The aid markers will be removed.")
          warn(nixBesieged, "The besieged regime marker will be removed.")
          warn(nixCivilWar, "The civil war marker will be removed.")
          warn(nixRegimeChange, "The regime change marker will be removed.")
          warn(nixAwakening, "The awakening markers will be removed.")
          warn(nixReaction, "The reaction markers will be removed.")
          warn(nixTrainingCamps, "The Training Camps marker will be removed")
          if (!anyWarnings || askYorN(s"Do you wish continue (y/n)? ")) {
            var updated = m
            logAdjustment(name, "Governance", govToString(updated.governance), govToString(newGov))
            updated = updated.copy(governance = newGov)
            if (nixCapital) {
              log(s"$name lost Caliphate Capital status.  Caliphate no longer delcared.")
              updated = updated.copy(caliphateCapital = false)
            }
            if (nixAid) {
              logAdjustment(name, "Aid", updated.aidMarkers, 0)
              updated = updated.copy(aidMarkers = 0)
            }
            if (nixBesieged) {
              logAdjustment(name, "Besieged regime", updated.besiegedRegime, false)
              updated = updated.copy(besiegedRegime = false)
            }
            if (nixCivilWar) {
              logAdjustment(name, "Civil War", updated.civilWar, false)
              endCivilWar(name)
            }
            if (nixRegimeChange) {
              logAdjustment(name, "Regime change", updated.regimeChange, NoRegimeChange)
              updated = updated.copy(regimeChange = NoRegimeChange)
            }
            if (nixAwakening) {
              logAdjustment(name, "Awakening markers", updated.awakening, 0)
              updated = updated.copy(awakening = 0)
            }
            if (nixReaction) {
              logAdjustment(name, "Reaction markers", updated.reaction, 0)
              updated = updated.copy(reaction = 0)
            }
            game = game.updateCountry(updated)
            removeTrainingCamp_?(name)
          }
        }
    }
  }

  def adjustActiveCells(name: String): Unit = {
    val c = game.getCountry(name)
    val maxCells = c.activeCells + game.cellsAvailable
    if (maxCells == 0) {
      println("There a no cells available to add to this country.")
      pause()
    }
    else 
      adjustInt("Active cells", c.activeCells, 0 to maxCells) foreach { value =>
        if (value < c.activeCells)
          removeActiveCellsFromCountry(name, c.activeCells - value, addCadre = false, s"$name adjusted: ")
        else if (value > c.activeCells)
          addActiveCellsToCountry(name, value - c.activeCells, ignoreFunding = true, s"$name adjusted: ")
      }
  }
  
  def adjustSleeperCells(name: String): Unit = {
    val c = game.getCountry(name)
    val maxCells = c.sleeperCells + game.cellsAvailable
    if (game.isCaliphateMember(name)) {
      println(s"$name is a Caliphate member and therefore cannot have sleeper cells.")
      pause()
      
    }
    else if (maxCells == 0) {
      println("There a no cells available to add to this country.")
      pause()
    }
    else 
      adjustInt("Sleeper cells", c.sleeperCells, 0 to maxCells) foreach { value =>
        if (value < c.sleeperCells)
          removeSleeperCellsFromCountry(name, c.sleeperCells - value, addCadre = false, s"$name adjusted: ")
        else if (value > c.sleeperCells)
          addSleeperCellsToCountry(name, value - c.sleeperCells, ignoreFunding = true, s"$name adjusted: ")
      }
  }
  
  def adjustCadre(name: String): Unit = {
    val country = game.getCountry(name)
    val newValue = !country.hasCadre
    logAdjustment(name, "Cadre", country.hasCadre, newValue)
    pause()
    country match {
      case m: MuslimCountry    => game = game.updateCountry(m.copy(hasCadre = newValue))
      case n: NonMuslimCountry => game = game.updateCountry(n.copy(hasCadre = newValue))
    }
  }
    
  def adjustTroops(name: String): Unit = {
    game.getCountry(name) match {
      case _: NonMuslimCountry => throw new IllegalArgumentException(s"Cannot add troops to non-Muslim country: $name")
      case m: MuslimCountry =>
        val maxTroops = m.troops + game.troopsAvailable
        if (maxTroops == 0) {
          println("There a no troops available to add to this country.")
          pause()
        }
        else
          adjustInt("Troops", m.troops, 0 to maxTroops) foreach { value =>
            logAdjustment(name, "Troops", m.troops, value)
            game = game.updateCountry(m.copy(troops = value))
          }
    }
  }
  
  def adjustMilitia(name: String): Unit = {
    game.getCountry(name) match {
      case _: NonMuslimCountry => throw new IllegalArgumentException(s"Cannot add militia to non-Muslim country: $name")
      case m: MuslimCountry =>
        val maxMilitia = m.militia + game.militiaAvailable
        if (maxMilitia == 0) {
          println("There a no troops available to add to this country.")
          pause()
        }
        else
          adjustInt("Militia", m.militia, 0 to maxMilitia) foreach { value =>
            logAdjustment(name, "Militia", m.militia, value)
            game = game.updateCountry(m.copy(militia = value))
          }
    }
  }
  
  def adjustAid(name: String): Unit = {
    game.getCountry(name) match {
      case _: NonMuslimCountry => throw new IllegalArgumentException(s"Cannot add aid to non-Muslim country: $name")
      case m: MuslimCountry if m.isGood =>
        println("Cannot add aid to a country with Good governance")
        pause()
      case m: MuslimCountry if m.isIslamistRule =>
        println("Cannot add aid to a country under Islamist Rule")
        pause()
      case m: MuslimCountry =>
        adjustInt("Aid", m.aidMarkers, 0 to 10) foreach { value =>
          logAdjustment(name, "Aid", m.aidMarkers, value)
          game = game.updateCountry(m.copy(aidMarkers = value))
        }
    }
  }
  
  def adjustAwakening(name: String): Unit = {
    game.getCountry(name) match {
      case _: NonMuslimCountry => throw new IllegalArgumentException(s"Cannot add awakening markers to non-Muslim country: $name")
      case m: MuslimCountry if m.isGood =>
        println("Cannot add awakening markers to a country with Good governance")
        pause()
      case m: MuslimCountry if m.isIslamistRule =>
        println("Cannot add awakening markers to a country under Islamist Rule")
        pause()
      case m: MuslimCountry if m.civilWar =>
        println("Cannot add awakening markers to a country in Civil War")
        pause()
      case m: MuslimCountry =>
        adjustInt("Awakening markers", m.awakening, 0 to 10) foreach { value =>
          logAdjustment(name, "Awakening markers", m.awakening, value)
          game = game.updateCountry(m.copy(awakening = value))
        }
    }
  }
  
  def adjustReaction(name: String): Unit = {
    game.getCountry(name) match {
      case _: NonMuslimCountry => throw new IllegalArgumentException(s"Cannot add reaction markers to non-Muslim country: $name")
      case m: MuslimCountry if m.isGood =>
        println("Cannot add reaction markers to a country with Good governance")
        pause()
      case m: MuslimCountry if m.isIslamistRule =>
        println("Cannot add reaction markers to a country under Islamist Rule")
        pause()
      case m: MuslimCountry if m.civilWar =>
        println("Cannot add reaction markers to a country in Civil War")
        pause()
      case m: MuslimCountry =>
        adjustInt("Reaction markers", m.reaction, 0 to 10) foreach { value =>
          logAdjustment(name, "Reaction markers", m.reaction, value)
          game = game.updateCountry(m.copy(reaction = value))
        }
    }
  }
  
  def adjustBesiegedRegime(name: String): Unit = {
    game.getCountry(name) match {
      case _: NonMuslimCountry => throw new IllegalArgumentException(s"Cannot add besieged regime to non-Muslim country: $name")
      case m: MuslimCountry if m.isGood =>
        println("Cannot add besieged regime to a country with Good governance")
        pause()
      case m: MuslimCountry if m.isIslamistRule =>
        println("Cannot add besieged regime to a country under Islamist Rule")
        pause()
      case m: MuslimCountry =>
        val newValue = !m.besiegedRegime
        logAdjustment(name, "Besieged regime", m.besiegedRegime, newValue)
        game = game.updateCountry(m.copy(besiegedRegime = newValue))
    }
  }
  
  def adjustRegimeChange(name: String): Unit = {
    game.getCountry(name) match {
      case _: NonMuslimCountry => throw new IllegalArgumentException(s"Cannot add Regime Change to non-Muslim country: $name")
      case m: MuslimCountry if m.isGood =>
        println("Cannot add Regime Change to a country with Good governance")
        pause()
      case m: MuslimCountry if m.isIslamistRule =>
        println("Cannot add Regime Change to a country under Islamist Rule")
        pause()
      case m: MuslimCountry =>
        val choices = (NoRegimeChange::GreenRegimeChange::TanRegimeChange::Nil) filterNot (_ == m.regimeChange)
        val prompt = s"New regime change value (${orList(choices)}): "
        askOneOf(prompt, choices, allowNone = true, allowAbort = false) foreach { newValue =>
          val nixCapital = newValue == NoRegimeChange && m.caliphateCapital
          if (nixCapital)
            println(s"$name will no longer be the Caliphate Capital.\n" +
                     "The Caliphate will be removed completely.")
          if (!nixCapital || askYorN(s"Do you wish continue (y/n)? ")) {
            logAdjustment(name, "Regime change", m.regimeChange, newValue)
            var updated = m.copy(regimeChange = newValue)
            if (nixCapital) {
              log(s"$name lost Caliphate Capital status.  Caliphate no longer delcared.")
              updated = updated.copy(caliphateCapital = false)
            }
            game = game.updateCountry(updated)
          }
        }
    }
  }
  
  def adjustCivilWar(name: String): Unit = {
    game.getCountry(name) match {
      case _: NonMuslimCountry => throw new IllegalArgumentException(s"Cannot add Civil War to non-Muslim country: $name")
      case m: MuslimCountry if m.isGood =>
        println("Cannot add Civil War to a country with Good governance")
        pause()
      case m: MuslimCountry if m.isIslamistRule =>
        println("Cannot add Civil War to a country under Islamist Rule")
        pause()
      case m: MuslimCountry =>
        val newValue = !m.civilWar
        logAdjustment(name, "Civil War", m.civilWar, newValue)
        if (newValue)
          startCivilWar(name)
        else
          endCivilWar(name)
    }
  }
  
  def adjustCaliphateCapital(name: String): Unit = {
    game.getCountry(name) match {
      case _: NonMuslimCountry => throw new IllegalArgumentException(s"Non-Muslim cannot be the Caliphate capital: $name")
      case m: MuslimCountry if !m.caliphateCandidate =>
        println(s"$name is not a valid Caliphate country.  Must be one of: Islamist Rule, Regime Change, Civil War")
        pause()
      case m: MuslimCountry =>
        (m.caliphateCapital, game.caliphateCapital) match {
          case (true, _) =>
            logAdjustment(name, "Caliphate Capital", true, false)
            game = game.updateCountry(m.copy(caliphateCapital = false))
          case (false, None) => 
            logAdjustment(name, "Caliphate Capital", false, true)
            game = game.updateCountry(m.copy(caliphateCapital = true))
          case (false, Some(previousCapitalName)) =>
            val previousCapital = game.getMuslim(previousCapitalName)
            logAdjustment(previousCapitalName, "Caliphate Capital", true, false)
            logAdjustment(name, "Caliphate Capital", false, true)
            game = game.updateCountries(previousCapital.copy(caliphateCapital = false)::m.copy(caliphateCapital = true)::Nil)
        }
    }
  }
  
  // Move plots between country and available/resolved
  def adJustCountryPlots(name: String): Unit = {
    val country = game.getCountry(name)
    def numIndexesUsed(numPlots: Int, hidden: Boolean) = numPlots match {
      case 0           => 0
      case _ if hidden => 1
      case x           => x
    }
    def showPlots(plots: Vector[Plot], startIndex: Int, hidden: Boolean): Unit = {
      @tailrec def showNext(list: List[Plot], index: Int): Unit = list match {
        case Nil =>
        case plot :: rest =>
          println(s"$index) ${plot.name}")
          showNext(rest, index + 1)
      }
    
      numIndexesUsed(plots.size, hidden) match {
        case 0           => println("none")
        case _ if hidden => println(s"$startIndex) ${plots.size} hidden plots")
        case _           => showNext(plots.toList, startIndex)
      }
    }
    def showMapPlots(plots: Vector[PlotOnMap], startIndex: Int, hidden: Boolean): Unit = {
      @tailrec def showNext(list: List[PlotOnMap], index: Int): Unit = list match {
        case Nil =>
        case plot :: rest =>
          println(s"$index) $plot")
          showNext(rest, index + 1)
      }
    
      numIndexesUsed(plots.size, hidden) match {
        case 0           => println("none")
        case _ if hidden => println(s"$startIndex) ${plots.size} hidden plots")
        case _           => showNext(plots.toList, startIndex)
      }
    }

    // For hidden plot we select a random index.
    def selectedIndex(index: Int, numPlots: Int, hidden: Boolean): Int =
      if (hidden) nextInt(numPlots) else index

    var countryPlots = country.plots.toVector
    var resolved     = game.resolvedPlots.toVector
    var available    = game.availablePlots.toVector
    
    @tailrec def getNextResponse(): Unit = {
      val countryStart   = 1
      val resolvedStart  = countryStart  + countryPlots.size
      val availableStart = resolvedStart + resolved.size
      println()
      println()
      println(s"$name plots (can be removed)")
      println(separator(length = 25))
      showMapPlots(countryPlots, countryStart, game.humanRole == US)
      println()
      println(s"Resolved Plots (can be added to $name)")  
      println(separator(length = 25))
      showPlots(resolved, resolvedStart, false)
      println()
      println(s"Available Plots (can be added to $name)")  
      println(separator(length = 25))
      showPlots(available, availableStart, game.humanRole == US)
      println()
      if (countryPlots.isEmpty && resolved.isEmpty && available.isEmpty)
        println("Plots cannot be adjusted at this time.")
      else {
        val countryIndexes   = numIndexesUsed(countryPlots.size, game.humanRole == US)
        val resolvedIndexes  = numIndexesUsed(resolved.size, false)
        val availableIndexes = numIndexesUsed(available.size, game.humanRole == US)
        val totalIndexes     = countryIndexes + resolvedIndexes + availableIndexes
        println("Select the number corresponding to the plot to add/remove")
        askOneOf("Plot: ", 1 to totalIndexes, allowNone = true, allowAbort = false) map (_.toInt) match {
          case None => // Cancel the adjustment
          case Some(num) =>
            val index = num - 1  // Actual indexes are zero based
            if (index < countryIndexes) {
              // Remove from country, prompt for destination (available or resolved)
              val choices = "resolved"::"available"::Nil
              askOneOf(s"Remove to (${orList(choices)}): ", choices, allowNone = true, allowAbort = false) match {
                case None => // Cancel the adjustment
                case Some(target) =>
                  val i = selectedIndex(index, countryPlots.size, game.humanRole == US)
                  if (target == "resolved")
                    resolved = resolved :+ countryPlots(i).plot
                  else
                    available = available :+ countryPlots(i).plot
                  countryPlots = countryPlots.patch(i, Vector.empty, 1)
                  getNextResponse()
              }
            }
            else if (index < countryIndexes + resolvedIndexes) {
              // Add from resolved to country
              val i = selectedIndex(index - countryIndexes, resolved.size, false)
              countryPlots = countryPlots :+ PlotOnMap(resolved(i))
              resolved = resolved.patch(i, Vector.empty, 1)
              getNextResponse()
            }
            else {
              // Add from available to country
              val i = selectedIndex(index - countryIndexes - resolvedIndexes, available.size, game.humanRole == US)
              countryPlots   = countryPlots :+ PlotOnMap(available(i))
              available = available.patch(i, Vector.empty, 1)
              getNextResponse()
            }
        }
      }
    }
    getNextResponse()
    
    val clist = countryPlots.toList.sorted
    val rlist = resolved.toList.sorted
    val alist = available.toList.sorted
    if (clist != country.plots.sorted)
      logAdjustment(name, "plots", 
                    mapPlotsDisplay(country.plots, game.humanRole),
                    mapPlotsDisplay(clist, game.humanRole))
      
    if (rlist != game.resolvedPlots.sorted)
      logAdjustment("Resolved plots", 
                    plotsDisplay(game.resolvedPlots, Jihadist),
                    plotsDisplay(rlist, Jihadist))
      
    if (alist != game.availablePlots.sorted)
      logAdjustment("Available plots", 
                    plotsDisplay(game.availablePlots, game.humanRole),
                    plotsDisplay(alist, game.humanRole))

    game = game.copy(availablePlots = alist, resolvedPlots = rlist)
    country match {
      case m: MuslimCountry    => game = game.updateCountry(m.copy(plots = clist))
      case n: NonMuslimCountry => game = game.updateCountry(n.copy(plots = clist))
    }
  }
  
  def adjustCountryMarkers(name: String): Unit = {
    val countryMarkers = (deck.cards filter (_.marker == CountryMarker) map (_.name)).sorted.distinct
    val country = game.getCountry(name)
    val priorCampCapacity = game.trainingCampCapacity
    var inPlay = country.markers
    def available = countryMarkers filterNot inPlay.contains
    def showColums(xs: List[String]): Unit = {
      if (xs.isEmpty) println("none")
      else columnFormat(xs, 4) foreach println
    }
    @tailrec def getNextResponse(): Unit = {
      println()
      println(s"$name markers:")
      showColums(inPlay)
      println()
      println("Country markers that are out of play:")
      showColums(available)
      println()
      println(s"Enter a marker name to move it between $name and out of play.")
      askOneOf("Marker: ", countryMarkers, allowNone = true, allowAbort = false) match {
        case None =>
        case Some(name) if inPlay contains name =>
          inPlay = inPlay filterNot(_ == name)
          getNextResponse()
        case Some(name) =>
          inPlay = name :: inPlay
          getNextResponse()
      }
    }
    getNextResponse()
    inPlay = inPlay.sorted
    if (inPlay != country.markers) {
      logAdjustment(name, "Markers", country.markers, inPlay)
      country match {
        case m: MuslimCountry    => game = game.updateCountry(m.copy(markers = inPlay))
        case n: NonMuslimCountry => game = game.updateCountry(n.copy(markers = inPlay))
      }
      updateTrainingCampCapacity(priorCampCapacity)
    }  
  }
  
  def adjustCountryWMDCache(name: String): Unit = {
    game.getCountry(name) match {
      case n: NonMuslimCountry => 
      adjustInt("WMD cache", n.wmdCache, 0 to 3) foreach { value =>
        logAdjustment(name, "WMD cache", n.wmdCache, value)
        game = game.updateCountry(n.copy(wmdCache = value))
      }
      case m: MuslimCountry =>
        adjustInt("WMD cache", m.wmdCache, 0 to 3) foreach { value =>
          logAdjustment(name, "WMD cache", m.wmdCache, value)
          game = game.updateCountry(m.copy(wmdCache = value))
        }
    }    
  }
}

