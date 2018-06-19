package ordogkatlan.ops.distribution.model

import io.jvm.uuid._

/**
  * egy teljesült kívánság
  */
case class FulfilledWish(
  visitorId: UUID,              //a látogató azonosítója
  wishId: UUID,                 //a kívánság azonosítója
  wonSeats: Int,                //megkapott helyek száma
  credits: Float,               //a megkapott helyek összesített ára
  play: TicketablePlay,         //a kapcsolódó előadás
  priority: Int                 //a látogató által beállított prioritás, melyen a kívánság teljesült
)

object FulfilledWish {

  //a teljesült kívánság felépítése a kívánságból, a kapcsolódó előadásból és a látogató azonosítójából
  def from(wish: CalculableWish, play: TicketablePlay, applicantId: UUID):FulfilledWish = FulfilledWish(
    visitorId = applicantId,
    wishId = wish.wishId,
    wonSeats = wish.wonSeats,
    credits = play.ticketPrice * wish.wonSeats,
    play = play,
    priority = wish.priority
  )

}
