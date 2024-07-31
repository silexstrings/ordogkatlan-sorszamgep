package ordogkatlan.ops.distribution.processor

import io.jvm.uuid._
import ordogkatlan.ops.distribution.ds.CalculatorDataSource
import ordogkatlan.ops.distribution.model.{Applicant, DistributionState, TicketablePlay}
import ordogkatlan.ops.distribution.processor.plugins.{FilterFulfillable, GroupApplicants, OrderApplicants, WishFulfiller}

import java.time.LocalDateTime
import scala.annotation.tailrec
import scala.concurrent.{ExecutionContext, Future}

/**
  * időzítés:
  *  * kiosztási kísérlet 5 percenként
  *
  *   csak, ha van mit: létezik osztható sorszám és
  *   csak, ha van kinek: létezik nem teljesített kívánság osztható sorszámhoz
  **/
class Calculator(ds: CalculatorDataSource)(implicit ec: ExecutionContext) {

  /**
    * belépési pont
    */
  def calculate(now: LocalDateTime): Future[Option[DistributionState]] = {
    (for {
      applicants <- ds.applicants()          // az összes látogató, aki kaphat még most valamit
      if applicants.nonEmpty                          // ha van kinek, csak akkor lépünk tovább
      plays <- ds.detailedPlays(applicants)           // az összes előadás a látogatók kívánságlistáján

      //ha van mit, csak akkor lépünk tovább
      if plays.values.exists(p => p.distributableSince.isBefore(now) && p.distributableUntil.isAfter(now) && p.distributableSeats > 0)
    } yield {
      //és elindítjuk a kiosztást
      Some(calculateDistribution(applicants, plays, now))
    })
      .recover {
        //a nullelemmel térünk vissza, ha valamelyik belépési feltétel nem teljesült
        case _:NoSuchElementException => None
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
    plays: Map[UUID, TicketablePlay],
    now: LocalDateTime
  ): List[List[Applicant]] = GroupApplicants(
    FilterFulfillable.filterHasFulfillable(applicants)(now, plays)
  ).map(OrderApplicants.apply)

  private def rebuildApplicantOrder(state: DistributionState):List[List[Applicant]] =
    rebuildApplicantOrder(state.servedInGroup)(state.plays, state.now)


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
    val trail = fulfilledOpt.map { fulfilled =>
      val title = state.plays(fulfilled.ticketablePlayId).title
      s"desired: ${fulfilled.desiredSeats} won: ${fulfilled.wonSeats} for: ${fulfilled.ticketablePlayId} ($title)"
    }.getOrElse("no eligible")

    state.updated(applicant, fulfilledOpt).copy(trail = state.trail.appended(s"fulfilling: $trail"))
  }

  /**
    * a kiosztás pillanatnyi állapotában egyenrangú látogatóknak teljesítjük egy-egy legmagasabb prioritásos kívánságát,
    * ha az teljesíthető, vagy teljesítendő.
    */
  @tailrec
  private def iterateSpenderGroupMembers(applicants: List[Applicant])(state: DistributionState): DistributionState = {
    val trailed = state.copy(trail = state.trail.appended(s"iterating on applicant ${applicants.headOption.map(_.visitorId)}"))
    applicants.headOption match {
      case Some(applicant) =>
        iterateSpenderGroupMembers(applicants.tail)(serveOneApplicant(applicant)(trailed))
      case None =>
        trailed
    }
  }

  /**
    * végigmegyünk az eddigi költés alapján felállított csoportokon, az eddig legkevesebbet költöttektől az eddig
    * legtöbbet költöttekig
    */
  @tailrec
  private def iterateSpenderGroups(groups: List[List[Applicant]])(state: DistributionState): DistributionState = {
    val trailed = state.copy(trail = state.trail.appended(s"iterating on spender group: ${groups.headOption.flatMap(_.headOption.map(_.spentCredits))}"))
    groups.headOption match {
      case Some(group) =>
        val newState = iterateSpenderGroupMembers(group)(trailed)
        iterateSpenderGroups(groups.tail)(newState)
      case None => trailed
    }
  }

  /**
    * egy iterációban mindig egy prioritással foglalkozunk, először a fontosabbakkal, aztán haladunk a kevésbé
    * fontosak felé
    */
  @tailrec
  private def iteratePrirorities(state: DistributionState): DistributionState = {
    val trailed = state.copy(trail = state.trail.appended(s"iterating on priority ${state.priority}"))
    if (trailed.priority < 11) {
      val newState = iterateSpenderGroups(trailed.applicants)(trailed).copy(priority = trailed.priority + 1)
      iteratePrirorities(newState.copy(applicants = rebuildApplicantOrder(newState), servedInGroup = Set()))
    }
    else {
      trailed
    }
  }

  /**
    * itt kezdődik a valódi kalkuláció
    *
    * bemenetek:
    *   * az adatbázis szerint érvényes kívánsággal rendelkező látogatók
    *   * az érintett előadások
    *   * az aktuális időpont
    *
    * kimenetek:
    *   * a kiosztás utolsó iterációjának végállapota
    */
  private def calculateDistribution(
    applicants: Set[Applicant],
    plays: Map[UUID, TicketablePlay],
    now: LocalDateTime): DistributionState = {

    //induló állapot
    val state = DistributionState(
      applicants = rebuildApplicantOrder(applicants)(plays, now),
      plays = plays,
      priority = 0,
      initial = applicants,
      now = now,
      trail = Seq()
    )

    //végigmegyünk a prioritásokon
    iteratePrirorities(state)
  }
}


object Calculator {

  trait MagicNumbers {
    val BLOCKER_HALF_INTERVAL_MINS: Int = 30 //az előadásra való odaéérés becsült értékének a fele (perc)
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
