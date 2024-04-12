package ordogkatlan.ops.distribution.processor.plugins

import io.jvm.uuid._
import ordogkatlan.ops.distribution.model.Overlaps._
import ordogkatlan.ops.distribution.model.{Applicant, CalculableWish, DistributionState, TicketablePlay}

import java.time.LocalDateTime

/**
  * A teljesíthető kívánságok és a hozzájuk tartozó látogatók kiválogatása
  */
object FilterFulfillable {

  /**
    * kiválasztja a potenciális látogatók közül azokat, akiknek van kívánsága, ami ebben a körben teljesíthető, tehát:
    *   * van épp kiszolgálni próbált helyen előadása
    */
  def filterHasFulfillable(applicants: Set[Applicant])(
    now: LocalDateTime,
    plays: Map[UUID, TicketablePlay]
  ): Set[Applicant] = {
    applicants.filter { applicant =>
      applyImpl(applicant)(now, plays).nonEmpty //van legalább még egy nyerhető előadása
    }
  }

  /**
    * a kívánságlistán azok az előadások, amelyek:
    *   * nem maradnak el
    *   * kioszthatóak
    *   * van még legalább 1 hely rájuk
    *   * a látogatónak nincs már megkapott, átfedő sorszáma
    *   * a látogatónak nincs még megkapott sorszáma rá
    */
  private def applyImpl(applicant: Applicant)(
    now: LocalDateTime,
    plays: Map[UUID, TicketablePlay]): List[CalculableWish] =

    applicant.wishlist.filter { wish =>                             //csak azok a kívánságok, amikre
      wish.wonSeats == 0 &&                                         //nem kapott még sorszámot a látogató
      plays.get(wish.ticketablePlayId).exists { play =>             //és olyan előadásra vonatkoznak, ami
        !play.isCanceled &&                                         // * nem marad el
        play.distributableSince.isBefore(now) &&                    // * kiosztható már
        play.distributableUntil.isAfter(now) &&                     // * kiosztható még (van idő átvenni a sorszámot, ha megkapja)
        play.distributableSeats > 0 &&                              // * van még rá hely
        applicant.wishlist.filter(_.wonSeats > 0).count { p =>      // * a már megnyertek között
          plays.get(p.ticketablePlayId).map(_.day).contains(play.day)
        } < 2 &&                                                    // nincs még 2 aznapi
        applicant.wishlist.filter(_.wonSeats > 0).flatMap { p =>    //és a már megnyertek között
          plays.get(p.ticketablePlayId).map(_.blocking)
        }.forall(!_.overlaps(play.blocking))                        //nincs olyan, ami átfedő
      }
    }

  def apply(applicant: Applicant)(state: DistributionState): List[CalculableWish] =
    applyImpl(applicant)(state.now, state.plays)
}
