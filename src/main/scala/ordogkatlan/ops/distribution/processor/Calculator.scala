package ordogkatlan.ops.distribution.processor

import java.time.{LocalDate, LocalDateTime, LocalTime}

import io.jvm.uuid._
import ordogkatlan.ops.distribution.ds.CalculatorDataSource
import ordogkatlan.ops.distribution.model.{Applicant, DistributionState, TicketablePlay}
import ordogkatlan.ops.distribution.processor.plugins.{FilterFulfillable, GroupApplicants, OrderApplicants, WishFulfiller}

import scala.annotation.tailrec
import scala.concurrent.{ExecutionContext, Future}

/**
  * időzítés:
  *  * minden fesztiválnap előtti nap este kilenckor elsődlege kiosztás a következő napra
  *  * egy elsődleges kiosztás és a következő nap végső megnyerhetőségi határideje (jelenleg: 15:30) között
  *    másodlagos kiosztási kísérlet 5 percenként
  *
  *   csak, ha van mit: létezik osztható sorszám és
  *   csak, ha van kinek: létezik nem teljesített kívánság osztható sorszámhoz
  **/
class Calculator(ds: CalculatorDataSource)(implicit ec: ExecutionContext) {

  /**
    * belépési pont
    */
  def calculate(targetDay: LocalDate, now: LocalDateTime): Future[DistributionState] = {
    (for {
      applicants <- ds.applicants(targetDay)          // az összes látogató, aki kaphat még a célnapra valamit
      if applicants.nonEmpty                          // ha van kinek, csak akkor lépünk tovább
      plays <- ds.detailedPlays(applicants)           // az összes előadás a látogatók kívánságlistáján

      //ha van mit, csak akkor lépünk tovább
      if plays.values.exists(p => p.start.toLocalDate == targetDay && p.distributableSeats > 0)
    } yield {
      //és elindítjuk a kiosztást
      calculateDistribution(targetDay, applicants, plays, now)
    })
      .recover {
        //a napi nullelemmel térünk vissza, ha valamelyik belépési feltétel nem teljesült
        case _:NoSuchElementException => DistributionState.empty(targetDay, now)
      }
  }

  /**
    * potenciális látogatók szűrése:
    *   *  kaphat-e egyátalán a következő iterációban valamit
    * és rendezése:
    *   * előbb foglalkozunk azzal, akit esélyesebbnek kell tartanunk
    *   * egyenrangúság esetén sorba rendezünk (véletlenszerűen)
    */
  private def rebuildApplicantOrder(applicants: Set[Applicant])(
    priority: Int,
    targetDay: LocalDate,
    plays: Map[UUID, TicketablePlay],
    now: LocalDateTime
  ): List[List[Applicant]] = GroupApplicants(
    FilterFulfillable.filterHasFulfillable(applicants)(priority, targetDay, now, plays)
  ).map(OrderApplicants.apply)

  private def rebuildApplicantOrder(state: DistributionState):List[List[Applicant]] =
    rebuildApplicantOrder(state.served)(state.priority, state.targetDay, state.plays, state.now)


  /**
    * egy iteráció egy lépésében az éppen érintett látogató egy kívánsággal való kiszolgálása, ha lehetséges
    * és ezzel nem csökkentjük az esélyét valami későbbire, amit jobban szeretne, vagy nincs más, akinek adhatnánk
    *
    * "minél inkább azt kapja mindenki, amit szeretne"
    * "minél teljesebb kiosztásra törekszünk, ha csak lehet, ne ragadjon benn sorszám"
    */
  private def serveOneApplicant(applicant: Applicant)(state: DistributionState): DistributionState = {

    val wishes = FilterFulfillable(applicant)(state)
    val fulfilledOpt = WishFulfiller(wishes, applicant)(state) //teljesítünk egy kívánságot

    state.updated(applicant, fulfilledOpt)
  }

  /**
    * a kiosztás pillanatnyi állapotában egyenrangú látogatóknak teljesítjük egy-egy legmagasabb prioritásos kívánságát,
    * ha az teljesíthető, vagy teljesítendő az adott célnapon.
    */
  @tailrec
  private def iterateSpenderGroupMembers(applicants: List[Applicant])(state: DistributionState): DistributionState = {
    applicants.headOption match {
      case Some(applicant) =>
        iterateSpenderGroupMembers(applicants.tail)(serveOneApplicant(applicant)(state))
      case None =>
        state
    }
  }

  /**
    * végigmegyünk az eddigi költés alapján felállított csoportokon, az eddig legkevesebbet költöttektől az eddig
    * legtöbbet költöttekig
    */
  @tailrec
  private def iterateSpenderGroups(groups: List[List[Applicant]])(state: DistributionState): DistributionState = {
    groups.headOption match {
      case Some(group) =>
        val newState = iterateSpenderGroupMembers(group)(state)
        iterateSpenderGroups(groups.tail)(newState)
      case None => state
    }
  }

  /**
    * egy iterációban mindig egy prioritással foglalkozunk, először a fontosabbakkal, aztán haladunk a kevésbé
    * fontosak felé
    */
  @tailrec
  private def iteratePrirorities(state: DistributionState): DistributionState = {
    if (state.priority < 11) {
      val newState = iterateSpenderGroups(state.applicants)(state).copy(priority = state.priority + 1)
      iteratePrirorities(newState.copy(applicants = rebuildApplicantOrder(newState), served = Set()))
    }
    else {
      state
    }
  }

  /**
    * itt kezdődik a valódi kalkuláció
    *
    * bemenetek:
    *   * a célnap
    *   * az adatbázis szerint érvényes kívánsággal rendelkező látogatók
    *   * az érintett előadások
    *   * az aktuális időpont
    *
    * kimenetek:
    *   * a kiosztás utolsó iterációjának végállapota
    */
  private def calculateDistribution(
    targetDay: LocalDate,
    applicants: Set[Applicant],
    plays: Map[UUID, TicketablePlay],
    now: LocalDateTime): DistributionState = {

    //induló állapot
    val state = DistributionState(
      applicants = rebuildApplicantOrder(applicants)(0, targetDay, plays, now),
      plays = plays,
      priority = 0,
      targetDay = targetDay,
      initial = applicants,
      now = now
    )

    //végigmegyünk a prioritásokon
    iteratePrirorities(state)
  }
}


object Calculator {

  trait MagicNumbers {
    val BLOCKER_HALF_INTERVAL_MINS: Int = 30 //az előadásra való odaéérés becsült értékének a fele (perc)
    val RETRIEVAL_BUFFER_MINS: Int = 90      //legfeljebb ennyi idővel az előadás kezdete előtt vehető át sorszám (perc)
    val DISTRIBUTION_BUFFER_MINS: Int = 60   //az átvételi határidő előtt legfeljebb ennyivel osztható ki sorszám

    val RETRIEVAL_END_TIME: LocalTime = LocalTime.parse("18:00:00") //az infopultok zárási időpontja
  }

  val magicNumbers: MagicNumbers = new MagicNumbers {}

  /**
    * egy sorszám értéke a rendelkezésre álló helyek és a bejelentett kívánságok alapján
    *
    * "egy sorszám annyit ér, ahány nem kapták meg, hogy te megkaphasd"
    */
  def creditsPerTicket(seats: Int, wishes: Int): Double = {
    Math.round(Math.max(0D, (wishes.toDouble / seats.toDouble) - 1D) * 100D) / 100D
  }

}
