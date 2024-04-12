package ordogkatlan.ops.distribution.ds

import io.jvm.uuid._
import ordogkatlan.ops.distribution.model.{Applicant, TicketablePlay}

import scala.concurrent.Future

trait CalculatorDataSource {

  /**
   * nem diszkvalifikált, azaz:
   * * nem volt át nem vett, megkapott, de vissza nem adott kívánsága
   * van legalább egy kívánsága, ami kiszolgálható, azaz:
   * * nem elmaradó előadásra szól
   * * épp benne vagyunk az előadás kiosztási periódusában
   */
  def applicants(): Future[Set[Applicant]]

  /**
    * az aktuális kiosztás során érintett előadások tára
    */
  def detailedPlays(applicants: Set[Applicant]): Future[Map[UUID, TicketablePlay]]

}