package ordogkatlan.ops.distribution.processor.plugins

import ordogkatlan.ops.distribution.model.{Applicant, CalculableWish, DistributionState}

trait WishFulfiller {

  /**
    * a látogató számára kiadható sorszámok közül kiválasztja a legmegfelelőbbet és beállítja az odaadható sorszámokat,
    * azaz a kívánt és a még rendelkezésre álló mennyiség közül a kevesebbet
    *
    * "a másik véglet szerint mindenkinek egyszerre egyet adnánk. Ekkor a túljelentkezés miatt tipikusan senki nem
    * kapna egy előadásra egynél többet"
    *
    * "inkább adunk 150 látogatónak két-két sorszámot, mint 300-nak egyet-egyet (ha mindenki kettőt kért)"
    */
  def apply(wishes: List[CalculableWish], applicant: Applicant)(state: DistributionState):Option[CalculableWish] =
    findBest(wishes, applicant)(state).map { wish =>
      wish.copy(
        wonSeats = Math.min(wish.desiredSeats, state.plays(wish.ticketablePlayId).distributableSeats)
      )
    }

  def findBest(wishes: List[CalculableWish], applicant: Applicant)(state: DistributionState):Option[CalculableWish]

}


class PriorityWishFulfiller extends WishFulfiller {

  /**
    * legmegfelelőbb: az éppen kezelt prioritáson van
    *
    * "mindenkinek próbáljuk azt adni, amit a legjobban szeretne"
    */
  override def findBest(wishes: List[CalculableWish], applicant: Applicant)(state: DistributionState) =
    wishes.find(_.priority == state.priority)
}