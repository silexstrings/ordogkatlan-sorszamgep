package ordogkatlan.ops.distribution.model

import io.jvm.uuid._
import org.joda.time.{DateTime, LocalDate}


/**
  * Az aktuális kiosztás aktuális állapotának leírója
  */
case class DistributionState(
  applicants: List[List[Applicant]],  //a látogatók, csoportokba osztva az eddig megkapott sorszámok összértéke szerint
  plays: Map[UUID, TicketablePlay],   //az érintett előadások adatai

  priority: Int,                                //az aktuális iterációban kezelt prioritási hely
  served: Set[Applicant] = Set(),               //az aktuális iterációban már érintett látogatók
  fulfilledWishes: Set[FulfilledWish] = Set(),  //a kiosztás során már teljesített kívánságok
  targetDay: LocalDate,                         //az aktuális kiosztás célnapja
  now: DateTime,                                //az aktiális kosztás "most"-ja, az időpontfüggőségek ez alapján dőlnek el

  //az aktuális kiosztás kezdetén talált olyan látogatók, akiknek a kiosztás során adható sorszám
  initial: Set[Applicant]
) {

  //az adott kiosztás során esélyes, de végül semmit nem kapott látogatók azonosítója - értesítéshez szükséges
  lazy val nonWinningEntries: Set[UUID] = initial.map(_.visitorId) -- fulfilledWishes.map(_.visitorId)

  /**
    * egy látogató egy kiosztási iteráció során való érintésének feljegyzése
    */
  def updated(applicant: Applicant, fulfilledOpt: Option[CalculableWish]):DistributionState = fulfilledOpt match {
    // ha volt teljesített kívánság
    case Some(justFulfilled) =>
      //a látogató adatait módosítjuk a teljesült kívánsággal
      val applicantUpdated = applicant.updated(justFulfilled)(this.plays)

      copy(
        //az előadást a módosított állapotra cseréljük
        plays = plays.updated(justFulfilled.ticketablePlayId, plays(justFulfilled.ticketablePlayId).updated(justFulfilled)),
        //a látogatót az érintettek közé tesszük, módosított állapotban
        served = served + applicantUpdated,
        //feljegyzzük a teljesült kívánságot
        fulfilledWishes =
          fulfilledWishes + FulfilledWish.from(justFulfilled, plays(justFulfilled.ticketablePlayId), applicant.visitorId)
      )

    // ha nem volt teljesített kívánság
    case None =>
      copy(
        //a látogatót az érintettek közé tesszük
        served = served + applicant
      )
  }
}

object DistributionState {

  //célnapi nullelem
  def empty(targetDay: LocalDate, now: DateTime):DistributionState = DistributionState(
    applicants = List(),
    plays = Map(),
    priority = 0,
    targetDay = targetDay,
    initial = Set(),
    now = now
  )

}
