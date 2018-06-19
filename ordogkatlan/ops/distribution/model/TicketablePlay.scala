package ordogkatlan.ops.distribution.model

import io.jvm.uuid._
import ordogkatlan.models.Play
import ordogkatlan.ops.distribution.processor.Calculator
import org.joda.time.{DateTime, Interval}

/**
  * előadás, aminek van esélye, hogy az aktuális kiosztás során egy látogató sorszámot kapjon rá,
  * azaz a célnapon van
  */
case class TicketablePlay(
  playId: UUID,                     //az előadás azonosítója
  distributableSeats: Int,          //az előadás még kiosztható sorszámainak száma
  start: DateTime,                  //az előadás kezdete
  end: DateTime,                    //az előadás vége
  isCanceled: Boolean,              //elmarad-e az előadás
  ticketPrice: Float,               //az előadás egy sorszámának ára
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
  lazy val blocking:Interval = new Interval(
    start minusMinutes BLOCKER_HALF_INTERVAL_MINS,
    end plusMinutes BLOCKER_HALF_INTERVAL_MINS
  )

  /**
    * az előadásra megkapott sorszám átvételének időhatára
    *
    * a kioszthatósági időhatár kiszámításához szükséges
    */
  lazy val retrievableUntil:DateTime = {
    val retrievalEndTime = start.toLocalDate.toDateTime(RETRIEVAL_END_TIME)
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
  lazy val distributableUntil:DateTime = retrievableUntil minusMinutes DISTRIBUTION_BUFFER_MINS


  /**
    * az éppen most kiosztott kívánság feljegyzése
    */
  def updated(fulfilled: CalculableWish):TicketablePlay = copy(
    //csökkentjük a még kiosztható sorszámok darabszámát az éppen kiosztottakkal
    distributableSeats = distributableSeats - fulfilled.wonSeats
  )

}

object TicketablePlay {

  /**
    * adatbázismodellből létrehozza a kalkulátormodellt
    */
  def from(play: Play):TicketablePlay = TicketablePlay(
    playId = play.playId.get,
    distributableSeats = play.reservableSeats,
    start = play.start,
    end = play.end,
    isCanceled = play.isCanceled,
    ticketPrice = play.creditsPerTicket.getOrElse(0F),
    title = play.title,
    allReservableSeats = play.reservableSeats
  )

}
