package ordogkatlan.ops.distribution.model

import java.time.{LocalDate, LocalDateTime}
import io.jvm.uuid._
import ordogkatlan.ops.distribution.processor.Calculator

/**
  * előadás, aminek van esélye, hogy az aktuális kiosztás során egy látogató sorszámot kapjon rá
  */
case class TicketablePlay(
  playId: UUID,                     //az előadás azonosítója
  distributableSeats: Int,          //az előadás még kiosztható sorszámainak száma
  location: String,                 //az előadás helyszíne
  distributableSince: LocalDateTime,//az előadásra szóló sorszámok kioszthatósági időhatára
  distributableUntil: LocalDateTime,//az előadásra szóló sorszámok kioszthatósági időhatára
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
   * az előadás napja
   */
  lazy val day: LocalDate = start.toLocalDate

  /**
    * az éppen most kiosztott kívánság feljegyzése
    */
  def updated(fulfilled: CalculableWish): TicketablePlay = copy(
    //csökkentjük a még kiosztható sorszámok darabszámát az éppen kiosztottakkal
    distributableSeats = distributableSeats - fulfilled.wonSeats
  )

}
