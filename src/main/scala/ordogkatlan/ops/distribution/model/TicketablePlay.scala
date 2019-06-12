package ordogkatlan.ops.distribution.model

import java.time.LocalDateTime

import io.jvm.uuid._
import ordogkatlan.ops.distribution.processor.Calculator

/**
  * előadás, aminek van esélye, hogy az aktuális kiosztás során egy látogató sorszámot kapjon rá,
  * azaz a célnapon van
  */
case class TicketablePlay(
  playId: UUID,                     //az előadás azonosítója
  distributableSeats: Int,          //az előadás még kiosztható sorszámainak száma
  location: String,                 //az előadás helyszíne
  start: LocalDateTime,             //az előadás kezdete
  end: LocalDateTime,               //az előadás vége
  isCanceled: Boolean,              //elmarad-e az előadás
  ticketPrice: Double,              //az előadás egy sorszámának ára
  title: String,                    //az előadás címe
  allReservableSeats: Int           //az előadás összes kiosztható sorszámainak száma
) {

  import Calculator.magicNumbers._

  /**
    * két előadásra nem kapható egyidejűleg sorszám, ha a két előadás ezen értékének van közös időpillanata
    *
    * "ne kapjon senki olyan sorszámot, amire nem tudna elmenni"
    *
    * tehát két előadásra csak akkor lehet egyidejűleg sorszámot kapni, ha a korábbi vége minimum egy órával
    * hamarabb van a későbbi kezdeténél
    */
  lazy val blocking: (LocalDateTime, LocalDateTime) = (
    start minusMinutes BLOCKER_HALF_INTERVAL_MINS,
    end plusMinutes BLOCKER_HALF_INTERVAL_MINS
  )

  /**
    * az előadásra megkapott sorszám átvételének időhatára
    *
    * a kioszthatósági időhatár kiszámításához szükséges
    */
  lazy val retrievableUntil: LocalDateTime = {
    val retrievalEndTime = start.toLocalDate.atTime(RETRIEVAL_END_TIME)
    if ((start minusMinutes RETRIEVAL_BUFFER_MINS) isAfter retrievalEndTime) {
      retrievalEndTime
    }
    else {
      start minusMinutes RETRIEVAL_BUFFER_MINS
    }
  }

  /**
    * az előadásra szóló sorszámok kioszthatósági időhatára
    */
  lazy val distributableUntil: LocalDateTime = retrievableUntil minusMinutes DISTRIBUTION_BUFFER_MINS


  /**
    * az éppen most kiosztott kívánság feljegyzése
    */
  def updated(fulfilled: CalculableWish): TicketablePlay = copy(
    //csökkentjük a még kiosztható sorszámok darabszámát az éppen kiosztottakkal
    distributableSeats = distributableSeats - fulfilled.wonSeats
  )

}
