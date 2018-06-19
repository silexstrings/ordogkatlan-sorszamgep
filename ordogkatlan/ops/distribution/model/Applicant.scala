package ordogkatlan.ops.distribution.model

import io.jvm.uuid._

/**
  * egy látogató, akinek az aktuális kiosztás során van esélye, hogy kapjon sorszámot
  */
case class Applicant(

  visitorId: UUID,                  //a látogató azonosítója
  wishlist: List[CalculableWish],   //a látogató kívánságlistájának aktuális kiosztás szempontjából releváns része
  spentCredits: Float               //a látogató által eddig megkapott sorszámok összértéke
) {

  /**
    * egy frissen megakpott kívánság feljegyzése
    */
  def updated(fulfilled: CalculableWish)(plays: Map[UUID, TicketablePlay]):Applicant = {
    copy(
      //kicseréljük a kívánságot a megkapott változatra
      wishlist = wishlist.updated(wishlist.indexWhere(_.wishId == fulfilled.wishId), fulfilled),
      //feljegyezzük a költést
      spentCredits = spentCredits + fulfilled.wonSeats * plays(fulfilled.ticketablePlayId).ticketPrice
    )
  }

}