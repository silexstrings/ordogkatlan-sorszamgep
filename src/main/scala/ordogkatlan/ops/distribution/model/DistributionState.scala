package ordogkatlan.ops.distribution.model

import io.jvm.uuid._

import java.time.LocalDateTime


/**
  * Az aktuális kiosztás aktuális állapotának leírója
  */
case class DistributionState(
  applicants: List[List[Applicant]],  //a látogatók, csoportokba osztva az eddig megkapott sorszámok összértéke szerint
  plays: Map[UUID, TicketablePlay],   //az érintett előadások adatai

  priority: Int,                                //az aktuális iterációban kezelt prioritási hely
  servedInGroup: Set[Applicant] = Set(),               //az aktuális iterációban már érintett látogatók
  winners: Set[Applicant] = Set(),               //már valamit kapott látogatók (utófeldolgozásra)
  fulfilledWishes: Set[FulfilledWish] = Set(),  //a kiosztás során már teljesített kívánságok
  now: LocalDateTime,                           //az aktiális kosztás "most"-ja, az időpontfüggőségek ez alapján dőlnek el

  //az aktuális kiosztás kezdetén talált olyan látogatók, akiknek a kiosztás során adható sorszám
  initial: Set[Applicant],
  trail: Seq[String]
) {

  /**
    * egy látogató egy kiosztási iteráció során való érintésének feljegyzése
    */
  def updated(applicant: Applicant, fulfilledOpt: Option[CalculableWish]): DistributionState = fulfilledOpt match {
    // ha volt teljesített kívánság
    case Some(justFulfilled) =>
      //a látogató adatait módosítjuk a teljesült kívánsággal
      val applicantUpdated = applicant.updated(justFulfilled)(this.plays)

      copy(
        //az előadást a módosított állapotra cseréljük
        plays = plays.updated(justFulfilled.ticketablePlayId, plays(justFulfilled.ticketablePlayId).updated(justFulfilled)),
        //a látogatót az érintettek közé tesszük, módosított állapotban
        servedInGroup = servedInGroup + applicantUpdated,
        winners = winners.filter(_.visitorId != applicantUpdated.visitorId) + applicantUpdated,
        //feljegyzzük a teljesült kívánságot
        fulfilledWishes =
          fulfilledWishes + FulfilledWish.from(justFulfilled, plays(justFulfilled.ticketablePlayId), applicant.visitorId)
      )

    // ha nem volt teljesített kívánság
    case None =>
      copy(
        //a látogatót az érintettek közé tesszük
        servedInGroup = servedInGroup + applicant,
      )
  }
}

object DistributionState {

  //nullelem
  def empty(now: LocalDateTime): DistributionState = DistributionState(
    applicants = List(),
    plays = Map(),
    priority = 0,
    initial = Set(),
    now = now,
    trail = Seq()
  )

}
