package ordogkatlan.ops.distribution.model

import io.jvm.uuid._
import org.joda.time.LocalDate

/**
  * egy látogató egy kívánsága, aminek van esélye, hogy az aktuális, vagy egy jövőbeli
  * kiosztás során legalább részben teljesüljön
  */
case class CalculableWish(
  wishId: UUID,             //a kívánság azonosítója
  desiredSeats: Int,        //a megszerezni kívánt sorszámok mennyisége
  wonSeats: Int,            //a már sikeresen megszerzett sorszámok mennyisége
  ticketablePlayId: UUID,   //az előadás azonosítója
  priority: Int,            //a kívánság fontossága a látogató számára. Az alacsonyabb értékű kívánság preferáltabb

  //látogatók előzetes szűrésére szolgáló technikai mezők
  //a szükséges subselectek vállalhatatlanul lassítanák a query futását
  disqualifierAt: Option[LocalDate],
  playDay: LocalDate
)