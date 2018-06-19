package ordogkatlan.ops.distribution.processor.plugins

import com.google.inject.Singleton
import io.jvm.uuid._
import ordogkatlan.ops.distribution.model.{Applicant, CalculableWish, DistributionState, TicketablePlay}
import org.joda.time.{DateTime, LocalDate}

/**
  * A teljesíthető kívánságok és a hozzájuk tartozó látogatók kiválogatása
  */
@Singleton
class FilterFulfillable {

  /**
    * kiválasztja a potenciális látogatók közül azokat, akiknek van kívánsága, ami ebben a körben teljesíthető, tehát:
    *   * látogató nem kapott már két különböző, célnapi előadásra sorszámot
    *   * és van épp kiszolgálni próbált helyen előadása
    */
  def filterHasFulfillable(applicants: Set[Applicant])(
    priority: Int,
    targetDay: LocalDate,
    now: DateTime,
    plays: Map[UUID, TicketablePlay]
  ):Set[Applicant] = {

    applicants.filter { applicant =>
      { //nincs még két különböző előadásra sorszáma a látogatónak, azaz
        applicant.wishlist.count { w =>
          w.wonSeats > 0 &&  //megkapott sorszáma
          plays.get(w.ticketablePlayId).exists(_.start.toLocalDate.isEqual(targetDay)) //a célnapi előadások közül
        } < 2 //kevesebb, mint kettőre van
      } && applyImpl(applicant)(targetDay, now, plays).nonEmpty //és van legalább még egy nyerhető előadása
    }

  }

  /**
    * a kívánságlistán azok az előadások, amelyek:
    *   * nem maradnak el
    *   * a célnapon vannak
    *   * van még legalább 1 hely rájuk
    *   * a látogatónak nincs már megkapott, átfedő sorszáma
    *   * a látgoatnók nincs még megkapott sorszáma rá
    */
  def applyImpl(applicant: Applicant)(targetDay: LocalDate, now: DateTime, plays: Map[UUID, TicketablePlay]): List[CalculableWish] =
    applicant.wishlist.filter { wish =>                             //csak azok a kívánságok, amikre
      wish.wonSeats == 0 &&                                         //nem kapott még sorszámot a látogató
      plays.get(wish.ticketablePlayId).exists { play =>             //és olyan előadásra vonatkoznak, ami
        !play.isCanceled &&                                         // * nem marad el
        play.start.toLocalDate.isEqual(targetDay) &&                // * a célnapon van
        play.distributableUntil.isAfter(now) &&                     // * még van idő átvenni a sorszámot, ha megkapja
        play.distributableSeats > 0 &&                              // * van még rá hely
        applicant.wishlist.filter(_.wonSeats > 0).flatMap { p =>    //és a már megnyertek között
          plays.get(p.ticketablePlayId).map(_.blocking)
        }.forall(!_.overlaps(play.blocking))                        //nincs olyan, ami átfedő
      }
    }

  def apply(applicant: Applicant)(state: DistributionState): List[CalculableWish] =
    applyImpl(applicant)(state.targetDay, state.now, state.plays)
}
